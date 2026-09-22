package io.github.davidegeacalatayud.wiiremotex.core.model

enum class ExtensionType {
    NONE,
    NUNCHUK,
    MOTION_PLUS,
}

data class NunchukState(
    val stickX: Int = 128,
    val stickY: Int = 128,
    val accelerationX: Int = 512,
    val accelerationY: Int = 512,
    val accelerationZ: Int = 512,
    val cPressed: Boolean = false,
    val zPressed: Boolean = false,
)

data class MotionPlusState(
    val yawDegPerSec: Float = 0f,
    val rollDegPerSec: Float = 0f,
    val pitchDegPerSec: Float = 0f,
    val extensionConnected: Boolean = false,
    val active: Boolean = false,
)

sealed interface ExtensionState {
    data object None : ExtensionState
    data class Nunchuk(val value: NunchukState = NunchukState()) : ExtensionState
    data class MotionPlus(val value: MotionPlusState = MotionPlusState()) : ExtensionState
}
