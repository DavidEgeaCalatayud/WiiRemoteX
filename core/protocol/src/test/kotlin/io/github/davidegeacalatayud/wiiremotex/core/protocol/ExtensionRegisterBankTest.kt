package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExtensionRegisterBankTest {
    private val bank = ExtensionRegisterBank()

    @Test
    fun `Nunchuk exposes standard six byte identity at A400FA`() {
        val result = bank.read(
            extension = ExtensionState.Nunchuk(),
            address = 0xA400FA,
            size = 6,
        )

        assertEquals(0, result.error)
        assertContentEquals(
            ExtensionRegisterBank.NUNCHUK_ID,
            result.data,
        )
    }

    @Test
    fun `MotionPlus exposes inactive identity at A600FA`() {
        val result = bank.read(
            extension = ExtensionState.MotionPlus(),
            address = 0xA600FA,
            size = 6,
        )

        assertEquals(0, result.error)
        assertContentEquals(
            ExtensionRegisterBank.MOTION_PLUS_INACTIVE_ID,
            result.data,
        )
    }

    @Test
    fun `write 04 to A600FE activates MotionPlus`() {
        val result = bank.write(
            extension = ExtensionState.MotionPlus(
                MotionPlusState(active = false),
            ),
            address = 0xA600FE,
            data = byteArrayOf(0x04),
        )

        val extension = assertIs<ExtensionState.MotionPlus>(result.extension)
        assertTrue(extension.value.active)
        assertEquals(0, result.error)
    }

    @Test
    fun `write 55 to A400F0 deactivates active MotionPlus`() {
        val result = bank.write(
            extension = ExtensionState.MotionPlus(
                MotionPlusState(active = true),
            ),
            address = 0xA400F0,
            data = byteArrayOf(0x55),
        )

        val extension = assertIs<ExtensionState.MotionPlus>(result.extension)
        assertFalse(extension.value.active)
    }

    @Test
    fun `probing extension space with no extension returns error seven`() {
        val result = bank.read(
            extension = ExtensionState.None,
            address = 0xA400FE,
            size = 2,
        )

        assertEquals(ExtensionRegisterBank.ERROR_NO_EXTENSION, result.error)
    }
}
