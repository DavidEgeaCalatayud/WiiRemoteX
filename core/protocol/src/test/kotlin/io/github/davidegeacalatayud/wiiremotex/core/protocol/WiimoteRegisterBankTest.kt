package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WiimoteRegisterBankTest {
    private val bank = WiimoteRegisterBank()

    @Test
    fun `Nunchuk identifier is exposed at A400FA`() {
        val data = bank.read(
            state = WiimoteState(
                nunchuk = NunchukState(connected = true),
            ),
            address = 0xA400FA,
            size = 6,
        )

        assertNotNull(data)
        assertContentEquals(
            byteArrayOf(
                0x00,
                0x00,
                0xA4.toByte(),
                0x20,
                0x00,
                0x00,
            ),
            data,
        )
    }

    @Test
    fun `inactive MotionPlus identifier is exposed at A600FA`() {
        val data = bank.read(
            state = WiimoteState(
                motionPlus = MotionPlusState(present = true),
            ),
            address = 0xA600FA,
            size = 6,
        )

        assertNotNull(data)
        assertContentEquals(
            byteArrayOf(
                0x00,
                0x00,
                0xA6.toByte(),
                0x20,
                0x00,
                0x05,
            ),
            data,
        )
    }

    @Test
    fun `write 04 to A600FE activates MotionPlus`() {
        val result = bank.write(
            state = WiimoteState(
                motionPlus = MotionPlusState(present = true),
            ),
            address = 0xA600FE,
            data = byteArrayOf(0x04),
        )

        assertTrue(result.success)
        assertTrue(result.activateMotionPlus)
        assertEquals(0x04, result.motionPlusMode)
    }
    @Test
    fun `MotionPlus calibration block is readable when present`() {
        val data = bank.read(
            state = WiimoteState(
                motionPlus = MotionPlusState(present = true),
            ),
            address = 0xA60020,
            size = 16,
        )

        assertNotNull(data)
        assertEquals(16, data.size)
    }
    @Test
    fun `MotionPlus activation fails when not present`() {
        val result = bank.write(
            state = WiimoteState(),
            address = 0xA600FE,
            data = byteArrayOf(0x04),
        )

        assertEquals(false, result.success)
        assertEquals(false, result.activateMotionPlus)
    }
}
