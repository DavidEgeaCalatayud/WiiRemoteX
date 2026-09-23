package io.github.davidegeacalatayud.wiiremotex.transports.esp32ble

import android.annotation.SuppressLint
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android fallback transport:
 * Android -> BLE -> ESP32 -> Bluetooth Classic HID -> Wii.
 *
 * Wii protocol state stays in WiimoteSessionEngine. This class only transports
 * encoded Wii input reports and forwards Wii output reports back to the runtime.
 */
@SuppressLint("MissingPermission")
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
        fun onBridgeReady(version: Int, compatible: Boolean) = Unit
        fun onWiiConnectionStateChanged(state: Int) = Unit
        fun onHostReport(reportId: Int, payload: ByteArray) = Unit
        fun onBridgeError(code: Int) = Unit
        fun onDiagnostic(message: String) = Unit
        fun onError(message: String, cause: Throwable? = null) = Unit

        companion object {
            val NO_OP = object : Listener {}
        }
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager.adapter
    private val sequence = AtomicInteger(0)
    private val reassembler = BridgeFrameReassembler()
    private val writeQueue = ArrayDeque<ByteArray>()
    private val queueLock = Any()

    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var writeInFlight = false
    private var state = State.IDLE

    val isReady: Boolean
        get() = state == State.READY

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            listener.onDiagnostic("ESP32 bridge discovered: ${result.device.address}")
            adapter.bluetoothLeScanner?.stopScan(this)
            setState(State.CONNECTING)
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                result.device.connectGatt(appContext, false, gattCallback, BluetoothGatt.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                result.device.connectGatt(appContext, false, gattCallback)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            fail("ESP32 BLE scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("ESP32 BLE connection failed: GATT $status")
                closeGatt()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    this@AndroidEsp32BleTransport.gatt = gatt
                    setState(State.DISCOVERING)
                    listener.onDiagnostic("ESP32 BLE connected; discovering bridge service")
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    listener.onDiagnostic("ESP32 BLE disconnected")
                    clearSession()
                    setState(State.IDLE)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("ESP32 service discovery failed: GATT $status")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                fail("WiiRemoteX ESP32 bridge service was not found")
                return
            }

            configureBridgeService(gatt, service)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleIncoming(characteristic, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleIncoming(characteristic, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != CCC_UUID) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Unable to enable ESP32 bridge indications: GATT $status")
                return
            }

            setState(State.READY)
            listener.onDiagnostic("ESP32 BLE transport ready; waiting for BRIDGE_READY")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid != PHONE_TO_BRIDGE_UUID) return

            synchronized(queueLock) {
                writeInFlight = false
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("ESP32 BLE write failed: GATT $status")
            }
            drainWrites()
        }
    }

    fun start(): Boolean {
        if (state != State.IDLE && state != State.ERROR) return true

        val scanner = adapter?.bluetoothLeScanner ?: run {
            fail("Bluetooth LE scanner is unavailable")
            return false
        }

        clearSession()
        setState(State.SCANNING)
        listener.onDiagnostic("Scanning for WiiRemoteX ESP32 bridge")

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            true
        } catch (error: SecurityException) {
            fail("Bluetooth scan permission is required", error)
            false
        }
    }

    fun stop() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // Permission may have been revoked while the transport was active.
        }
        closeGatt()
        clearSession()
        setState(State.IDLE)
    }

    override fun send(report: HidInputReport): Boolean {
        if (!isReady) return false
        return sendBridgeMessage(
            BridgeMessageType.INPUT_REPORT,
            byteArrayOf(report.reportId.toByte()) + report.payload,
        )
    }

    fun startWiiPairing(): Boolean = sendControl(BridgeControlCode.START_WII_PAIRING)

    fun stopWiiPairing(): Boolean = sendControl(BridgeControlCode.STOP_WII_PAIRING)

    fun clearWiiBond(): Boolean = sendControl(BridgeControlCode.CLEAR_WII_BOND)

    private fun sendControl(code: Int): Boolean =
        isReady && sendBridgeMessage(
            BridgeMessageType.CONTROL,
            byteArrayOf(code.toByte()),
        )

    private fun sendBridgeMessage(type: BridgeMessageType, payload: ByteArray): Boolean {
        val currentSequence = sequence.getAndUpdate { (it + 1) and 0xFFFF }
        val packets = BridgeFrameCodec.encode(type, currentSequence, payload)

        synchronized(queueLock) {
            if (writeQueue.size + packets.size > MAX_QUEUED_PACKETS) {
                listener.onError("ESP32 BLE TX queue is full")
                return false
            }
            packets.forEach(writeQueue::addLast)
        }

        drainWrites()
        return true
    }

    private fun drainWrites() {
        val currentGatt = gatt ?: return
        val characteristic = txCharacteristic ?: return
        val packet: ByteArray

        synchronized(queueLock) {
            if (writeInFlight || writeQueue.isEmpty()) return
            packet = writeQueue.removeFirst()
            writeInFlight = true
        }

        val accepted = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(
                    characteristic,
                    packet,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                characteristic.value = packet
                @Suppress("DEPRECATION")
                currentGatt.writeCharacteristic(characteristic)
            }
        } catch (error: SecurityException) {
            listener.onError("Unable to write to ESP32 bridge", error)
            false
        }

        if (!accepted) {
            synchronized(queueLock) {
                writeInFlight = false
            }
            listener.onError("Android rejected the ESP32 BLE write")
        }
    }

    private fun configureBridgeService(gatt: BluetoothGatt, service: BluetoothGattService) {
        val tx = service.getCharacteristic(PHONE_TO_BRIDGE_UUID)
        val rx = service.getCharacteristic(BRIDGE_TO_PHONE_UUID)
        if (tx == null || rx == null) {
            fail("ESP32 bridge characteristics are incomplete")
            return
        }

        txCharacteristic = tx
        rxCharacteristic = rx

        if (!gatt.setCharacteristicNotification(rx, true)) {
            fail("Android rejected ESP32 bridge indication subscription")
            return
        }

        val descriptor = rx.getDescriptor(CCC_UUID)
        if (descriptor == null) {
            fail("ESP32 bridge CCC descriptor was not found")
            return
        }

        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

        if (!accepted) fail("Android rejected ESP32 indication descriptor write")
    }

    private fun handleIncoming(
        characteristic: BluetoothGattCharacteristic,
        packet: ByteArray,
    ) {
        if (characteristic.uuid != BRIDGE_TO_PHONE_UUID) return
        val message = reassembler.accept(packet) ?: return

        when (message.type) {
            BridgeMessageType.OUTPUT_REPORT -> {
                if (message.payload.isEmpty()) return
                listener.onHostReport(
                    message.payload[0].toInt() and 0xFF,
                    message.payload.copyOfRange(1, message.payload.size),
                )
            }

            BridgeMessageType.STATUS -> handleStatus(message.payload)
            BridgeMessageType.INPUT_REPORT,
            BridgeMessageType.CONTROL,
            -> listener.onDiagnostic("Unexpected bridge message from ESP32: ${message.type}")
        }
    }

    private fun handleStatus(payload: ByteArray) {
        if (payload.size < 2) return
        when (payload[0].toInt() and 0xFF) {
            BridgeStatusCode.WII_CONNECTION -> {
                val wiiState = payload[1].toInt() and 0xFF
                listener.onWiiConnectionStateChanged(
                    if (wiiState in WiiConnectionState.DISCONNECTED..WiiConnectionState.CONNECTED) {
                        wiiState
                    } else {
                        WiiConnectionState.DISCONNECTED
                    },
                )
            }

            BridgeStatusCode.BRIDGE_READY -> {
                val version = payload[1].toInt() and 0xFF
                listener.onBridgeReady(version, version == BridgeFrameCodec.VERSION)
            }

            BridgeStatusCode.ERROR -> listener.onBridgeError(payload[1].toInt() and 0xFF)
        }
    }

    private fun clearSession() {
        reassembler.reset()
        txCharacteristic = null
        rxCharacteristic = null
        sequence.set(0)
        synchronized(queueLock) {
            writeQueue.clear()
            writeInFlight = false
        }
        listener.onWiiConnectionStateChanged(WiiConnectionState.DISCONNECTED)
    }

    private fun closeGatt() {
        val current = gatt
        gatt = null
        try {
            current?.disconnect()
            current?.close()
        } catch (_: SecurityException) {
        }
    }

    private fun setState(newState: State) {
        state = newState
        listener.onStateChanged(newState)
    }

    private fun fail(message: String, cause: Throwable? = null) {
        setState(State.ERROR)
        listener.onError(message, cause)
    }

    private companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7C0A0001-6F4B-4A42-9D47-575258000001")
        val PHONE_TO_BRIDGE_UUID: UUID = UUID.fromString("7C0A0002-6F4B-4A42-9D47-575258000001")
        val BRIDGE_TO_PHONE_UUID: UUID = UUID.fromString("7C0A0003-6F4B-4A42-9D47-575258000001")
        val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        const val MAX_QUEUED_PACKETS = 1_024
    }
}
