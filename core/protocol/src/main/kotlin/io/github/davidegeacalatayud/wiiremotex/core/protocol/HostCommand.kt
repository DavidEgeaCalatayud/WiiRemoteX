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
        val reportId: Int,
        val enabled: Boolean,
        val acknowledge: Boolean,
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class StatusRequest(
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class WriteMemory(
        val registerSpace: Boolean,
        val address: Int,
        val data: ByteArray,
        val acknowledge: Boolean,
        val rumbleEnabled: Boolean,
    ) : HostCommand

    data class ReadMemory(
        val registerSpace: Boolean,
        val address: Int,
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
                reportId = reportId,
                enabled = first and 0x04 != 0,
                acknowledge = first and 0x02 != 0,
                rumbleEnabled = first and 0x01 != 0,
            )

            0x15 -> HostCommand.StatusRequest(
                rumbleEnabled = first and 0x01 != 0,
            )

            0x16 -> decodeWriteMemory(first, payload)

            0x17 -> decodeReadMemory(first, payload)

            else -> HostCommand.Unknown(reportId, payload.copyOf())
        }
    }

    private fun decodeWriteMemory(
        common: Int,
        payload: ByteArray,
    ): HostCommand.WriteMemory {
        val address = payload.address24()
        val size = payload.getOrNull(4)?.toInt()?.and(0xFF)?.coerceIn(0, 16) ?: 0
        val end = minOf(payload.size, 5 + size)
        val data =
            if (end > 5) payload.copyOfRange(5, end)
            else byteArrayOf()

        return HostCommand.WriteMemory(
            registerSpace = common and 0x04 != 0,
            address = address,
            data = data,
            acknowledge = common and 0x02 != 0,
            rumbleEnabled = common and 0x01 != 0,
        )
    }

    private fun decodeReadMemory(
        common: Int,
        payload: ByteArray,
    ): HostCommand.ReadMemory {
        val size =
            ((payload.getOrNull(4)?.toInt()?.and(0xFF) ?: 0) shl 8) or
                (payload.getOrNull(5)?.toInt()?.and(0xFF) ?: 0)

        return HostCommand.ReadMemory(
            registerSpace = common and 0x04 != 0,
            address = payload.address24(),
            size = size.coerceIn(0, 0xFFFF),
            rumbleEnabled = common and 0x01 != 0,
        )
    }

    private fun ByteArray.address24(): Int =
        ((getOrNull(1)?.toInt()?.and(0xFF) ?: 0) shl 16) or
            ((getOrNull(2)?.toInt()?.and(0xFF) ?: 0) shl 8) or
            (getOrNull(3)?.toInt()?.and(0xFF) ?: 0)
}
