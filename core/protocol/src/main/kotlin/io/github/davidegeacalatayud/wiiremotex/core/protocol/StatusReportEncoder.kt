package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

class StatusReportEncoder(
    private val buttonsEncoder: CoreButtonsReportEncoder = CoreButtonsReportEncoder(),
) {
    fun encode(state: WiimoteState): HidInputReport {
        val buttons = buttonsEncoder.encode(state).payload

        var flags = 0
        if (state.leds.one) flags = flags or 0x10
        if (state.leds.two) flags = flags or 0x20
        if (state.leds.three) flags = flags or 0x40
        if (state.leds.four) flags = flags or 0x80
        if (state.batteryLevel <= LOW_BATTERY_THRESHOLD) flags = flags or 0x01
        val extensionVisible =
            when (val extension = state.extension) {
                ExtensionState.None -> false
                is ExtensionState.Nunchuk -> true
                is ExtensionState.MotionPlus -> extension.value.active
            }
        if (extensionVisible) flags = flags or 0x02
        if (state.infrared.enabled) flags = flags or 0x08

        return HidInputReport(
            reportId = 0x20,
            payload = byteArrayOf(
                buttons[0],
                buttons[1],
                flags.toByte(),
                0x00,
                0x00,
                state.batteryLevel.coerceIn(0, 0xFF).toByte(),
            ),
        )
    }

    private companion object {
        const val LOW_BATTERY_THRESHOLD = 0x20
    }
}
