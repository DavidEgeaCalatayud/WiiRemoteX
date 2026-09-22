package io.github.davidegeacalatayud.wiiremotex.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HostCommandDecoderTest {
    private val decoder = HostCommandDecoder()

    @Test
    fun `0x11 decodes LEDs and rumble`() {
        val command = assertIs<HostCommand.SetPlayerLeds>(
            decoder.decode(0x11, byteArrayOf(0x31)),
        )
        assertTrue(command.rumbleEnabled)
        assertTrue(command.leds.one)
        assertTrue(command.leds.two)
        assertEquals(false, command.leds.three)
        assertEquals(false, command.leds.four)
    }

    @Test
    fun `0x12 decodes continuous report mode`() {
        val command = assertIs<HostCommand.SetReportMode>(
            decoder.decode(0x12, byteArrayOf(0x04, 0x33)),
        )
        assertTrue(command.continuous)
        assertEquals(0x33, command.reportMode)
    }
}
