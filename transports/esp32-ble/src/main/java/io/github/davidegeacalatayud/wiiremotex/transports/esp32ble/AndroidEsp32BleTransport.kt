package io.github.davidegeacalatayud.wiiremotex.transports.esp32ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HidInputReport
import io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeControlCode
import io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec
import io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameReassembler
import io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeMessageType
import io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeStatusCode
import io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.WiiConnectionState
import io.github.davidegeacalatayud.wiiremotex.core.session.HidTransport
import java.util.ArrayDeque
import java.util.UUID

/**
 * Android fallback transport:
 *
 * Android -> BLE -> ESP32 -> Bluetooth Classic HID -> Nintendo Wii.
 *
 * The Wii protocol engine remains in the Android process. The ESP32 is only a
 * transport bridge, matching the iOS architecture exactly.
 */
class AndroidEsp32BleTransport(
    context: Context,
    private val listener: Listener = Listener.NO_OP,
) : HidTransport {

    enum class State {
        IDLE,
        SCANNING,
        CONNECTING,
        DISCOVERING,
        READY,
        ERROR,
    }

    interface Listener {
        fun onStateChanged(state: State) = Unit
        fun onBridgeReady(protocolVersion: Int, firmwareVersion: String?) = Unit
        fun onWiiConnectionStateChanged(state: Int) = Unit
        fun onHostReport(reportId: Int, payload: ByteArray) = Unit
        fun onBridgeError(code: Int) = Unit
        fun onDiagnostic(message: String) = Unit
        fun onError(message: String, cause: Throwable? = null) = Unit

        companion object {
            val NO_OP = object : Listener {}
        }
    }

    private data class PendingWrite(
        val data: ByteArray,
        var retries: Int = 0,
    )

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val lock = Any()
    private val reassembler = BridgeFrameReassembler()
    private val writeQueue = ArrayDeque<PendingWrite>()

    private var scannerActive = false
    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var writeInFlight = false
    private var state = State.IDLE
    private var sequence = 0

    val currentState: State
        get() = synchronized(lock) { state }

    val ready: Boolean
        get() = currentState == State.READY

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            stopScanOnly()
            listener.onDiagnostic(
                "ESP32 bridge discovered: ${device.name ?: device.address}",
            )
            setState(State.CONNECTING)
            connect(device)
        }

        override fun onScanFailed(errorCode: Int) {
            scannerActive = false
            fail("BLE scan failed with code $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("ESP32 GATT connection failed with status $status")
                closeGatt()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    listener.onDiagnostic("Android BLE link connected")
                    setState(State.DISCOVERING)
                    if (!safeGattCall("discover BLE services") { gatt.discoverServices() }) {
                        closeGatt()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    listener.onDiagnostic("ESP32 BLE link disconnected")
                    resetSession()
                    setState(State.IDLE)
                    closeGatt()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("BLE service discovery failed with status $status")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                fail("WiiRemoteX ESP32 service was not found")
                return
            }

            configureService(gatt, service)
        }

        @Deprecated("Legacy callback used on Android 12 and earlier")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == BRIDGE_TO_PHONE_UUID) {
                characteristic.value?.let(::handlePacket)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BRIDGE_TO_PHONE_UUID) {
                handlePacket(value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != CCC_UUID) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Unable to enable ESP32 bridge indications: status $status")
                return
            }

            listener.onDiagnostic("ESP32 bridge indications enabled")
            // Transport is usable now; BRIDGE_READY still validates protocol compatibility.
            setState(State.READY)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid != PHONE_TO_BRIDGE_UUID) return

            var retry: PendingWrite? = null
            synchronized(lock) {
                writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS && lastWrite != null) {
                    val failed = lastWrite!!
                    if (failed.retries < MAX_WRITE_RETRIES) {
                        failed.retries++
                        retry = failed
                    }
                }
                lastWrite = null
                if (retry != null) {
                    writeQueue.addFirst(retry)
                }
            }

            if (status != BluetoothGatt.GATT_SUCCESS && retry == null) {
                listener.onError("BLE write failed after retries: status $status")
            }

            drainWrites()
        }
    }

    private var lastWrite: PendingWrite? = null

    fun start(): Boolean {
        if (currentState != State.IDLE && currentState != State.ERROR) {
            listener.onDiagnostic("ESP32 BLE transport already active")
            return true
        }

        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null) {
            fail("Bluetooth LE is unavailable on this Android device")
            return false
        }

        resetSession()
        setState(State.SCANNING)

        return try {
            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build(),
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(filters, settings, scanCallback)
            scannerActive = true
            listener.onDiagnostic("Scanning for WiiRemoteX ESP32 bridge")
            true
        } catch (error: SecurityException) {
            fail("Bluetooth scan permission is required", error)
            false
        }
    }

    fun stop() {
        stopScanOnly()
        resetSession()
        closeGatt()
        setState(State.IDLE)
    }

    fun startWiiPairing(): Boolean =
        sendControl(BridgeControlCode.START_WII_PAIRING)

    fun stopWiiPairing(): Boolean =
        sendControl(BridgeControlCode.STOP_WII_PAIRING)

    fun clearWiiBond(): Boolean =
        sendControl(BridgeControlCode.CLEAR_WII_BOND)

    override fun send(report: HidInputReport): Boolean {
        if (!ready) return false

        return enqueueMessage(
            BridgeMessageType.INPUT_REPORT,
            byteArrayOf(report.reportId.toByte()) + report.payload,
        )
    }

    private fun sendControl(code: Int): Boolean =
        ready && enqueueMessage(
            BridgeMessageType.CONTROL,
            byteArrayOf(code.toByte()),
        )

    private fun enqueueMessage(
        type: BridgeMessageType,
        payload: ByteArray,
    ): Boolean {
        val packets = try {
            val next = synchronized(lock) {
                val value = sequence
                sequence = (sequence + 1) and 0xFFFF
                value
            }
            BridgeFrameCodec.encode(type, next, payload)
        } catch (error: IllegalArgumentException) {
            listener.onError("Bridge message is too large", error)
            return false
        }

        synchronized(lock) {
            if (writeQueue.size + packets.size > MAX_PENDING_PACKETS) {
                listener.onError("ESP32 BLE write queue is full")
                return false
            }
            packets.forEach { packet ->
                writeQueue.addLast(PendingWrite(packet))
            }
        }

        drainWrites()
        return true
    }

    private fun drainWrites() {
        val currentGatt: BluetoothGatt
        val characteristic: BluetoothGattCharacteristic
        val pending: PendingWrite

        synchronized(lock) {
            if (writeInFlight || state != State.READY) return
            currentGatt = gatt ?: return
            characteristic = txCharacteristic ?: return
            pending = writeQueue.pollFirst() ?: return
            writeInFlight = true
            lastWrite = pending
        }

        val writeType =
            if (
                characteristic.properties and
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            ) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        val accepted = try {
            writeCharacteristic(
                currentGatt,
                characteristic,
                pending.data,
                writeType,
            )
        } catch (error: SecurityException) {
            listener.onError("Bluetooth connect permission is required", error)
            false
        }

        if (!accepted) {
            synchronized(lock) {
                writeInFlight = false
                lastWrite = null
                if (pending.retries < MAX_WRITE_RETRIES) {
                    pending.retries++
                    writeQueue.addFirst(pending)
                }
            }
            listener.onError("Android rejected a BLE bridge write")
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        writeType: Int,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, writeType) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.writeType = writeType
            characteristic.value = data
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun configureService(
        gatt: BluetoothGatt,
        service: BluetoothGattService,
    ) {
        val tx = service.getCharacteristic(PHONE_TO_BRIDGE_UUID)
        val rx = service.getCharacteristic(BRIDGE_TO_PHONE_UUID)

        if (tx == null || rx == null) {
            fail("ESP32 bridge characteristics are incomplete")
            return
        }

        txCharacteristic = tx
        rxCharacteristic = rx

        try {
            if (!gatt.setCharacteristicNotification(rx, true)) {
                fail("Android rejected bridge indication subscription")
                return
            }

            val ccc = rx.getDescriptor(CCC_UUID)
            if (ccc == null) {
                fail("ESP32 bridge CCC descriptor is missing")
                return
            }

            val accepted = writeDescriptor(
                gatt,
                ccc,
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE,
            )
            if (!accepted) {
                fail("Android rejected bridge CCC write")
            }
        } catch (error: SecurityException) {
            fail("Bluetooth connect permission is required", error)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                )
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, false, gattCallback)
            }
        } catch (error: SecurityException) {
            fail("Bluetooth connect permission is required", error)
        }
    }

    private fun handlePacket(packet: ByteArray) {
        if (packet.size > BridgeFrameCodec.MAX_PACKET_SIZE) {
            listener.onError("Rejected oversized ESP32 BLE packet")
            return
        }

        val message = reassembler.accept(packet) ?: return

        when (message.type) {
            BridgeMessageType.OUTPUT_REPORT -> {
                if (message.payload.isEmpty()) return
                val reportId = message.payload[0].toInt() and 0xFF
                listener.onHostReport(
                    reportId,
                    message.payload.copyOfRange(1, message.payload.size),
                )
            }

            BridgeMessageType.STATUS -> handleStatus(message.payload)

            BridgeMessageType.INPUT_REPORT,
            BridgeMessageType.CONTROL,
            -> listener.onDiagnostic(
                "Ignored unexpected ESP32 message type ${message.type}",
            )
        }
    }

    private fun handleStatus(payload: ByteArray) {
        if (payload.size < 2) return

        when (payload[0].toInt() and 0xFF) {
            BridgeStatusCode.WII_CONNECTION -> {
                val connection = payload[1].toInt() and 0xFF
                if (connection in WiiConnectionState.DISCONNECTED..WiiConnectionState.CONNECTED) {
                    listener.onWiiConnectionStateChanged(connection)
                }
            }

            BridgeStatusCode.BRIDGE_READY -> {
                val protocolVersion = payload[1].toInt() and 0xFF
                val firmwareVersion =
                    if (payload.size >= 5) {
                        "${payload[2].toInt() and 0xFF}." +
                            "${payload[3].toInt() and 0xFF}." +
                            "${payload[4].toInt() and 0xFF}"
                    } else {
                        null
                    }

                listener.onBridgeReady(protocolVersion, firmwareVersion)

                if (protocolVersion != BridgeFrameCodec.VERSION) {
                    fail(
                        "ESP32 bridge protocol v$protocolVersion is incompatible " +
                            "with app protocol v${BridgeFrameCodec.VERSION}",
                    )
                }
            }

            BridgeStatusCode.ERROR -> {
                listener.onBridgeError(payload[1].toInt() and 0xFF)
            }
        }
    }

    private fun setState(newState: State) {
        synchronized(lock) {
            state = newState
        }
        listener.onStateChanged(newState)
    }

    private fun resetSession() {
        reassembler.reset()
        synchronized(lock) {
            sequence = 0
            writeQueue.clear()
            writeInFlight = false
            lastWrite = null
            txCharacteristic = null
            rxCharacteristic = null
        }
    }

    private fun stopScanOnly() {
        if (!scannerActive) return

        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // The caller may have revoked Bluetooth permission while scanning.
        } finally {
            scannerActive = false
        }
    }

    private fun closeGatt() {
        val current = synchronized(lock) {
            val value = gatt
            gatt = null
            value
        }

        try {
            current?.disconnect()
            current?.close()
        } catch (_: SecurityException) {
            current?.close()
        }
    }

    private fun fail(message: String, cause: Throwable? = null) {
        setState(State.ERROR)
        listener.onError(message, cause)
    }

    private inline fun safeGattCall(
        description: String,
        action: () -> Boolean,
    ): Boolean = try {
        val accepted = action()
        if (!accepted) {
            fail("Android rejected request to $description")
        }
        accepted
    } catch (error: SecurityException) {
        fail("Bluetooth connect permission is required", error)
        false
    }

    private companion object {
        val SERVICE_UUID: UUID =
            UUID.fromString("7C0A0001-6F4B-4A42-9D47-575258000001")
        val PHONE_TO_BRIDGE_UUID: UUID =
            UUID.fromString("7C0A0002-6F4B-4A42-9D47-575258000001")
        val BRIDGE_TO_PHONE_UUID: UUID =
            UUID.fromString("7C0A0003-6F4B-4A42-9D47-575258000001")
        val CCC_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        const val MAX_PENDING_PACKETS = 1_024
        const val MAX_WRITE_RETRIES = 2
    }
}
