package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.PlayerLeds
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class StatusReportEncoderTest {
    @Test
    fun `status report contains buttons leds zero padding and battery`() {
        val report = StatusReportEncoder().encode(
            WiimoteState(
                pressedButtons = setOf(WiiButton.A),
                leds = PlayerLeds(one = true, three = true),
                batteryLevel = 0xC0,
            ),
        )

        assertEquals(0x20, report.reportId)
        assertContentEquals(
            byteArrayOf(
                0x00,
                0x08,
                0x50,
                0x00,
                0x00,
                0xC0.toByte(),
            ),
            report.payload,
        )
    }
}
