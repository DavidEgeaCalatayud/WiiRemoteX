package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WiimoteDataReportEncoderTest {
    private val encoder = WiimoteDataReportEncoder()

    @Test
    fun `mode 0x31 emits buttons plus three accelerometer bytes`() {
        val report = encoder.encode(
            WiimoteState(
                reportMode = 0x31,
                motion = MotionState(
                    accelerationX = 512,
                    accelerationY = 516,
                    accelerationZ = 640,
                ),
            ),
        )

        assertEquals(0x31, report.reportId)
        assertEquals(5, report.payload.size)
        assertContentEquals(
            byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x81.toByte(), 0xA0.toByte()),
            report.payload,
        )
    }

    @Test
    fun `mode 0x33 emits twelve extended IR bytes`() {
        val report = encoder.encode(
            WiimoteState(
                reportMode = 0x33,
                infrared = InfraredState(
                    enabled = true,
                    points = listOf(
                        InfraredPoint(x = 512, y = 384, size = 6, visible = true),
                    ),
                ),
            ),
        )

        assertEquals(0x33, report.reportId)
        assertEquals(17, report.payload.size)
        assertEquals(0x00, report.payload[5].toInt() and 0xFF)
        assertEquals(0x80, report.payload[6].toInt() and 0xFF)
        assertEquals(0x66, report.payload[7].toInt() and 0xFF)
    }

    @Test
    fun `mode 0x32 emits six byte Nunchuk payload padded to eight bytes`() {
        val report = encoder.encode(
            WiimoteState(
                reportMode = 0x32,
                nunchuk = NunchukState(
                    connected = true,
                    stickX = 200,
                    stickY = 50,
                    cPressed = true,
                    zPressed = false,
                ),
            ),
        )

        assertEquals(0x32, report.reportId)
        assertEquals(10, report.payload.size)
        assertEquals(200, report.payload[2].toInt() and 0xFF)
        assertEquals(50, report.payload[3].toInt() and 0xFF)
    }

    @Test
    fun `MotionPlus extension uses six byte gyro layout`() {
        val report = encoder.encode(
            WiimoteState(
                reportMode = 0x32,
                motion = MotionState(
                    gyroYaw = 0x1F7F,
                    gyroRoll = 0x1F7F,
                    gyroPitch = 0x1F7F,
                ),
                motionPlus = MotionPlusState(present = true, active = true),
            ),
        )

        assertEquals(0x7F, report.payload[2].toInt() and 0xFF)
        assertEquals(0x7F, report.payload[3].toInt() and 0xFF)
        assertEquals(0x7F, report.payload[4].toInt() and 0xFF)
    }
    @Test
    fun `interleaved mode alternates full IR halves with 21-byte payloads`() {
        val state = WiimoteState(
            reportMode = 0x3E,
            infrared = InfraredState(
                enabled = true,
                points = listOf(
                    InfraredPoint(x = 320, y = 240, size = 6, visible = true),
                    InfraredPoint(x = 700, y = 240, size = 6, visible = true),
                ),
            ),
        )

        val first = encoder.encodeInterleaved(state, 0x3E)
        val second = encoder.encodeInterleaved(state, 0x3F)

        assertEquals(0x3E, first.reportId)
        assertEquals(0x3F, second.reportId)
        assertEquals(21, first.payload.size)
        assertEquals(21, second.payload.size)
    }
}
