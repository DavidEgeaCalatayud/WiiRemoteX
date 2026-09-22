package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import kotlin.math.abs
import kotlin.math.roundToInt

class ExtensionCodec {
    fun encode(extension: ExtensionState): ByteArray =
        when (extension) {
            ExtensionState.None -> ByteArray(6)
            is ExtensionState.Nunchuk -> encodeNunchuk(extension.value)
            is ExtensionState.MotionPlus -> encodeMotionPlus(extension.value)
        }

    fun encodeNunchuk(state: NunchukState): ByteArray {
        val ax = state.accelerationX.coerceIn(0, 1023)
        val ay = state.accelerationY.coerceIn(0, 1023)
        val az = state.accelerationZ.coerceIn(0, 1023)

        var sixth =
            ((az and 0x03) shl 6) or
                ((ay and 0x03) shl 4) or
                ((ax and 0x03) shl 2)

        if (!state.cPressed) sixth = sixth or 0x02
        if (!state.zPressed) sixth = sixth or 0x01

        return byteArrayOf(
            state.stickX.coerceIn(0, 255).toByte(),
            state.stickY.coerceIn(0, 255).toByte(),
            (ax shr 2).toByte(),
            (ay shr 2).toByte(),
            (az shr 2).toByte(),
            sixth.toByte(),
        )
    }

    fun encodeMotionPlus(state: MotionPlusState): ByteArray {
        val yaw = gyro(state.yawDegPerSec)
        val roll = gyro(state.rollDegPerSec)
        val pitch = gyro(state.pitchDegPerSec)

        return byteArrayOf(
            yaw.raw.toByte(),
            roll.raw.toByte(),
            pitch.raw.toByte(),
            (
                (((yaw.raw shr 8) and 0x3F) shl 2) or
                    (if (yaw.slow) 0x02 else 0x00) or
                    (if (pitch.slow) 0x01 else 0x00)
                ).toByte(),
            (
                (((roll.raw shr 8) and 0x3F) shl 2) or
                    (if (roll.slow) 0x02 else 0x00) or
                    (if (state.extensionConnected) 0x01 else 0x00)
                ).toByte(),
            (
                (((pitch.raw shr 8) and 0x3F) shl 2) or 0x02
                ).toByte(),
        )
    }

    private fun gyro(degreesPerSecond: Float): GyroValue {
        val slow = abs(degreesPerSecond) <= SLOW_RANGE_DEG_PER_SEC
        val scale = if (slow) {
            SLOW_COUNTS_PER_DEG_PER_SEC
        } else {
            FAST_COUNTS_PER_DEG_PER_SEC
        }

        val raw = (GYRO_ZERO + degreesPerSecond * scale)
            .roundToInt()
            .coerceIn(0, 0x3FFF)

        return GyroValue(raw = raw, slow = slow)
    }

    private data class GyroValue(
        val raw: Int,
        val slow: Boolean,
    )

    private companion object {
        const val GYRO_ZERO = 8063f
        const val SLOW_RANGE_DEG_PER_SEC = 440f
        const val SLOW_COUNTS_PER_DEG_PER_SEC = 13.768f
        const val FAST_COUNTS_PER_DEG_PER_SEC =
            SLOW_COUNTS_PER_DEG_PER_SEC * (440f / 2000f)
    }
}
