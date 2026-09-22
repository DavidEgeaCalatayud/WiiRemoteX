package io.github.davidegeacalatayud.wiiremotex.core.model

data class MotionCalibrationProfile(
    val accelerometer: WiimoteAccelerometerCalibration = WiimoteAccelerometerCalibration.DEFAULT,
    val gyroBiasXRadPerSec: Float = 0f,
    val gyroBiasYRadPerSec: Float = 0f,
    val gyroBiasZRadPerSec: Float = 0f,
    val pointerHorizontalRangeDegrees: Float = 60f,
    val pointerVerticalRangeDegrees: Float = 45f,
    val pointerSmoothingAlpha: Float = 0.22f,
    val pointerDeadZone: Float = 0.0025f,
) {
    init {
        require(pointerHorizontalRangeDegrees > 0f)
        require(pointerVerticalRangeDegrees > 0f)
        require(pointerSmoothingAlpha in 0f..1f)
        require(pointerDeadZone >= 0f)
    }

    companion object {
        val DEFAULT = MotionCalibrationProfile()
    }
}
