package io.github.davidegeacalatayud.wiiremotex.core.session

import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WiimoteSessionEngineTest {
    @Test
    fun `pressing A updates state and emits report 0x30`() {
        val result = WiimoteSessionEngine().setButton(WiiButton.A, true)
        assertTrue(WiiButton.A in result.state.pressedButtons)
        val effect = assertIs<WiimoteEffect.SendReport>(result.effects.single())
        assertEquals(0x30, effect.report.reportId)
        assertContentEquals(byteArrayOf(0x00, 0x08), effect.report.payload)
    }

    @Test
    fun `host report 0x12 changes reporting mode`() {
        val result = WiimoteSessionEngine().onHostReport(0x12, byteArrayOf(0x04, 0x33))
        assertTrue(result.state.continuousReporting)
        assertEquals(0x33, result.state.reportMode)
    }
}
