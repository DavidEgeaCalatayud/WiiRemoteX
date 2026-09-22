package io.github.davidegeacalatayud.wiiremotex

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredMode
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
import io.github.davidegeacalatayud.wiiremotex.platform.sensors.MotionCalibrationStore
import io.github.davidegeacalatayud.wiiremotex.platform.sensors.OrientationSample
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
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
    private val calibrationStore = MotionCalibrationStore(application)
    private val traceRecorder = HardwareTraceRecorder()

    private val transport = AndroidHidTransport(
        context = application,
        listener = this,
    )

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var reportFuture: ScheduledFuture<*>? = null

    private var latestOrientation: OrientationSample? = null
    private var pointerCenterYaw: Float? = null
    private var pointerCenterPitch: Float? = null
    private var smoothedPointerX = 0.5f
    private var smoothedPointerY = 0.5f

    private val motionSource = AndroidMotionSource(
        context = application,
        initialCalibration = calibrationStore.load(),
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

        traceRecorder.clear()
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
        val mode = if (enabled) {
            irModeForReport(session.state.reportMode)
        } else {
            InfraredMode.OFF
        }

        apply(
            session.setInfrared(
                enabled = enabled,
                pixelClockEnabled = enabled,
                logicEnabled = enabled,
                configured = enabled,
                mode = mode,
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
                    stickX = mapNunchukAxis(
                        normalizedX,
                        NUNCHUK_X_MIN,
                        NUNCHUK_CENTER,
                        NUNCHUK_X_MAX,
                    ),
                    stickY = mapNunchukAxis(
                        normalizedY,
                        NUNCHUK_Y_MIN,
                        NUNCHUK_CENTER,
                        NUNCHUK_Y_MAX,
                    ),
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
        smoothedPointerX = 0.5f
        smoothedPointerY = 0.5f
        val calibration = motionSource.calibrateGyroscope()
        calibrationStore.save(calibration)
        setIrPointer(0.5f, 0.5f, enabled = true)
        log(
            "SYS",
            "Motion Pointer recentered; gyro bias persisted " +
                "x=${calibration.gyroBiasXRadPerSec} " +
                "y=${calibration.gyroBiasYRadPerSec} " +
                "z=${calibration.gyroBiasZRadPerSec}",
        )
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
        traceRecorder.record(
            direction = "RX",
            event = "interrupt_report",
            connectionState = _uiState.value.hidStage.name,
            state = session.state,
            reportId = reportId,
            payload = payload,
        )
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

    override fun onGetReport(
        type: Int,
        reportId: Int,
        bufferSize: Int,
    ): ByteArray? {
        traceRecorder.record(
            direction = "HID_CONTROL",
            event = "get_report type=$type buffer_size=$bufferSize",
            connectionState = _uiState.value.hidStage.name,
            state = session.state,
            reportId = reportId,
        )
        log("HID", "GET_REPORT type=$type id=0x${reportId.hex2()} size=$bufferSize")
        return null
    }

    override fun onSetReport(
        type: Int,
        reportId: Int,
        payload: ByteArray,
    ): Boolean {
        traceRecorder.record(
            direction = "HID_CONTROL",
            event = "set_report type=$type",
            connectionState = _uiState.value.hidStage.name,
            state = session.state,
            reportId = reportId,
            payload = payload,
        )
        log("HID", "SET_REPORT type=$type id=0x${reportId.hex2()} ${payload.toHex()}")

        // HID report type 2 is OUTPUT. Route it through the same Wii output-report decoder.
        if (type == 2) {
            onHostReport(reportId, payload)
            return true
        }
        return false
    }

    override fun onSetProtocol(protocol: Int) {
        traceRecorder.record(
            direction = "HID_CONTROL",
            event = "set_protocol protocol=$protocol",
            connectionState = _uiState.value.hidStage.name,
            state = session.state,
        )
        log("HID", "SET_PROTOCOL $protocol")
    }

    override fun onVirtualCableUnplug(device: BluetoothDevice?) {
        traceRecorder.record(
            direction = "HID_CONTROL",
            event = "virtual_cable_unplug",
            connectionState = _uiState.value.hidStage.name,
            state = session.state,
        )
        log("HID", "Virtual cable unplug")
        _uiState.update { it.copy(hidStage = HidStage.REGISTERED) }
    }

    override fun onError(
        message: String,
        cause: Throwable?,
    ) {
        val detail = cause?.message ?: cause?.let { it::class.simpleName }
        fail(if (detail == null) message else "$message: $detail")
    }

    private fun irModeForReport(reportMode: Int): InfraredMode =
        when (reportMode) {
            0x36, 0x37 -> InfraredMode.BASIC
            0x3E, 0x3F -> InfraredMode.FULL
            else -> InfraredMode.EXTENDED
        }

    private fun mapNunchukAxis(
        value: Float,
        min: Int,
        center: Int,
        max: Int,
    ): Int {
        val normalized = value.coerceIn(-1f, 1f)
        return if (normalized < 0f) {
            (center + normalized * (center - min)).toInt()
        } else {
            (center + normalized * (max - center)).toInt()
        }.coerceIn(min, max)
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

        val calibration = motionSource.currentCalibration()
        val horizontalRange =
            Math.toRadians(calibration.pointerHorizontalRangeDegrees.toDouble()).toFloat()
        val verticalRange =
            Math.toRadians(calibration.pointerVerticalRangeDegrees.toDouble()).toFloat()

        val targetX =
            (0.5f - yawDelta / horizontalRange).coerceIn(0f, 1f)
        val targetY =
            (0.5f + pitchDelta / verticalRange).coerceIn(0f, 1f)

        val deltaX = targetX - smoothedPointerX
        val deltaY = targetY - smoothedPointerY

        if (
            abs(deltaX) < calibration.pointerDeadZone &&
            abs(deltaY) < calibration.pointerDeadZone
        ) {
            return
        }

        smoothedPointerX += deltaX * calibration.pointerSmoothingAlpha
        smoothedPointerY += deltaY * calibration.pointerSmoothingAlpha

        setIrPointer(
            normalizedX = smoothedPointerX,
            normalizedY = smoothedPointerY,
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
                    traceRecorder.record(
                        direction = "TX",
                        event = if (sent) "send_report" else "send_report_not_sent",
                        connectionState = _uiState.value.hidStage.name,
                        state = result.state,
                        reportId = report.reportId,
                        payload = report.payload,
                    )

                    if (logTx) {
                        val suffix = if (sent) "✓" else "not sent (no HID host)"
                        log("TX", "0x${report.reportId.hex2()} ${report.payload.toHex()} $suffix")
                    }
                }
            }
        }
    }

    fun exportHardwareTraceJson(): String = traceRecorder.exportJson()

    fun clearHardwareTrace() {
        traceRecorder.clear()
        log("SYS", "Hardware trace cleared")
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

        if (direction != "RX" && direction != "TX") {
            traceRecorder.record(
                direction = direction,
                event = message,
                connectionState = _uiState.value.hidStage.name,
                state = session.state,
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

        const val NUNCHUK_CENTER = 128
        const val NUNCHUK_X_MIN = 35
        const val NUNCHUK_X_MAX = 228
        const val NUNCHUK_Y_MIN = 27
        const val NUNCHUK_Y_MAX = 220


        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
