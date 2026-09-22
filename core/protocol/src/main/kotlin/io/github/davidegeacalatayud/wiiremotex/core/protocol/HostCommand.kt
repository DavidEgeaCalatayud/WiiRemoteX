package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.PlayerLeds

sealed interface HostCommand {
    data class SetPlayerLeds(val leds: PlayerLeds, val rumbleEnabled: Boolean) : HostCommand
    data class SetReportMode(
        val reportMode: Int,
        val continuous: Boolean,
        val rumbleEnabled: Boolean,
    ) : HostCommand
    data class StatusRequest(val rumbleEnabled: Boolean) : HostCommand
    data class Unknown(val reportId: Int, val payload: ByteArray) : HostCommand
}

class HostCommandDecoder {
    fun decode(reportId: Int, payload: ByteArray): HostCommand {
        val first = payload.firstOrNull()?.toInt()?.and(0xFF) ?: 0
        return when (reportId) {
            0x11 -> HostCommand.SetPlayerLeds(
                leds = PlayerLeds.fromOutputByte(first),
                rumbleEnabled = first and 0x01 != 0,
            )
            0x12 -> HostCommand.SetReportMode(
                reportMode = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: 0x30,
                continuous = first and 0x04 != 0,
                rumbleEnabled = first and 0x01 != 0,
            )
            0x15 -> HostCommand.StatusRequest(
                rumbleEnabled = first and 0x01 != 0,
            )
            else -> HostCommand.Unknown(reportId, payload.copyOf())
        }
    }
}
