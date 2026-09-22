package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

class WiimoteDataReportEncoder(
    private val buttonsEncoder: CoreButtonsReportEncoder = CoreButtonsReportEncoder(),
) {
    fun encode(
        state: WiimoteState,
        passThroughNunchukSample: Boolean = false,
    ): HidInputReport = when (state.reportMode) {
        0x31 -> encodeButtonsAndAccelerometer(state)
        0x32 -> encodeButtonsAndExtension(
            state = state,
            reportId = 0x32,
            extensionBytes = 8,
            passThroughNunchukSample = passThroughNunchukSample,
        )
        0x33 -> encodeButtonsAccelerometerAndIr(state)
        0x34 -> encodeButtonsAndExtension(
            state = state,
            reportId = 0x34,
            extensionBytes = 19,
            passThroughNunchukSample = passThroughNunchukSample,
        )
        0x35 -> encodeButtonsAccelerometerAndExtension(state, 16, passThroughNunchukSample)
        0x36 -> encodeButtonsIrAndExtension(state, passThroughNunchukSample)
        0x37 -> encodeButtonsAccelerometerIrAndExtension(state, passThroughNunchukSample)
        0x3D -> encodeExtensionOnly(state, passThroughNunchukSample)
        0x3E, 0x3F -> encodeInterleaved(state, state.reportMode)
        else -> buttonsEncoder.encode(state)
    }

    fun encodeInterleaved(
        state: WiimoteState,
        reportId: Int,
    ): HidInputReport {
        require(reportId == 0x3E || reportId == 0x3F)

        val base = buttonsEncoder.encode(state).payload
        val z = state.motion.accelerationZ.coerceIn(0, 1023)
        var first = base[0].toInt() and 0xFF
        var second = base[1].toInt() and 0xFF

        val accelerationByte = if (reportId == 0x3E) {
            first = first or (((z shr 4) and 0x03) shl 5)
            second = second or (((z shr 6) and 0x03) shl 5)
            (state.motion.accelerationX.coerceIn(0, 1023) shr 2) and 0xFF
        } else {
            first = first or ((z and 0x03) shl 5)
            second = second or (((z shr 2) and 0x03) shl 5)
            (state.motion.accelerationY.coerceIn(0, 1023) shr 2) and 0xFF
        }

        val fullIr = encodeFullIr(state.infrared.points)
        val irOffset = if (reportId == 0x3E) 0 else 18

        return HidInputReport(
            reportId = reportId,
            payload = byteArrayOf(
                first.toByte(),
                second.toByte(),
                accelerationByte.toByte(),
            ) + fullIr.copyOfRange(irOffset, irOffset + 18),
        )
    }

    fun encodeButtonsAndAccelerometer(state: WiimoteState): HidInputReport {
        val buttons = buttonsWithAccelerometerLsbs(state)
        val motion = state.motion
        return HidInputReport(
            reportId = 0x31,
            payload = byteArrayOf(
                buttons.first.toByte(),
                buttons.second.toByte(),
                ((motion.accelerationX.coerceIn(0, 1023) shr 2) and 0xFF).toByte(),
                ((motion.accelerationY.coerceIn(0, 1023) shr 2) and 0xFF).toByte(),
                ((motion.accelerationZ.coerceIn(0, 1023) shr 2) and 0xFF).toByte(),
            ),
        )
    }

    fun encodeButtonsAccelerometerAndIr(state: WiimoteState): HidInputReport {
        val base = encodeButtonsAndAccelerometer(state).payload
        return HidInputReport(
            reportId = 0x33,
            payload = base + encodeExtendedIr(state.infrared.points),
        )
    }

    private fun encodeButtonsAndExtension(
        state: WiimoteState,
        reportId: Int,
        extensionBytes: Int,
        passThroughNunchukSample: Boolean,
    ): HidInputReport {
        val buttons = buttonsEncoder.encode(state).payload
        val extension = encodeExtensionPayload(state, passThroughNunchukSample).copyOf(extensionBytes)
        return HidInputReport(
            reportId = reportId,
            payload = buttons + extension,
        )
    }

    private fun encodeButtonsAccelerometerAndExtension(
        state: WiimoteState,
        extensionBytes: Int,
        passThroughNunchukSample: Boolean,
    ): HidInputReport {
        val base = encodeButtonsAndAccelerometer(state).payload
        val extension =
            encodeExtensionPayload(state, passThroughNunchukSample).copyOf(extensionBytes)
        return HidInputReport(
            reportId = 0x35,
            payload = base + extension,
        )
    }

    private fun encodeButtonsIrAndExtension(
        state: WiimoteState,
        passThroughNunchukSample: Boolean,
    ): HidInputReport {
        val buttons = buttonsEncoder.encode(state).payload
        val ir = encodeBasicIr(state.infrared.points)
        val extension = encodeExtensionPayload(state, passThroughNunchukSample).copyOf(9)
        return HidInputReport(
            reportId = 0x36,
            payload = buttons + ir + extension,
        )
    }

    private fun encodeExtensionOnly(
        state: WiimoteState,
        passThroughNunchukSample: Boolean,
    ): HidInputReport =
        HidInputReport(
            reportId = 0x3D,
            payload = encodeExtensionPayload(state, passThroughNunchukSample).copyOf(21),
        )

    private fun encodeButtonsAccelerometerIrAndExtension(
        state: WiimoteState,
        passThroughNunchukSample: Boolean,
    ): HidInputReport {
        val base = encodeButtonsAndAccelerometer(state).payload
        val ir = encodeBasicIr(state.infrared.points)
        val extension = encodeExtensionPayload(state, passThroughNunchukSample).copyOf(6)
        return HidInputReport(
            reportId = 0x37,
            payload = base + ir + extension,
        )
    }

    private fun buttonsWithAccelerometerLsbs(state: WiimoteState): Pair<Int, Int> {
        val base = buttonsEncoder.encode(state).payload
        var first = base[0].toInt() and 0xFF
        var second = base[1].toInt() and 0xFF

        val x = state.motion.accelerationX.coerceIn(0, 1023)
        val y = state.motion.accelerationY.coerceIn(0, 1023)
        val z = state.motion.accelerationZ.coerceIn(0, 1023)

        first = first or ((x and 0x03) shl 5)
        second = second or ((y and 0x02) shl 4)
        second = second or ((z and 0x02) shl 5)

        return first to second
    }

    private fun encodeFullIr(points: List<InfraredPoint>): ByteArray {
        val normalized = normalizePoints(points)
        return normalized.flatMap { point ->
            if (!point.visible) {
                List(9) { 0xFF.toByte() }
            } else {
                val x = point.x.coerceIn(0, 1023)
                val y = point.y.coerceIn(0, 767)
                val size = point.size.coerceIn(0, 15)

                val rawX = (x shr 3).coerceIn(0, 127)
                val rawY = (y shr 3).coerceIn(0, 95)
                val radius = (size / 2).coerceAtLeast(1)

                listOf(
                    (x and 0xFF).toByte(),
                    (y and 0xFF).toByte(),
                    ((((y shr 8) and 0x03) shl 6) or
                        (((x shr 8) and 0x03) shl 4) or
                        size).toByte(),
                    (rawX - radius).coerceIn(0, 127).toByte(),
                    (rawY - radius).coerceIn(0, 127).toByte(),
                    (rawX + radius).coerceIn(0, 127).toByte(),
                    (rawY + radius).coerceIn(0, 127).toByte(),
                    0x00,
                    0x80.toByte(),
                )
            }
        }.toByteArray()
    }

    private fun encodeExtendedIr(points: List<InfraredPoint>): ByteArray {
        val normalized = normalizePoints(points)
        return normalized.flatMap { point ->
            if (!point.visible) {
                listOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
            } else {
                val x = point.x.coerceIn(0, 1023)
                val y = point.y.coerceIn(0, 767)
                val size = point.size.coerceIn(0, 15)
                listOf(
                    (x and 0xFF).toByte(),
                    (y and 0xFF).toByte(),
                    ((((y shr 8) and 0x03) shl 6) or
                        (((x shr 8) and 0x03) shl 4) or
                        size).toByte(),
                )
            }
        }.toByteArray()
    }

    private fun encodeBasicIr(points: List<InfraredPoint>): ByteArray {
        val normalized = normalizePoints(points)
        val output = ByteArray(10) { 0xFF.toByte() }

        for (pair in 0 until 2) {
            val first = normalized[pair * 2]
            val second = normalized[pair * 2 + 1]
            val offset = pair * 5

            if (first.visible) {
                val x = first.x.coerceIn(0, 1023)
                val y = first.y.coerceIn(0, 767)
                output[offset] = (x and 0xFF).toByte()
                output[offset + 1] = (y and 0xFF).toByte()
                output[offset + 2] = (
                    (((y shr 8) and 0x03) shl 6) or
                        (((x shr 8) and 0x03) shl 4) or
                        (output[offset + 2].toInt() and 0x0F)
                    ).toByte()
            }

            if (second.visible) {
                val x = second.x.coerceIn(0, 1023)
                val y = second.y.coerceIn(0, 767)
                output[offset + 3] = (x and 0xFF).toByte()
                output[offset + 4] = (y and 0xFF).toByte()
                val high = (((y shr 8) and 0x03) shl 2) or ((x shr 8) and 0x03)
                output[offset + 2] = (
                    (output[offset + 2].toInt() and 0xF0) or high
                    ).toByte()
            }
        }

        return output
    }

    fun encodeExtensionPayload(
        state: WiimoteState,
        passThroughNunchukSample: Boolean = false,
    ): ByteArray =
        when {
            state.motionPlus.active &&
                state.motionPlus.passThroughNunchuk &&
                state.nunchuk.connected &&
                passThroughNunchukSample -> encodeMotionPlusNunchukPassThrough(state.nunchuk)

            state.motionPlus.active -> encodeMotionPlus(state)
            state.nunchuk.connected -> encodeNunchuk(state.nunchuk)
            else -> ByteArray(6)
        }

    private fun encodeNunchuk(state: NunchukState): ByteArray {
        val ax = state.accelerationX.coerceIn(0, 1023)
        val ay = state.accelerationY.coerceIn(0, 1023)
        val az = state.accelerationZ.coerceIn(0, 1023)

        var buttons = 0
        buttons = buttons or ((az and 0x03) shl 6)
        buttons = buttons or ((ay and 0x03) shl 4)
        buttons = buttons or ((ax and 0x03) shl 2)
        if (!state.cPressed) buttons = buttons or 0x02
        if (!state.zPressed) buttons = buttons or 0x01

        return byteArrayOf(
            state.stickX.coerceIn(0, 255).toByte(),
            state.stickY.coerceIn(0, 255).toByte(),
            ((ax shr 2) and 0xFF).toByte(),
            ((ay shr 2) and 0xFF).toByte(),
            ((az shr 2) and 0xFF).toByte(),
            buttons.toByte(),
        )
    }

    private fun encodeMotionPlusNunchukPassThrough(
        state: NunchukState,
    ): ByteArray {
        val ax = state.accelerationX.coerceIn(0, 1023)
        val ay = state.accelerationY.coerceIn(0, 1023)
        val az = state.accelerationZ.coerceIn(0, 1023)

        var last = 0
        last = last or (((az shr 1) and 0x03) shl 6)
        last = last or (((ay shr 1) and 0x01) shl 5)
        last = last or (((ax shr 1) and 0x01) shl 4)
        if (!state.cPressed) last = last or 0x08
        if (!state.zPressed) last = last or 0x04

        return byteArrayOf(
            state.stickX.coerceIn(0, 255).toByte(),
            state.stickY.coerceIn(0, 255).toByte(),
            ((ax shr 2) and 0xFF).toByte(),
            ((ay shr 2) and 0xFF).toByte(),
            ((((az shr 3) and 0x7F) shl 1) or 0x01).toByte(),
            last.toByte(),
        )
    }

    private fun encodeMotionPlus(state: WiimoteState): ByteArray {
        val yaw = state.motion.gyroYaw.coerceIn(0, 0x3FFF)
        val roll = state.motion.gyroRoll.coerceIn(0, 0x3FFF)
        val pitch = state.motion.gyroPitch.coerceIn(0, 0x3FFF)
        val mp = state.motionPlus

        return byteArrayOf(
            (yaw and 0xFF).toByte(),
            (roll and 0xFF).toByte(),
            (pitch and 0xFF).toByte(),
            (((yaw shr 8) and 0x3F) or
                (if (mp.yawSlow) 0x02 else 0x00) or
                (if (mp.pitchSlow) 0x01 else 0x00)).toByte(),
            (((roll shr 8) and 0x3F) or
                (if (mp.rollSlow) 0x02 else 0x00) or
                (if (mp.extensionConnected) 0x01 else 0x00)).toByte(),
            (((pitch shr 8) and 0x3F) or 0x02).toByte(),
        )
    }

    private fun normalizePoints(points: List<InfraredPoint>): List<InfraredPoint> =
        List(4) { index -> points.getOrElse(index) { InfraredPoint() } }
}
