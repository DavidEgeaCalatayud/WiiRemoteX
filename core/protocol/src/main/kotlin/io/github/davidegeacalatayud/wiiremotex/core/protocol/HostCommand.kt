package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.PlayerLeds

sealed interface HostCommand {
    data class SetPlayerLeds(
        val leds: PlayerLeds,
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class SetReportMode(
        val reportMode: Int,
        val continuous: Boolean,
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class SetIrEnabled(
        val enabled: Boolean,
        val acknowledge: Boolean,
        val outputReportId: Int,
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class StatusRequest(
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class WriteMemory(
        val address: Int,
        val registerSpace: Boolean,
        val data: ByteArray,
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class ReadMemory(
        val address: Int,
        val registerSpace: Boolean,
        val size: Int,
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class Unknown(
        val reportId: Int,
        val payload: ByteArray,
    ) : HostCommand
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

            0x13, 0x1A -> HostCommand.SetIrEnabled(
                enabled = first and 0x04 != 0,
                acknowledge = first and 0x02 != 0,
                outputReportId = reportId,
                rumbleEnabled = first and 0x01 != 0,
            )

            0x15 -> HostCommand.StatusRequest(
                rumbleEnabled = first and 0x01 != 0,
            )

            0x16 -> decodeWriteMemory(payload)

            0x17 -> decodeReadMemory(payload)

            else -> HostCommand.Unknown(reportId, payload.copyOf())
        }
    }

    private fun decodeWriteMemory(payload: ByteArray): HostCommand {
        val flags = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0
        val address = readAddress(payload, 1)
        val size = payload.getOrNull(4)?.toInt()?.and(0xFF)?.coerceIn(0, 16) ?: 0
        val data = payload
            .drop(5)
            .take(size)
            .map { it }
            .toByteArray()

        return HostCommand.WriteMemory(
            address = address,
            registerSpace = flags and 0x0C != 0,
            data = data,
            rumbleEnabled = flags and 0x01 != 0,
        )
    }

    private fun decodeReadMemory(payload: ByteArray): HostCommand {
        val flags = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: 0
        val address = readAddress(payload, 1)
        val size = (
            ((payload.getOrNull(4)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(5)?.toInt()?.and(0xFF) ?: 0)
            ).coerceIn(0, 0xFFFF)

        return HostCommand.ReadMemory(
            address = address,
            registerSpace = flags and 0x0C != 0,
            size = size,
            rumbleEnabled = flags and 0x01 != 0,
        )
    }

    private fun readAddress(payload: ByteArray, offset: Int): Int =
        ((payload.getOrNull(offset)?.toInt()?.and(0xFF) ?: 0) shl 16) or
            ((payload.getOrNull(offset + 1)?.toInt()?.and(0xFF) ?: 0) shl 8) or
            (payload.getOrNull(offset + 2)?.toInt()?.and(0xFF) ?: 0)
}
