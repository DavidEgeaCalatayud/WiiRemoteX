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
        val result = WiimoteSessionEngine().onHostReport(
            0x12,
            byteArrayOf(0x04, 0x33),
        )

        assertTrue(result.state.continuousReporting)
        assertEquals(0x33, result.state.reportMode)
    }

    @Test
    fun `status request emits report 0x20`() {
        val result = WiimoteSessionEngine().onHostReport(
            0x15,
            byteArrayOf(0x00),
        )

        val effect = assertIs<WiimoteEffect.SendReport>(result.effects.single())
        assertEquals(0x20, effect.report.reportId)
        assertEquals(6, effect.report.payload.size)
    }
    @Test
    fun `battery level is clamped to Wii byte range`() {
        val engine = WiimoteSessionEngine()

        assertEquals(0xFF, engine.setBatteryLevel(999).batteryLevel)
        assertEquals(0x00, engine.setBatteryLevel(-1).batteryLevel)
        assertEquals(0x80, engine.setBatteryLevel(0x80).batteryLevel)
    }
    @Test
    fun `motion does not emit while report mode is buttons only`() {
        val engine = WiimoteSessionEngine()

        val result = engine.setMotion(
            io.github.davidegeacalatayud.wiiremotex.core.model.MotionState(
                accelerationX = 600,
            ),
        )

        assertTrue(result.effects.isEmpty())
        assertEquals(600, result.state.motion.accelerationX)
    }

    @Test
    fun `motion emits report 0x31 after host selects mode 0x31`() {
        val engine = WiimoteSessionEngine()
        engine.onHostReport(0x12, byteArrayOf(0x00, 0x31))

        val result = engine.setMotion(
            io.github.davidegeacalatayud.wiiremotex.core.model.MotionState(
                accelerationX = 600,
            ),
        )

        val effect = assertIs<WiimoteEffect.SendReport>(result.effects.single())
        assertEquals(0x31, effect.report.reportId)
    }

    @Test
    fun `IR enable host command updates state`() {
        val engine = WiimoteSessionEngine()

        val result = engine.onHostReport(
            0x13,
            byteArrayOf(0x04),
        )

        assertTrue(result.state.infrared.enabled)
    }
    @Test
    fun `continuous reporting suppresses event driven motion and emits on scheduler tick`() {
        val engine = WiimoteSessionEngine()
        engine.onHostReport(0x12, byteArrayOf(0x04, 0x31))

        val motionResult = engine.setMotion(
            io.github.davidegeacalatayud.wiiremotex.core.model.MotionState(
                accelerationX = 620,
            ),
        )

        assertTrue(motionResult.effects.isEmpty())

        val tick = engine.nextContinuousReport()
        val effect = assertIs<WiimoteEffect.SendReport>(tick.effects.single())
        assertEquals(0x31, effect.report.reportId)
    }

    @Test
    fun `interleaved continuous reports alternate 0x3E and 0x3F`() {
        val engine = WiimoteSessionEngine()
        engine.onHostReport(0x12, byteArrayOf(0x04, 0x3E))

        val first = assertIs<WiimoteEffect.SendReport>(
            engine.nextContinuousReport().effects.single(),
        )
        val second = assertIs<WiimoteEffect.SendReport>(
            engine.nextContinuousReport().effects.single(),
        )

        assertEquals(0x3F, first.report.reportId)
        assertEquals(0x3E, second.report.reportId)
    }
    @Test
    fun `32 byte EEPROM read is split into two 0x21 reports`() {
        val engine = WiimoteSessionEngine()

        val result = engine.onHostReport(
            0x17,
            byteArrayOf(
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x20,
            ),
        )

        assertEquals(2, result.effects.size)

        val first = assertIs<WiimoteEffect.SendReport>(result.effects[0])
        val second = assertIs<WiimoteEffect.SendReport>(result.effects[1])

        assertEquals(0x21, first.report.reportId)
        assertEquals(0x21, second.report.reportId)
        assertEquals(0x00, first.report.payload[3].toInt() and 0xFF)
        assertEquals(0x00, first.report.payload[4].toInt() and 0xFF)
        assertEquals(0x00, second.report.payload[3].toInt() and 0xFF)
        assertEquals(0x10, second.report.payload[4].toInt() and 0xFF)
    }

    @Test
    fun `EEPROM write can be read back through host protocol`() {
        val engine = WiimoteSessionEngine()
        val write = ByteArray(21)
        write[0] = 0x00
        write[1] = 0x00
        write[2] = 0x01
        write[3] = 0x00
        write[4] = 0x03
        write[5] = 0x12
        write[6] = 0x34
        write[7] = 0x56

        engine.onHostReport(0x16, write)

        val read = engine.onHostReport(
            0x17,
            byteArrayOf(
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x03,
            ),
        )

        val effect = assertIs<WiimoteEffect.SendReport>(read.effects.single())
        assertContentEquals(
            byteArrayOf(0x12, 0x34, 0x56),
            effect.report.payload.copyOfRange(5, 8),
        )
    }
}
