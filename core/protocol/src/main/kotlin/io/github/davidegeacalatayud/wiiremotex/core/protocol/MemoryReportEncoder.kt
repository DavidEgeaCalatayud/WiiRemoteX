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
        val chunk = data.copyOf(16)
        val actualSize = data.size.coerceIn(1, 16)
        val sizeAndError = (((actualSize - 1) and 0x0F) shl 4) or (error and 0x0F)
        val buttons = buttonsEncoder.encode(state).payload

        return HidInputReport(
            reportId = 0x21,
            payload = byteArrayOf(
                buttons[0],
                buttons[1],
                sizeAndError.toByte(),
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
