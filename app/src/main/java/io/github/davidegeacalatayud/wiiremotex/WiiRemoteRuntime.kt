package io.github.davidegeacalatayud.wiiremotex

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import io.github.davidegeacalatayud.wiiremotex.core.session.SessionResult
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteEffect
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteSessionEngine
import io.github.davidegeacalatayud.wiiremotex.platform.bluetooth.AndroidHidTransport
import io.github.davidegeacalatayud.wiiremotex.platform.sensors.AndroidMotionSource
import io.github.davidegeacalatayud.wiiremotex.platform.sensors.OrientationSample
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HidStage {
    IDLE,
    STARTING,
    REGISTERED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class DiagnosticEntry(
    val timestamp: String,
    val direction: String,
    val message: String,
)

data class WiiRemoteUiState(
    val hidStage: HidStage = HidStage.IDLE,
    val wiimote: WiimoteState = WiimoteState(),
    val diagnostics: List<DiagnosticEntry> = emptyList(),
    val lastError: String? = null,
    val motionPointerEnabled: Boolean = false,
)

class WiiRemoteRuntime(
    private val application: Application,
) : AndroidHidTransport.Listener {

    private val session = WiimoteSessionEngine()
    private val batteryManager = application.getSystemService(BatteryManager::class.java)

    private val transport = AndroidHidTransport(
        context = application,
        listener = this,
    )

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var reportFuture: ScheduledFuture<*>? = null

    private var latestOrientation: OrientationSample? = null
    private var pointerCenterYaw: Float? = null
    private var pointerCenterPitch: Float? = null

    private val motionSource = AndroidMotionSource(
        context = application,
        listener = object : AndroidMotionSource.Listener {
            override fun onMotionChanged(motion: io.github.davidegeacalatayud.wiiremotex.core.model.MotionState) {
                apply(session.setMotion(motion))
            }

            override fun onOrientationChanged(orientation: OrientationSample) {
                latestOrientation = orientation
                updateMotionPointer(orientation)
            }
        },
    )

    private val _uiState = MutableStateFlow(WiiRemoteUiState())
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
        if (motionSource.start()) {
            log("SYS", "Android accelerometer/gyroscope source started")
        } else {
            log("ERR", "No compatible Android motion sensors available")
        }
        startReportScheduler()
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
        setRumble(false)
        stopReportScheduler()
        motionSource.stop()
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

    fun setIrPointer(normalizedX: Float, normalizedY: Float, enabled: Boolean = true) {
        val x = (normalizedX.coerceIn(0f, 1f) * 1023f).toInt()
        val y = (normalizedY.coerceIn(0f, 1f) * 767f).toInt()
        val separation = 120
        val points = listOf(
            InfraredPoint(
                x = (x - separation).coerceIn(0, 1023),
                y = y,
                size = 6,
                visible = enabled,
            ),
            InfraredPoint(
                x = (x + separation).coerceIn(0, 1023),
                y = y,
                size = 6,
                visible = enabled,
            ),
            InfraredPoint(),
            InfraredPoint(),
        )

        apply(
            session.setInfrared(
                enabled = enabled,
                points = points,
            ),
        )
    }

    fun setIrEnabled(enabled: Boolean) {
        val current = session.state.infrared
        apply(
            session.setInfrared(
                enabled = enabled,
                points = current.points.map { point ->
                    point.copy(visible = enabled && point.visible)
                },
            ),
        )
    }

    fun setNunchukEnabled(enabled: Boolean) {
        apply(
            session.setNunchuk(
                session.state.nunchuk.copy(connected = enabled),
            ),
        )
    }

    fun setNunchukStick(normalizedX: Float, normalizedY: Float) {
        val current = session.state.nunchuk
        apply(
            session.setNunchuk(
                current.copy(
                    connected = true,
                    stickX = (normalizedX.coerceIn(-1f, 1f) * 96f + 128f).toInt(),
                    stickY = (normalizedY.coerceIn(-1f, 1f) * 96f + 128f).toInt(),
                ),
            ),
        )
    }

    fun setNunchukButton(cPressed: Boolean? = null, zPressed: Boolean? = null) {
        val current = session.state.nunchuk
        apply(
            session.setNunchuk(
                current.copy(
                    connected = true,
                    cPressed = cPressed ?: current.cPressed,
                    zPressed = zPressed ?: current.zPressed,
                ),
            ),
        )
    }

    fun setMotionPlusEnabled(enabled: Boolean) {
        val current = session.state.motionPlus
        apply(
            session.setMotionPlus(
                current.copy(
                    present = enabled,
                    active = if (enabled) current.active else false,
                    extensionConnected = session.state.nunchuk.connected,
                ),
            ),
        )
        log(
            "SYS",
            if (enabled) {
                "MotionPlus present; waiting for Wii activation at 0xA600FE"
            } else {
                "MotionPlus removed"
            },
        )
    }

    fun setMotionPointerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(motionPointerEnabled = enabled) }

        if (enabled) {
            setIrEnabled(true)
            recenterMotionPointer()
            log("SYS", "Motion Pointer enabled")
        } else {
            log("SYS", "Motion Pointer disabled")
        }
    }

    fun recenterMotionPointer() {
        val orientation = latestOrientation
        if (orientation != null) {
            pointerCenterYaw = orientation.yawRadians
            pointerCenterPitch = orientation.pitchRadians
        }
        motionSource.calibrateGyroscope()
        log("SYS", "Motion Pointer and gyro recentered")
    }

    override fun onRegistrationChanged(registered: Boolean) {
        log(
            "SYS",
            if (registered) "HID application registered" else "HID application unregistered",
        )

        _uiState.update {
            it.copy(
                hidStage = if (registered) HidStage.REGISTERED else HidStage.IDLE,
                wiimote = session.state,
            )
        }
    }

    override fun onConnectionStateChanged(
        device: BluetoothDevice?,
        state: Int,
    ) {
        val stage = when (state) {
            BluetoothProfile.STATE_CONNECTING -> HidStage.CONNECTING
            BluetoothProfile.STATE_CONNECTED -> HidStage.CONNECTED
            BluetoothProfile.STATE_DISCONNECTING -> HidStage.REGISTERED
            BluetoothProfile.STATE_DISCONNECTED -> HidStage.REGISTERED
            else -> _uiState.value.hidStage
        }

        log("SYS", "Bluetooth connection state: ${connectionStateName(state)}")
        _uiState.update { it.copy(hidStage = stage) }
    }

    override fun onHostReport(
        reportId: Int,
        payload: ByteArray,
    ) {
        log("RX", "0x${reportId.hex2()} ${payload.toHex()}")

        if (reportId == 0x15) {
            updateBatteryFromSystem()
        }

        val rumbleBefore = session.state.rumbleEnabled
        val result = session.onHostReport(reportId, payload)
        apply(result)

        if (result.state.rumbleEnabled != rumbleBefore) {
            setRumble(result.state.rumbleEnabled)
        }
    }

    override fun onError(
        message: String,
        cause: Throwable?,
    ) {
        val detail = cause?.message ?: cause?.let { it::class.simpleName }
        fail(if (detail == null) message else "$message: $detail")
    }

    private fun startReportScheduler() {
        if (reportFuture?.isCancelled == false && reportFuture?.isDone == false) {
            return
        }

        reportFuture = scheduler.scheduleAtFixedRate(
            {
                if (_uiState.value.hidStage == HidStage.CONNECTED) {
                    apply(
                        result = session.nextContinuousReport(),
                        logTx = false,
                    )
                }
            },
            0L,
            CONTINUOUS_REPORT_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )

        log("SYS", "Continuous report scheduler started at 100 Hz")
    }

    private fun stopReportScheduler() {
        reportFuture?.cancel(false)
        reportFuture = null
    }

    private fun updateMotionPointer(orientation: OrientationSample) {
        if (!_uiState.value.motionPointerEnabled) return

        val centerYaw = pointerCenterYaw
        val centerPitch = pointerCenterPitch
        if (centerYaw == null || centerPitch == null) {
            pointerCenterYaw = orientation.yawRadians
            pointerCenterPitch = orientation.pitchRadians
            return
        }

        val yawDelta = wrapRadians(orientation.yawRadians - centerYaw)
        val pitchDelta = orientation.pitchRadians - centerPitch

        val normalizedX =
            (0.5f - yawDelta / HORIZONTAL_POINTER_RANGE_RAD).coerceIn(0f, 1f)
        val normalizedY =
            (0.5f + pitchDelta / VERTICAL_POINTER_RANGE_RAD).coerceIn(0f, 1f)

        setIrPointer(
            normalizedX = normalizedX,
            normalizedY = normalizedY,
            enabled = true,
        )
    }

    private fun wrapRadians(value: Float): Float {
        var wrapped = value
        val fullTurn = (2.0 * PI).toFloat()

        while (wrapped > PI.toFloat()) wrapped -= fullTurn
        while (wrapped < -PI.toFloat()) wrapped += fullTurn

        return wrapped
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

        val effect = VibrationEffect.createWaveform(
            longArrayOf(0L, 1_000L),
            0,
        )
        vibrator.vibrate(effect)
        log("SYS", "Rumble ON")
    }

    private fun apply(
        result: SessionResult,
        logTx: Boolean = true,
    ) {
        _uiState.update { it.copy(wiimote = result.state) }

        result.effects.forEach { effect ->
            when (effect) {
                is WiimoteEffect.SendReport -> {
                    val report = effect.report
                    val sent = transport.send(report)

                    if (logTx) {
                        val suffix = if (sent) "✓" else "not sent (no HID host)"
                        log("TX", "0x${report.reportId.hex2()} ${report.payload.toHex()} $suffix")
                    }
                }
            }
        }
    }

    private fun fail(message: String) {
        log("ERR", message)
        _uiState.update {
            it.copy(
                hidStage = HidStage.ERROR,
                lastError = message,
            )
        }
    }

    private fun log(direction: String, message: String) {
        val entry = DiagnosticEntry(
            timestamp = LocalTime.now().format(TIME_FORMAT),
            direction = direction,
            message = message,
        )

        _uiState.update {
            it.copy(
                diagnostics = (it.diagnostics + entry).takeLast(MAX_LOG_LINES),
            )
        }
    }

    private fun connectionStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }

    private fun Int.hex2(): String =
        toString(16).uppercase().padStart(2, '0')

    private fun ByteArray.toHex(): String =
        joinToString(" ") { byte ->
            (byte.toInt() and 0xFF).hex2()
        }

    private companion object {
        const val MAX_LOG_LINES = 120
        const val CONTINUOUS_REPORT_INTERVAL_MS = 10L

        val HORIZONTAL_POINTER_RANGE_RAD: Float = Math.toRadians(60.0).toFloat()
        val VERTICAL_POINTER_RANGE_RAD: Float = Math.toRadians(45.0).toFloat()

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
