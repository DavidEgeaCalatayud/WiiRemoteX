package io.github.davidegeacalatayud.wiiremotex.core.model

enum class ExtensionType {
    NONE,
    NUNCHUK,
    MOTION_PLUS,
    MOTION_PLUS_NUNCHUK,
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

enum class MotionPlusMode(
    val activationByte: Int,
) {
    STANDALONE(0x04),
    NUNCHUK_PASSTHROUGH(0x05),
    CLASSIC_PASSTHROUGH(0x07),
}

data class MotionPlusState(
    val yawDegPerSec: Float = 0f,
    val rollDegPerSec: Float = 0f,
    val pitchDegPerSec: Float = 0f,
    val extensionConnected: Boolean = false,
    val active: Boolean = false,
    val mode: MotionPlusMode = MotionPlusMode.STANDALONE,
    val passThroughNunchuk: NunchukState? = null,
    val reportMotionPlusNext: Boolean = true,
)

sealed interface ExtensionState {
    data object None : ExtensionState
    data class Nunchuk(val value: NunchukState = NunchukState()) : ExtensionState
    data class MotionPlus(val value: MotionPlusState = MotionPlusState()) : ExtensionState
}
