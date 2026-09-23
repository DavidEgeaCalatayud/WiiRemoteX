package io.github.davidegeacalatayud.wiiremotex.platform.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HidInputReport
import io.github.davidegeacalatayud.wiiremotex.core.protocol.WiiHidDescriptor
import io.github.davidegeacalatayud.wiiremotex.core.session.HidTransport
import java.util.concurrent.Executor

class AndroidHidTransport(
    context: Context,
    private val executor: Executor = context.mainExecutor,
    private val listener: Listener = Listener.NO_OP,
) : HidTransport {

    interface Listener {
        fun onRegistrationChanged(registered: Boolean) = Unit
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) = Unit
        fun onHostReport(reportId: Int, payload: ByteArray) = Unit
        fun onGetReport(type: Int, reportId: Int, bufferSize: Int): ByteArray? = null
        fun onSetReport(type: Int, reportId: Int, payload: ByteArray): Boolean = false
        fun onSetProtocol(protocol: Int) = Unit
        fun onVirtualCableUnplug(device: BluetoothDevice?) = Unit
        fun onError(message: String, cause: Throwable? = null) = Unit

        companion object {
            val NO_OP = object : Listener {}
        }
    }

    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java).adapter
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (pluggedDevice != null) hostDevice = pluggedDevice
            listener.onRegistrationChanged(registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) hostDevice = device
            if (state == BluetoothProfile.STATE_DISCONNECTED && hostDevice == device) hostDevice = null
            listener.onConnectionStateChanged(device, state)
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            if (device != null) hostDevice = device
            listener.onHostReport(reportId.toInt() and 0xFF, data?.copyOf() ?: byteArrayOf())
        }

        override fun onGetReport(
            device: BluetoothDevice?,
            type: Byte,
            id: Byte,
            bufferSize: Int,
        ) {
            if (device == null) return
            hostDevice = device

            val reportId = id.toInt() and 0xFF
            val reportType = type.toInt() and 0xFF
            val response = listener.onGetReport(reportType, reportId, bufferSize)

            try {
                if (response == null) {
                    hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                } else {
                    val payload =
                        if (bufferSize > 0 && response.size < bufferSize) response.copyOf(bufferSize)
                        else response
                    hidDevice?.replyReport(device, type, id, payload)
                }
            } catch (error: SecurityException) {
                listener.onError("Unable to answer HID GET_REPORT", error)
            }
        }

        override fun onSetReport(
            device: BluetoothDevice?,
            type: Byte,
            id: Byte,
            data: ByteArray?,
        ) {
            if (device == null) return
            hostDevice = device

            val accepted = listener.onSetReport(
                type = type.toInt() and 0xFF,
                reportId = id.toInt() and 0xFF,
                payload = data?.copyOf() ?: byteArrayOf(),
            )

            try {
                hidDevice?.reportError(
                    device,
                    if (accepted) {
                        BluetoothHidDevice.ERROR_RSP_SUCCESS
                    } else {
                        BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID
                    },
                )
            } catch (error: SecurityException) {
                listener.onError("Unable to answer HID SET_REPORT", error)
            }
        }

        override fun onSetProtocol(device: BluetoothDevice?, protocol: Byte) {
            if (device != null) hostDevice = device
            listener.onSetProtocol(protocol.toInt() and 0xFF)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            listener.onVirtualCableUnplug(device)
            if (hostDevice == device) hostDevice = null
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as? BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                hostDevice = null
            }
        }
    }

    fun start(): Boolean = try {
        adapter?.getProfileProxy(appContext, serviceListener, BluetoothProfile.HID_DEVICE) ?: false
    } catch (error: SecurityException) {
        listener.onError("BLUETOOTH_CONNECT permission is required", error)
        false
    }

    private fun registerApp() {
        val hid = hidDevice ?: return
        val subclass = (
            BluetoothHidDevice.SUBCLASS1_NONE.toInt() or
                BluetoothHidDevice.SUBCLASS2_GAMEPAD.toInt()
            ).toByte()

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Nintendo RVL-CNT-01",
            "WiiRemoteX experimental Wii Remote",
            "WiiRemoteX",
            subclass,
            WiiHidDescriptor.reportDescriptor,
        )

        try {
            if (!hid.registerApp(sdp, null, null, executor, callback)) {
                listener.onError("Android rejected the HID registration command")
            }
        } catch (error: SecurityException) {
            listener.onError("BLUETOOTH_CONNECT permission is required", error)
        }
    }

    override fun send(report: HidInputReport): Boolean {
        val hid = hidDevice ?: return false
        val host = hostDevice ?: return false
        return try {
            hid.sendReport(host, report.reportId, report.payload)
        } catch (error: SecurityException) {
            listener.onError("Unable to send HID report", error)
            false
        }
    }

    fun stop() {
        try {
            hidDevice?.unregisterApp()
            hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (error: SecurityException) {
            listener.onError("Unable to close HID profile", error)
        } finally {
            hidDevice = null
            hostDevice = null
        }
    }
}
