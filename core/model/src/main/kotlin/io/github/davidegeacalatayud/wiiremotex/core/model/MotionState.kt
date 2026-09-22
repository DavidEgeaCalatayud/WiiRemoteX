package io.github.davidegeacalatayud.wiiremotex.core.model

data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

data class Orientation(
    val yawDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
)

data class MotionState(
    val accelerationG: Vector3 = Vector3(),
    val angularVelocityDegPerSec: Vector3 = Vector3(),
    val orientation: Orientation = Orientation(),
)

data class InfraredPoint(
    val x: Int = 1023,
    val y: Int = 1023,
    val size: Int = 0,
    val visible: Boolean = false,
)

data class InfraredState(
    val enabled: Boolean = false,
    val points: List<InfraredPoint> = List(4) { InfraredPoint() },
) {
    init {
        require(points.size == 4) { "Wiimote IR camera exposes exactly four object slots" }
    }
}
