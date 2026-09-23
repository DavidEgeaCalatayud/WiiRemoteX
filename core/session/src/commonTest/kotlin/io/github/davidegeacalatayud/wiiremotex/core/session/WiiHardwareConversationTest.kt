package io.github.davidegeacalatayud.wiiremotex.core.session

import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WiiHardwareConversationTest {
    @Test
    fun `reference Wii conversation reaches continuous core button reporting`() {
        val engine = WiimoteSessionEngine()

        val status = engine.onHostReport(0x15, byteArrayOf(0x00))
        val statusReport = status.singleReport()
        assertEquals(0x20, statusReport.report.reportId)

        val mode = engine.onHostReport(0x12, byteArrayOf(0x04, 0x30))
        assertTrue(mode.state.continuousReporting)
        assertEquals(0x30, mode.state.reportMode)

        val press = engine.setButton(WiiButton.A, true)
        assertTrue(press.effects.isEmpty(), "continuous mode must defer transmission to scheduler")

        val down = engine.nextContinuousReport().singleReport().report
        assertEquals(0x30, down.reportId)
        assertContentEquals(byteArrayOf(0x00, 0x08), down.payload)

        engine.setButton(WiiButton.A, false)
        val up = engine.nextContinuousReport().singleReport().report
        assertEquals(0x30, up.reportId)
        assertContentEquals(byteArrayOf(0x00, 0x00), up.payload)
    }

    @Test
    fun `reference Wii extension conversation pauses initializes and resumes reporting`() {
        val engine = WiimoteSessionEngine()

        engine.onHostReport(0x15, byteArrayOf(0x00))
        engine.onHostReport(0x12, byteArrayOf(0x04, 0x30))

        val attached = engine.setNunchuk(NunchukState(connected = true))
        val attachmentStatus = attached.singleReport().report
        assertEquals(0x20, attachmentStatus.reportId)
        assertEquals(false, attached.state.dataReportingEnabled)

        val initialize = ByteArray(21).apply {
            this[0] = 0x04
            this[1] = 0xA4.toByte()
            this[2] = 0x00
            this[3] = 0xF0.toByte()
            this[4] = 0x01
            this[5] = 0x55
        }
        val initialized = engine.onHostReport(0x16, initialize)
        assertTrue(initialized.state.nunchuk.initialized)
        assertEquals(0x22, initialized.singleReport().report.reportId)

        val plaintext = ByteArray(21).apply {
            this[0] = 0x04
            this[1] = 0xA4.toByte()
            this[2] = 0x00
            this[3] = 0xFB.toByte()
            this[4] = 0x01
            this[5] = 0x00
        }
        val encryptionDisabled = engine.onHostReport(0x16, plaintext)
        assertTrue(encryptionDisabled.state.nunchuk.encryptionDisabled)
        assertEquals(0x22, encryptionDisabled.singleReport().report.reportId)

        val resumed = engine.onHostReport(0x12, byteArrayOf(0x04, 0x32))
        assertTrue(resumed.state.dataReportingEnabled)
        assertTrue(resumed.state.continuousReporting)
        assertEquals(0x32, resumed.state.reportMode)

        val tick = engine.nextContinuousReport().singleReport().report
        assertEquals(0x32, tick.reportId)
    }

    @Test
    fun `reference Wii memory conversation writes acknowledges and reads back`() {
        val engine = WiimoteSessionEngine()
        val write = ByteArray(21).apply {
            this[0] = 0x00
            this[1] = 0x00
            this[2] = 0x01
            this[3] = 0x20
            this[4] = 0x03
            this[5] = 0x11
            this[6] = 0x22
            this[7] = 0x33
        }

        val ack = engine.onHostReport(0x16, write).singleReport().report
        assertEquals(0x22, ack.reportId)

        val read = engine.onHostReport(
            0x17,
            byteArrayOf(0x00, 0x00, 0x01, 0x20, 0x00, 0x03),
        ).singleReport().report

        assertEquals(0x21, read.reportId)
        assertContentEquals(
            byteArrayOf(0x11, 0x22, 0x33),
            read.payload.copyOfRange(5, 8),
        )
    }

    private fun SessionResult.singleReport(): WiimoteEffect.SendReport =
        assertIs(effects.single())
}
