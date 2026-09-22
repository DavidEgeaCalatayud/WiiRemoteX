package io.github.davidegeacalatayud.wiiremotex.core.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HostCommandDecoderTest {
    private val decoder = HostCommandDecoder()

    @Test
    fun `0x10 decodes standalone rumble`() {
        val enabled = assertIs<HostCommand.SetRumble>(
            decoder.decode(0x10, byteArrayOf(0x01)),
        )
        assertTrue(enabled.enabled)

        val disabled = assertIs<HostCommand.SetRumble>(
            decoder.decode(0x10, byteArrayOf(0x00)),
        )
        assertEquals(false, disabled.enabled)
    }

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
    @Test
    fun `0x17 decodes register read request`() {
        val command = assertIs<HostCommand.ReadMemory>(
            decoder.decode(
                0x17,
                byteArrayOf(
                    0x04,
                    0xA4.toByte(),
                    0x00,
                    0xFA.toByte(),
                    0x00,
                    0x06,
                ),
            ),
        )

        assertTrue(command.registerSpace)
        assertEquals(0xA400FA, command.address)
        assertEquals(6, command.size)
    }

    @Test
    fun `0x16 decodes register write request`() {
        val payload = ByteArray(21)
        payload[0] = 0x04
        payload[1] = 0xA6.toByte()
        payload[2] = 0x00
        payload[3] = 0xFE.toByte()
        payload[4] = 0x01
        payload[5] = 0x04

        val command = assertIs<HostCommand.WriteMemory>(
            decoder.decode(0x16, payload),
        )

        assertEquals(0xA600FE, command.address)
        assertContentEquals(byteArrayOf(0x04), command.data)
    }
}
