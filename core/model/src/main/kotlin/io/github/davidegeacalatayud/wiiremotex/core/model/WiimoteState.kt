package io.github.davidegeacalatayud.wiiremotex.core.model

enum class WiiButton {
    A, B, ONE, TWO, PLUS, MINUS, HOME, UP, DOWN, LEFT, RIGHT
}

data class PlayerLeds(
    val one: Boolean = false,
    val two: Boolean = false,
    val three: Boolean = false,
    val four: Boolean = false,
) {
    companion object {
        fun fromOutputByte(value: Int) = PlayerLeds(
            one = value and 0x10 != 0,
            two = value and 0x20 != 0,
            three = value and 0x40 != 0,
            four = value and 0x80 != 0,
        )
    }
}

data class MotionState(
    val accelerationX: Int = 521,
    val accelerationY: Int = 521,
    val accelerationZ: Int = 632,
    val gyroYaw: Int = 0x1F7F,
    val gyroRoll: Int = 0x1F7F,
    val gyroPitch: Int = 0x1F7F,
)

data class InfraredPoint(
    val x: Int = 0,
    val y: Int = 0,
    val size: Int = 5,
    val visible: Boolean = false,
)

enum class InfraredMode(val registerValue: Int) {
    OFF(0),
    BASIC(1),
    EXTENDED(3),
    FULL(5);

    companion object {
        fun fromRegisterValue(value: Int): InfraredMode =
            entries.firstOrNull { it.registerValue == value } ?: OFF
    }
}

data class InfraredState(
    val enabled: Boolean = false,
    val pixelClockEnabled: Boolean = false,
    val logicEnabled: Boolean = false,
    val configured: Boolean = false,
    val mode: InfraredMode = InfraredMode.OFF,
    val points: List<InfraredPoint> = List(4) { InfraredPoint() },
)

data class NunchukState(
    val connected: Boolean = false,
    val initialized: Boolean = false,
    val encryptionDisabled: Boolean = false,
    val stickX: Int = 128,
    val stickY: Int = 128,
    val accelerationX: Int = 521,
    val accelerationY: Int = 521,
    val accelerationZ: Int = 632,
    val cPressed: Boolean = false,
    val zPressed: Boolean = false,
)

data class MotionPlusState(
    val present: Boolean = false,
    val initialized: Boolean = false,
    val active: Boolean = false,
    val activationMode: Int = 0x04,
    val passThroughNunchuk: Boolean = false,
    val yawSlow: Boolean = true,
    val rollSlow: Boolean = true,
    val pitchSlow: Boolean = true,
    val extensionConnected: Boolean = false,
)

data class WiimoteState(
    val pressedButtons: Set<WiiButton> = emptySet(),
    val leds: PlayerLeds = PlayerLeds(),
    val rumbleEnabled: Boolean = false,
    val reportMode: Int = 0x30,
    val dataReportingEnabled: Boolean = true,
    val continuousReporting: Boolean = false,
    val batteryLevel: Int = 0xC0,
    val motion: MotionState = MotionState(),
    val infrared: InfraredState = InfraredState(),
    val nunchuk: NunchukState = NunchukState(),
    val motionPlus: MotionPlusState = MotionPlusState(),
)
