package io.github.davidegeacalatayud.wiiremotex.core.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WiimoteEepromTest {
    @Test
    fun `accelerometer calibration block exists at 0x16`() {
        val eeprom = WiimoteEeprom()

        val data = eeprom.read(
            address = 0x0016,
            size = 10,
        )

        assertNotNull(data)
        assertEquals(10, data.size)
        assertContentEquals(
            byteArrayOf(
                0x82.toByte(),
                0x82.toByte(),
                0x82.toByte(),
                0x15,
                0x9C.toByte(),
                0x9C.toByte(),
                0x9E.toByte(),
                0x38,
                0x40,
                0x3E,
            ),
            data,
        )
    }

    @Test
    fun `user EEPROM writes are visible to later reads`() {
        val eeprom = WiimoteEeprom()

        assertTrue(
            eeprom.write(
                address = 0x0100,
                data = byteArrayOf(0x12, 0x34, 0x56),
            ),
        )

        assertContentEquals(
            byteArrayOf(0x12, 0x34, 0x56),
            eeprom.read(0x0100, 3),
        )
    }

    @Test
    fun `out of range access fails`() {
        val eeprom = WiimoteEeprom()

        assertEquals(null, eeprom.read(0x1700, 1))
        assertEquals(false, eeprom.write(0x16FF, byteArrayOf(0x01, 0x02)))
    }
}
