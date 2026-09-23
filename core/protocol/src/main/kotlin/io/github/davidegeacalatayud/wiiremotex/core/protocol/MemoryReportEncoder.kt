package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

class MemoryReportEncoder(
    private val buttonsEncoder: CoreButtonsReportEncoder = CoreButtonsReportEncoder(),
) {
    fun encodeRead(
        state: WiimoteState,
        address: Int,
        data: ByteArray,
        error: Int = 0,
    ): HidInputReport {
        val buttons = buttonsEncoder.encode(state).payload
        val chunk = data.copyOf(16)
        val sizeNibble = ((data.size.coerceIn(1, 16) - 1) shl 4) and 0xF0
        val status = sizeNibble or (error and 0x0F)

        return HidInputReport(
            reportId = 0x21,
            payload = byteArrayOf(
                buttons[0],
                buttons[1],
                status.toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
            ) + chunk,
        )
    }

    fun encodeAck(
        state: WiimoteState,
        outputReportId: Int,
        error: Int = 0,
    ): HidInputReport {
        val buttons = buttonsEncoder.encode(state).payload

        return HidInputReport(
            reportId = 0x22,
            payload = byteArrayOf(
                buttons[0],
                buttons[1],
                outputReportId.toByte(),
                error.toByte(),
            ),
        )
    }
}
