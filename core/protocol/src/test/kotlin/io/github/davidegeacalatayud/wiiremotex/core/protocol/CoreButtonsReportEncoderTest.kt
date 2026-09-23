package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CoreButtonsReportEncoderTest {
    private val encoder = CoreButtonsReportEncoder()

    @Test
    fun `A uses the expected second-byte bit`() {
        val report = encoder.encode(WiimoteState(pressedButtons = setOf(WiiButton.A)))
        assertEquals(0x30, report.reportId)
        assertContentEquals(byteArrayOf(0x00, 0x08), report.payload)
    }

    @Test
    fun `multiple buttons combine into the two-byte mask`() {
        val report = encoder.encode(
            WiimoteState(
                pressedButtons = setOf(
                    WiiButton.LEFT, WiiButton.UP, WiiButton.PLUS,
                    WiiButton.B, WiiButton.ONE, WiiButton.HOME,
                ),
            ),
        )
        assertContentEquals(byteArrayOf(0x19, 0x86.toByte()), report.payload)
    }
}
