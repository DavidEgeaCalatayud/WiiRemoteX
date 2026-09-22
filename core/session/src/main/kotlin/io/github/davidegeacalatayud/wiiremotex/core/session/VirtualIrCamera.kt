package io.github.davidegeacalatayud.wiiremotex.core.session

import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredState
import io.github.davidegeacalatayud.wiiremotex.core.model.Orientation
import kotlin.math.roundToInt

class VirtualIrCamera(
    private val horizontalFieldOfViewDegrees: Float = 33f,
    private val verticalFieldOfViewDegrees: Float = 23f,
    private val virtualSensorBarSpacingPixels: Int = 180,
) {
    private var center: Orientation? = null

    fun calibrate(orientation: Orientation) {
        center = orientation
    }

    fun clearCalibration() {
        center = null
    }

    fun isCalibrated(): Boolean = center != null

    fun project(
        orientation: Orientation,
        enabled: Boolean,
    ): InfraredState {
        val origin = center
        if (!enabled || origin == null) {
            return InfraredState(enabled = enabled)
        }

        val yawDelta = shortestAngleDegrees(
            orientation.yawDegrees - origin.yawDegrees,
        )
        val pitchDelta = orientation.pitchDegrees - origin.pitchDegrees

        val x = (
            IR_CENTER_X -
                (yawDelta / (horizontalFieldOfViewDegrees / 2f)) * IR_CENTER_X
            ).roundToInt()

        val y = (
            IR_CENTER_Y -
                (pitchDelta / (verticalFieldOfViewDegrees / 2f)) * IR_CENTER_Y
            ).roundToInt()

        val halfSpacing = virtualSensorBarSpacingPixels / 2

        return InfraredState(
            enabled = true,
            points = listOf(
                point(x - halfSpacing, y),
                point(x + halfSpacing, y),
                InfraredPoint(),
                InfraredPoint(),
            ),
        )
    }

    private fun point(x: Int, y: Int): InfraredPoint {
        val visible = x in 0 until IR_WIDTH && y in 0 until IR_HEIGHT
        return if (visible) {
            InfraredPoint(
                x = x,
                y = y,
                size = 8,
                visible = true,
            )
        } else {
            InfraredPoint()
        }
    }

    private fun shortestAngleDegrees(value: Float): Float {
        var angle = value
        while (angle > 180f) angle -= 360f
        while (angle < -180f) angle += 360f
        return angle
    }

    private companion object {
        const val IR_WIDTH = 1024
        const val IR_HEIGHT = 768
        const val IR_CENTER_X = IR_WIDTH / 2
        const val IR_CENTER_Y = IR_HEIGHT / 2
    }
}
