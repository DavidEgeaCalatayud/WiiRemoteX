package io.github.davidegeacalatayud.wiiremotex.core.model

enum class WiiButton { A, B, ONE, TWO, PLUS, MINUS, HOME, UP, DOWN, LEFT, RIGHT }

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

data class WiimoteState(
    val pressedButtons: Set<WiiButton> = emptySet(),
    val leds: PlayerLeds = PlayerLeds(),
    val rumbleEnabled: Boolean = false,
    val reportMode: Int = 0x30,
    val continuousReporting: Boolean = false,
    val batteryLevel: Int = 0xC0,
    val motion: MotionState = MotionState(),
    val infrared: InfraredState = InfraredState(),
    val extension: ExtensionState = ExtensionState.None,
)
