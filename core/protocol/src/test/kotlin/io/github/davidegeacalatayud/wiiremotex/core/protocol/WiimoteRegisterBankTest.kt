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
    fun `Nunchuk identifier is exposed after new initialization sequence`() {
        val state = WiimoteState(
            nunchuk = NunchukState(connected = true),
        )

        assertEquals(null, bank.read(state, 0xA400FA, 6))

        bank.write(
            state = state,
            address = 0xA400F0,
            data = byteArrayOf(0x55),
        )
        bank.write(
            state = state,
            address = 0xA400FB,
            data = byteArrayOf(0x00),
        )

        val data = bank.read(
            state = state,
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
    @Test
    fun `IR register bank stores mode and completes initialization on final control write`() {
        val enabledState = WiimoteState(
            infrared = io.github.davidegeacalatayud.wiiremotex.core.model.InfraredState(
                enabled = true,
                pixelClockEnabled = true,
                logicEnabled = true,
            ),
        )

        bank.write(enabledState, 0xB00000, byteArrayOf(
            0x02, 0x00, 0x00, 0x71, 0x01, 0x00, 0xAA.toByte(), 0x00, 0x64,
        ))
        bank.write(enabledState, 0xB0001A, byteArrayOf(0x63, 0x03))

        val mode = bank.write(
            enabledState,
            0xB00033,
            byteArrayOf(0x03),
        )
        val finalControl = bank.write(
            enabledState,
            0xB00030,
            byteArrayOf(0x08),
        )

        assertEquals(
            io.github.davidegeacalatayud.wiiremotex.core.model.InfraredMode.EXTENDED,
            mode.infraredMode,
        )
        assertEquals(true, finalControl.infraredConfigured)
        assertContentEquals(
            byteArrayOf(0x03),
            bank.read(enabledState, 0xB00033, 1),
        )
    }
}
