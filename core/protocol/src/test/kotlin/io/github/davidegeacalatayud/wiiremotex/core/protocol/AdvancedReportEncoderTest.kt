package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.Vector3
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AdvancedReportEncoderTest {
    private val encoder = WiimoteDataReportEncoder()

    @Test
    fun `0x31 contains buttons and three accelerometer bytes`() {
        val report = encoder.encode(
            WiimoteState(
                reportMode = 0x31,
                pressedButtons = setOf(WiiButton.A),
                motion = MotionState(
                    accelerationG = Vector3(
                        x = 0f,
                        y = 0f,
                        z = 1f,
                    ),
                ),
            ),
        )

        assertEquals(0x31, report.reportId)
        assertEquals(5, report.payload.size)
        assertEquals(0x08, report.payload[1].toInt() and 0x1F)
        assertEquals(0x80, report.payload[2].toInt() and 0xFF)
        assertEquals(0x80, report.payload[3].toInt() and 0xFF)
    }

    @Test
    fun `0x33 packs four extended IR object slots`() {
        val report = encoder.encode(
            WiimoteState(
                reportMode = 0x33,
                infrared = InfraredState(
                    enabled = true,
                    points = listOf(
                        InfraredPoint(x = 100, y = 200, size = 3, visible = true),
                        InfraredPoint(x = 900, y = 700, size = 6, visible = true),
                        InfraredPoint(),
                        InfraredPoint(),
                    ),
                ),
            ),
        )

        assertEquals(0x33, report.reportId)
        assertEquals(17, report.payload.size)
        assertContentEquals(
            byteArrayOf(100, 200.toByte()),
            report.payload.copyOfRange(5, 7),
        )
        assertContentEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            report.payload.copyOfRange(11, 14),
        )
    }

    @Test
    fun `Nunchuk encodes stick accelerometer and active low buttons`() {
        val payload = ExtensionCodec().encodeNunchuk(
            NunchukState(
                stickX = 35,
                stickY = 220,
                accelerationX = 512,
                accelerationY = 516,
                accelerationZ = 520,
                cPressed = true,
                zPressed = false,
            ),
        )

        assertEquals(6, payload.size)
        assertEquals(35, payload[0].toInt() and 0xFF)
        assertEquals(220, payload[1].toInt() and 0xFF)
        assertEquals(0, payload[5].toInt() and 0x02)
        assertEquals(1, payload[5].toInt() and 0x01)
    }

    @Test
    fun `MotionPlus still state stays close to documented zero and marks motionplus frame`() {
        val payload = ExtensionCodec().encodeMotionPlus(
            MotionPlusState(),
        )

        assertEquals(6, payload.size)

        val yaw =
            (payload[0].toInt() and 0xFF) or
                (((payload[3].toInt() and 0xFC) shr 2) shl 8)

        assertEquals(8063, yaw)
        assertEquals(0x02, payload[5].toInt() and 0x02)
    }

    @Test
    fun `0x37 is exactly 21 payload bytes`() {
        val report = encoder.encode(
            WiimoteState(
                reportMode = 0x37,
                extension = ExtensionState.Nunchuk(),
                infrared = InfraredState(enabled = true),
            ),
        )

        assertEquals(0x37, report.reportId)
        assertEquals(21, report.payload.size)
    }
}
