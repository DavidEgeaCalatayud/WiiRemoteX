package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import kotlin.math.roundToInt

data class WiiAccelerometerSample(
    val x: Int,
    val y: Int,
    val z: Int,
)

class WiiAccelerometerCodec(
    private val zeroG: Int = 0x200,
    private val countsPerG: Float = 102.4f,
) {
    fun quantize(motion: MotionState): WiiAccelerometerSample =
        WiiAccelerometerSample(
            x = axis(motion.accelerationG.x),
            y = axis(motion.accelerationG.y),
            z = axis(motion.accelerationG.z),
        )

    fun applyLsbToButtons(
        buttons: ByteArray,
        sample: WiiAccelerometerSample,
    ): ByteArray {
        require(buttons.size >= 2)

        val result = buttons.copyOf()
        var first = result[0].toInt() and 0xFF
        var second = result[1].toInt() and 0xFF

        first = first and 0x9F
        second = second and 0x9F

        first = first or ((sample.x and 0x03) shl 5)
        second = second or ((sample.y and 0x02) shl 4)
        second = second or ((sample.z and 0x02) shl 5)

        result[0] = first.toByte()
        result[1] = second.toByte()
        return result
    }

    fun upperBytes(sample: WiiAccelerometerSample): ByteArray =
        byteArrayOf(
            (sample.x shr 2).toByte(),
            (sample.y shr 2).toByte(),
            (sample.z shr 2).toByte(),
        )

    private fun axis(g: Float): Int =
        (zeroG + g * countsPerG)
            .roundToInt()
            .coerceIn(0, 1023)
}
