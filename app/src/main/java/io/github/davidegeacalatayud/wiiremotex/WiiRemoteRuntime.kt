package io.github.davidegeacalatayud.wiiremotex

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionType
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import io.github.davidegeacalatayud.wiiremotex.core.session.SessionResult
import io.github.davidegeacalatayud.wiiremotex.core.session.VirtualIrCamera
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteEffect
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteSessionEngine
import io.github.davidegeacalatayud.wiiremotex.platform.bluetooth.AndroidHidTransport
import io.github.davidegeacalatayud.wiiremotex.platform.sensors.AndroidMotionSource
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HidStage {
    IDLE, STARTING, REGISTERED, CONNECTING, CONNECTED, ERROR,
}

data class DiagnosticEntry(
    val timestamp: String,
    val direction: String,
    val message: String,
)

data class SensorAvailability(
    val accelerometer: Boolean = false,
    val gyroscope: Boolean = false,
    val rotationVector: Boolean = false,
)

data class WiiRemoteUiState(
    val hidStage: HidStage = HidStage.IDLE,
    val wiimote: WiimoteState = WiimoteState(),
    val diagnostics: List<DiagnosticEntry> = emptyList(),
    val lastError: String? = null,
    val pointerCalibrated: Boolean = false,
    val sensors: SensorAvailability = SensorAvailability(),
)

