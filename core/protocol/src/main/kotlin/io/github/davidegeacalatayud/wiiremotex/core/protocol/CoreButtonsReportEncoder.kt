package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

class CoreButtonsReportEncoder {
    fun encode(state: WiimoteState): HidInputReport {
        var first = 0
        var second = 0

        state.pressedButtons.forEach { button ->
            when (button) {
                WiiButton.LEFT -> first = first or 0x01
                WiiButton.RIGHT -> first = first or 0x02
                WiiButton.DOWN -> first = first or 0x04
                WiiButton.UP -> first = first or 0x08
                WiiButton.PLUS -> first = first or 0x10
                WiiButton.TWO -> second = second or 0x01
                WiiButton.ONE -> second = second or 0x02
                WiiButton.B -> second = second or 0x04
                WiiButton.A -> second = second or 0x08
                WiiButton.MINUS -> second = second or 0x10
                WiiButton.HOME -> second = second or 0x80
            }
        }

        return HidInputReport(
            reportId = 0x30,
            payload = byteArrayOf(first.toByte(), second.toByte()),
        )
    }
}
