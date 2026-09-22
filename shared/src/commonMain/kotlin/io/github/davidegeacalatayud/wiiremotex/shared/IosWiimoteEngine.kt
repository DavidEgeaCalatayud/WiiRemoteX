package io.github.davidegeacalatayud.wiiremotex.shared

import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredMode
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionCalibrationProfile
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.session.SessionResult
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteEffect
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteSessionEngine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swift-friendly facade around the exact same WiimoteSessionEngine used by Android.
 *
 * Every mutating call returns zero or more BLE packets ready to write to the ESP32
 * bridge characteristic. Host output reports arriving from the bridge are reassembled
 * and fed back into the same session engine.
 */
class IosWiimoteEngine(
    private var calibration: MotionCalibrationProfile = MotionCalibrationProfile.DEFAULT,
) {
    private val session = WiimoteSessionEngine()
    private val reassembler = BridgeFrameReassembler()

    private var sequence = 0
    private var pointerEnabled = false
    private var pointerCenterYaw = 0f
    private var pointerCenterPitch = 0f
    private var smoothedPointerX = 0.5f
    private var smoothedPointerY = 0.5f

    var wiiConnectionState: Int = WiiConnectionState.DISCONNECTED
        private set

    val reportMode: Int
        get() = session.state.reportMode

    val rumbleEnabled: Boolean
        get() = session.state.rumbleEnabled

    val infraredEnabled: Boolean
        get() = session.state.infrared.enabled

    val nunchukConnected: Boolean
        get() = session.state.nunchuk.connected

    val motionPlusPresent: Boolean
        get() = session.state.motionPlus.present

    fun buttonChanged(buttonName: String, pressed: Boolean): List<ByteArray> {
        val button = WiiButton.entries.firstOrNull {
            it.name == buttonName.uppercase()
        } ?: return emptyList()

        return encodeEffects(session.setButton(button, pressed))
    }

    fun batteryLevelChanged(level: Int) {
        session.setBatteryLevel(level)
    }

    fun physicalMotionChanged(
        accelerationXG: Float,
        accelerationYG: Float,
        accelerationZG: Float,
        gyroXRadPerSec: Float,
        gyroYRadPerSec: Float,
        gyroZRadPerSec: Float,
    ): List<ByteArray> {
        val accelerometer = calibration.accelerometer

        return encodeEffects(
            session.setMotion(
                MotionState(
                    accelerationX = accelerationToWiimote(
                        accelerationXG,
                        accelerometer.x.zeroG,
                        accelerometer.x.unitsPerG,
                    ),
                    accelerationY = accelerationToWiimote(
                        -accelerationYG,
                        accelerometer.y.zeroG,
                        accelerometer.y.unitsPerG,
                    ),
                    accelerationZ = accelerationToWiimote(
                        accelerationZG,
                        accelerometer.z.zeroG,
                        accelerometer.z.unitsPerG,
                    ),
                    gyroPitch = angularVelocityToMotionPlus(
                        gyroXRadPerSec - calibration.gyroBiasXRadPerSec,
                    ),
                    gyroRoll = angularVelocityToMotionPlus(
                        -(gyroYRadPerSec - calibration.gyroBiasYRadPerSec),
                    ),
                    gyroYaw = angularVelocityToMotionPlus(
                        gyroZRadPerSec - calibration.gyroBiasZRadPerSec,
                    ),
                ),
            ),
        )
    }

    fun updateGyroBias(
        xRadPerSec: Float,
        yRadPerSec: Float,
        zRadPerSec: Float,
    ) {
        calibration = calibration.copy(
            gyroBiasXRadPerSec = xRadPerSec,
            gyroBiasYRadPerSec = yRadPerSec,
            gyroBiasZRadPerSec = zRadPerSec,
        )
    }

    fun setIrEnabled(enabled: Boolean): List<ByteArray> {
        val current = session.state.infrared
        val mode = if (enabled) {
            irModeForReport(session.state.reportMode)
        } else {
            InfraredMode.OFF
        }

        return encodeEffects(
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

    fun setIrPointer(
        normalizedX: Float,
        normalizedY: Float,
        enabled: Boolean = true,
    ): List<ByteArray> {
        val x = (normalizedX.coerceIn(0f, 1f) * 1023f).toInt()
        val y = (normalizedY.coerceIn(0f, 1f) * 767f).toInt()
        val separation = 120

        return encodeEffects(
            session.setInfrared(
                enabled = enabled,
                points = listOf(
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
                ),
            ),
        )
    }

    fun setMotionPointerEnabled(
        enabled: Boolean,
        currentYawRadians: Float,
        currentPitchRadians: Float,
    ): List<ByteArray> {
        pointerEnabled = enabled

        if (!enabled) return emptyList()

        pointerCenterYaw = currentYawRadians
        pointerCenterPitch = currentPitchRadians
        smoothedPointerX = 0.5f
        smoothedPointerY = 0.5f

        return setIrEnabled(true) + setIrPointer(0.5f, 0.5f, true)
    }

    fun recenterMotionPointer(
        currentYawRadians: Float,
        currentPitchRadians: Float,
    ): List<ByteArray> {
        pointerCenterYaw = currentYawRadians
        pointerCenterPitch = currentPitchRadians
        smoothedPointerX = 0.5f
        smoothedPointerY = 0.5f
        return if (pointerEnabled) setIrPointer(0.5f, 0.5f, true) else emptyList()
    }

    fun orientationChanged(
        yawRadians: Float,
        pitchRadians: Float,
    ): List<ByteArray> {
        if (!pointerEnabled) return emptyList()

        val yawDelta = wrapRadians(yawRadians - pointerCenterYaw)
        val pitchDelta = pitchRadians - pointerCenterPitch
        val horizontalRange =
            degreesToRadians(calibration.pointerHorizontalRangeDegrees)
        val verticalRange =
            degreesToRadians(calibration.pointerVerticalRangeDegrees)

        val targetX = (0.5f - yawDelta / horizontalRange).coerceIn(0f, 1f)
        val targetY = (0.5f + pitchDelta / verticalRange).coerceIn(0f, 1f)

        val deltaX = targetX - smoothedPointerX
        val deltaY = targetY - smoothedPointerY

        if (
            abs(deltaX) < calibration.pointerDeadZone &&
            abs(deltaY) < calibration.pointerDeadZone
        ) {
            return emptyList()
        }

        smoothedPointerX += deltaX * calibration.pointerSmoothingAlpha
        smoothedPointerY += deltaY * calibration.pointerSmoothingAlpha

        return setIrPointer(smoothedPointerX, smoothedPointerY, true)
    }

    fun setNunchukEnabled(enabled: Boolean): List<ByteArray> =
        encodeEffects(
            session.setNunchuk(
                session.state.nunchuk.copy(connected = enabled),
            ),
        )

    fun setNunchukStick(
        normalizedX: Float,
        normalizedY: Float,
    ): List<ByteArray> {
        val current = session.state.nunchuk
        return encodeEffects(
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

    fun setNunchukButtons(
        cPressed: Boolean,
        zPressed: Boolean,
    ): List<ByteArray> {
        val current = session.state.nunchuk
        return encodeEffects(
            session.setNunchuk(
                current.copy(
                    connected = true,
                    cPressed = cPressed,
                    zPressed = zPressed,
                ),
            ),
        )
    }

    fun setMotionPlusEnabled(enabled: Boolean): List<ByteArray> {
        val current = session.state.motionPlus
        return encodeEffects(
            session.setMotionPlus(
                MotionPlusState(
                    present = enabled,
                    initialized = if (enabled) current.initialized else false,
                    active = if (enabled) current.active else false,
                    activationMode = current.activationMode,
                    passThroughNunchuk = if (enabled) current.passThroughNunchuk else false,
                    yawSlow = current.yawSlow,
                    rollSlow = current.rollSlow,
                    pitchSlow = current.pitchSlow,
                    extensionConnected = session.state.nunchuk.connected,
                ),
            ),
        )
    }

    fun continuousTick(): List<ByteArray> =
        encodeEffects(session.nextContinuousReport())

    fun startWiiPairingPackets(): List<ByteArray> =
        encodeBridgeMessage(
            BridgeMessageType.CONTROL,
            byteArrayOf(BridgeControlCode.START_WII_PAIRING.toByte()),
        )

    fun stopWiiPairingPackets(): List<ByteArray> =
        encodeBridgeMessage(
            BridgeMessageType.CONTROL,
            byteArrayOf(BridgeControlCode.STOP_WII_PAIRING.toByte()),
        )

    fun clearWiiBondPackets(): List<ByteArray> =
        encodeBridgeMessage(
            BridgeMessageType.CONTROL,
            byteArrayOf(BridgeControlCode.CLEAR_WII_BOND.toByte()),
        )

    fun acceptBridgePacket(packet: ByteArray): List<ByteArray> {
        val message = reassembler.accept(packet) ?: return emptyList()

        return when (message.type) {
            BridgeMessageType.OUTPUT_REPORT -> {
                if (message.payload.isEmpty()) return emptyList()

                val reportId = message.payload[0].toInt() and 0xFF
                val payload =
                    if (message.payload.size > 1) {
                        message.payload.copyOfRange(1, message.payload.size)
                    } else {
                        byteArrayOf()
                    }

                encodeEffects(session.onHostReport(reportId, payload))
            }

            BridgeMessageType.STATUS -> {
                handleStatus(message.payload)
                emptyList()
            }

            BridgeMessageType.INPUT_REPORT,
            BridgeMessageType.CONTROL,
            -> emptyList()
        }
    }

    fun resetBridgeSession() {
        reassembler.reset()
        wiiConnectionState = WiiConnectionState.DISCONNECTED
    }

    private fun handleStatus(payload: ByteArray) {
        if (payload.size < 2) return

        when (payload[0].toInt() and 0xFF) {
            BridgeStatusCode.WII_CONNECTION -> {
                wiiConnectionState = payload[1].toInt() and 0xFF
            }
        }
    }

    private fun encodeEffects(result: SessionResult): List<ByteArray> =
        result.effects.flatMap { effect ->
            when (effect) {
                is WiimoteEffect.SendReport -> {
                    val report = effect.report
                    encodeBridgeMessage(
                        type = BridgeMessageType.INPUT_REPORT,
                        payload = byteArrayOf(report.reportId.toByte()) + report.payload,
                    )
                }
            }
        }

    private fun encodeBridgeMessage(
        type: BridgeMessageType,
        payload: ByteArray,
    ): List<ByteArray> {
        val currentSequence = sequence
        sequence = (sequence + 1) and 0xFFFF
        return BridgeFrameCodec.encode(type, currentSequence, payload)
    }

    private fun accelerationToWiimote(
        valueG: Float,
        zeroG: Int,
        unitsPerG: Int,
    ): Int =
        (zeroG + valueG * unitsPerG)
            .roundToInt()
            .coerceIn(0, 1023)

    private fun angularVelocityToMotionPlus(valueRadS: Float): Int {
        val degreesPerSecond = valueRadS * 180.0 / PI
        return (MOTION_PLUS_ZERO + degreesPerSecond * MOTION_PLUS_UNITS_PER_DEGREE)
            .roundToInt()
            .coerceIn(0, 0x3FFF)
    }

    private fun mapNunchukAxis(
        normalized: Float,
        min: Int,
        center: Int,
        max: Int,
    ): Int {
        val value = normalized.coerceIn(-1f, 1f)
        return if (value >= 0f) {
            (center + value * (max - center)).roundToInt()
        } else {
            (center + value * (center - min)).roundToInt()
        }.coerceIn(min, max)
    }

    private fun irModeForReport(reportMode: Int): InfraredMode =
        when (reportMode) {
            0x33 -> InfraredMode.EXTENDED
            0x36, 0x37 -> InfraredMode.BASIC
            0x3E, 0x3F -> InfraredMode.FULL
            else -> InfraredMode.EXTENDED
        }

    private fun wrapRadians(value: Float): Float {
        var result = value
        val fullTurn = (2.0 * PI).toFloat()
        val halfTurn = PI.toFloat()

        while (result > halfTurn) result -= fullTurn
        while (result < -halfTurn) result += fullTurn
        return result
    }

    private fun degreesToRadians(value: Float): Float =
        (value * PI / 180.0).toFloat()

    private companion object {
        const val MOTION_PLUS_ZERO = 0x1F7F
        const val MOTION_PLUS_UNITS_PER_DEGREE = 13.768

        const val NUNCHUK_CENTER = 128
        const val NUNCHUK_X_MIN = 35
        const val NUNCHUK_X_MAX = 228
        const val NUNCHUK_Y_MIN = 27
        const val NUNCHUK_Y_MAX = 220
    }
}