class WiiRemoteRuntime(
    private val application: Application,
) : AndroidHidTransport.Listener {

    private val session = WiimoteSessionEngine()
    private val virtualIrCamera = VirtualIrCamera()
    private val batteryManager = application.getSystemService(BatteryManager::class.java)

    private val transport = AndroidHidTransport(
        context = application,
        listener = this,
    )

    private val motionSource = AndroidMotionSource(
        context = application,
        listener = AndroidMotionSource.Listener(::onMotionChanged),
    )

    private val _uiState = MutableStateFlow(
        WiiRemoteUiState(
            sensors = SensorAvailability(
                accelerometer = motionSource.capabilities.accelerometer,
                gyroscope = motionSource.capabilities.gyroscope,
                rotationVector = motionSource.capabilities.rotationVector,
            ),
        ),
    )
    val uiState: StateFlow<WiiRemoteUiState> = _uiState.asStateFlow()

    fun startHid() {
        if (_uiState.value.hidStage in setOf(
                HidStage.STARTING,
                HidStage.REGISTERED,
                HidStage.CONNECTING,
                HidStage.CONNECTED,
            )
        ) {
            log("SYS", "HID runtime already active")
            return
        }

        updateBatteryFromSystem()
        motionSource.start()
        logSensorCapabilities()
        log("SYS", "Starting Android HID Device profile")

        _uiState.update {
            it.copy(
                hidStage = HidStage.STARTING,
                lastError = null,
            )
        }

        if (!transport.start()) {
            fail("Android did not accept the HID Device profile request")
        }
    }

    fun stopHid() {
        motionSource.stop()
        setRumble(false)
        transport.stop()
        log("SYS", "HID runtime stopped")

        _uiState.update {
            it.copy(
                hidStage = HidStage.IDLE,
                wiimote = session.state,
                lastError = null,
            )
        }
    }

    fun onButtonChanged(button: WiiButton, pressed: Boolean) {
        apply(session.setButton(button, pressed))
    }

    fun calibratePointer() {
        val orientation = session.state.motion.orientation
        virtualIrCamera.calibrate(orientation)
        _uiState.update { it.copy(pointerCalibrated = true) }
        updateVirtualIr(emitReport = _uiState.value.hidStage == HidStage.CONNECTED)
        log(
            "SYS",
            "IR pointer calibrated at yaw=${orientation.yawDegrees.format1()} " +
                "pitch=${orientation.pitchDegrees.format1()}",
        )
    }

    fun selectExtension(type: ExtensionType) {
        val extension = when (type) {
            ExtensionType.NONE -> ExtensionState.None
            ExtensionType.NUNCHUK -> ExtensionState.Nunchuk()
            ExtensionType.MOTION_PLUS -> ExtensionState.MotionPlus(
                MotionPlusState(active = false),
            )
        }

        session.setExtension(extension, emitReport = false)
        publishSessionState()
        log("SYS", "Virtual extension selected: $type")

        if (_uiState.value.hidStage == HidStage.CONNECTED && type != ExtensionType.MOTION_PLUS) {
            apply(session.emitStatusReport())
        }
    }

    fun setNunchukStick(x: Int, y: Int) {
        val current = session.state.extension as? ExtensionState.Nunchuk ?: return
        val next = current.copy(
            value = current.value.copy(
                stickX = x.coerceIn(0, 255),
                stickY = y.coerceIn(0, 255),
            ),
        )
        applyExtensionUpdate(next)
    }

    fun setNunchukCPressed(pressed: Boolean) {
        val current = session.state.extension as? ExtensionState.Nunchuk ?: return
        applyExtensionUpdate(
            current.copy(value = current.value.copy(cPressed = pressed)),
        )
    }

    fun setNunchukZPressed(pressed: Boolean) {
        val current = session.state.extension as? ExtensionState.Nunchuk ?: return
        applyExtensionUpdate(
            current.copy(value = current.value.copy(zPressed = pressed)),
        )
    }

    override fun onRegistrationChanged(registered: Boolean) {
        log("SYS", if (registered) "HID application registered" else "HID application unregistered")
        _uiState.update {
            it.copy(
                hidStage = if (registered) HidStage.REGISTERED else HidStage.IDLE,
                wiimote = session.state,
            )
        }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        val stage = when (state) {
            BluetoothProfile.STATE_CONNECTING -> HidStage.CONNECTING
            BluetoothProfile.STATE_CONNECTED -> HidStage.CONNECTED
            BluetoothProfile.STATE_DISCONNECTING -> HidStage.REGISTERED
            BluetoothProfile.STATE_DISCONNECTED -> HidStage.REGISTERED
            else -> _uiState.value.hidStage
        }

        log("SYS", "Bluetooth connection state: ${connectionStateName(state)}")
        _uiState.update { it.copy(hidStage = stage) }

        if (stage == HidStage.CONNECTED) {
            updateVirtualIr(emitReport = false)
        }
    }

    override fun onHostReport(reportId: Int, payload: ByteArray) {
        log("RX", "0x${reportId.hex2()} ${payload.toHex()}")

        if (reportId == 0x15) {
            updateBatteryFromSystem()
        }

        val rumbleBefore = session.state.rumbleEnabled
        val extensionBefore = session.state.extension
        val result = session.onHostReport(reportId, payload)
        apply(result)

        if (result.state.rumbleEnabled != rumbleBefore) {
            setRumble(result.state.rumbleEnabled)
        }

        if (reportId == 0x13 || reportId == 0x1A) {
            updateVirtualIr(emitReport = false)
        }

        if (result.state.extension != extensionBefore) {
            publishSessionState()
            log("SYS", "Extension state changed by host: ${extensionLabel(result.state.extension)}")
        }
    }

    override fun onError(message: String, cause: Throwable?) {
        val detail = cause?.message ?: cause?.let { it::class.simpleName }
        fail(if (detail == null) message else "$message: $detail")
    }

    private fun onMotionChanged(motion: MotionState) {
        session.setMotion(motion, emitReport = false)

        when (val extension = session.state.extension) {
            ExtensionState.None -> Unit

            is ExtensionState.Nunchuk -> {
                val value = extension.value.copy(
                    accelerationX = accelerationToTenBit(motion.accelerationG.x),
                    accelerationY = accelerationToTenBit(motion.accelerationG.y),
                    accelerationZ = accelerationToTenBit(motion.accelerationG.z),
                )
                session.setExtension(extension.copy(value = value), emitReport = false)
            }

            is ExtensionState.MotionPlus -> {
                val gyro = motion.angularVelocityDegPerSec
                val value = extension.value.copy(
                    yawDegPerSec = -gyro.z,
                    rollDegPerSec = gyro.x,
                    pitchDegPerSec = gyro.y,
                )
                session.setExtension(extension.copy(value = value), emitReport = false)
            }
        }

        updateVirtualIr(emitReport = false)
        publishSessionState()

        if (
            _uiState.value.hidStage == HidStage.CONNECTED &&
            session.state.reportMode in SENSOR_REPORT_MODES
        ) {
            apply(session.emitCurrentDataReport())
        }
    }

    private fun updateVirtualIr(emitReport: Boolean) {
        if (!virtualIrCamera.isCalibrated()) {
            publishSessionState()
            return
        }

        val projected = virtualIrCamera.project(
            orientation = session.state.motion.orientation,
            enabled = session.state.infrared.enabled,
        )
        val result = session.setInfrared(projected, emitReport = emitReport)

        if (emitReport) apply(result) else publishSessionState()
    }

    private fun applyExtensionUpdate(extension: ExtensionState.Nunchuk) {
        val result = session.setExtension(
            extension = extension,
            emitReport =
                _uiState.value.hidStage == HidStage.CONNECTED &&
                    session.state.reportMode in EXTENSION_REPORT_MODES,
        )
        if (result.effects.isEmpty()) publishSessionState() else apply(result)
    }

    private fun publishSessionState() {
        _uiState.update { it.copy(wiimote = session.state) }
    }

    private fun updateBatteryFromSystem() {
        val percent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 0..100) {
            log("SYS", "Battery level unavailable; keeping Wii battery byte ${session.state.batteryLevel}")
            return
        }

        val wiiLevel = ((percent / 100.0) * 255.0).toInt().coerceIn(0, 255)
        val state = session.setBatteryLevel(wiiLevel)
        _uiState.update { it.copy(wiimote = state) }
        log("SYS", "Battery $percent% → Wii level 0x${wiiLevel.hex2()}")
    }

    private fun setRumble(enabled: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            application.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            application.getSystemService(Vibrator::class.java)
        }

        if (!enabled) {
            vibrator.cancel()
            log("SYS", "Rumble OFF")
            return
        }

        val effect = VibrationEffect.createWaveform(longArrayOf(0L, 1_000L), 0)
        vibrator.vibrate(effect)
        log("SYS", "Rumble ON")
    }

    private fun apply(result: SessionResult) {
        _uiState.update { it.copy(wiimote = result.state) }
        result.effects.forEach { effect ->
            when (effect) {
                is WiimoteEffect.SendReport -> {
                    val report = effect.report
                    val sent = transport.send(report)
                    val suffix = if (sent) "✓" else "not sent (no HID host)"
                    log("TX", "0x${report.reportId.hex2()} ${report.payload.toHex()} $suffix")
                }
            }
        }
    }

    private fun fail(message: String) {
        log("ERR", message)
        _uiState.update { it.copy(hidStage = HidStage.ERROR, lastError = message) }
    }

    private fun logSensorCapabilities() {
        val caps = motionSource.capabilities
        log(
            "SYS",
            "Sensors accel=${caps.accelerometer} gyro=${caps.gyroscope} rotation=${caps.rotationVector}",
        )
    }

    private fun log(direction: String, message: String) {
        val entry = DiagnosticEntry(
            timestamp = LocalTime.now().format(TIME_FORMAT),
            direction = direction,
            message = message,
        )
        _uiState.update {
            it.copy(diagnostics = (it.diagnostics + entry).takeLast(MAX_LOG_LINES))
        }
    }

    private fun connectionStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }

    private fun accelerationToTenBit(g: Float): Int =
        (512f + g * 102.4f).roundToInt().coerceIn(0, 1023)

    private fun extensionLabel(extension: ExtensionState): String = when (extension) {
        ExtensionState.None -> "NONE"
        is ExtensionState.Nunchuk -> "NUNCHUK"
        is ExtensionState.MotionPlus ->
            if (extension.value.active) "MOTION_PLUS_ACTIVE" else "MOTION_PLUS_INACTIVE"
    }

    private fun Float.format1(): String = "%.1f".format(this)

    private fun Int.hex2(): String =
        toString(16).uppercase().padStart(2, '0')

    private fun ByteArray.toHex(): String =
        joinToString(" ") { byte -> (byte.toInt() and 0xFF).hex2() }

    private companion object {
        const val MAX_LOG_LINES = 160
        val SENSOR_REPORT_MODES = setOf(0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x3D)
        val EXTENSION_REPORT_MODES = setOf(0x32, 0x34, 0x35, 0x36, 0x37, 0x3D)
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
