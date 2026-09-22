package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredState

class InfraredCodec {
    fun encodeExtended(state: InfraredState): ByteArray =
        state.points.flatMap { point ->
            encodeExtendedPoint(point).toList()
        }.toByteArray()

    fun encodeBasic(state: InfraredState): ByteArray {
        val points = state.points.map(::normalized)
        val result = ByteArray(10)

        encodeBasicPair(points[0], points[1], result, 0)
        encodeBasicPair(points[2], points[3], result, 5)

        return result
    }

    private fun encodeExtendedPoint(point: InfraredPoint): ByteArray {
        val p = normalized(point)
        if (!p.visible) {
            return byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        }

        val third =
            (((p.y shr 8) and 0x03) shl 6) or
                (((p.x shr 8) and 0x03) shl 4) or
                (p.size and 0x0F)

        return byteArrayOf(
            p.x.toByte(),
            p.y.toByte(),
            third.toByte(),
        )
    }

    private fun encodeBasicPair(
        first: InfraredPoint,
        second: InfraredPoint,
        output: ByteArray,
        offset: Int,
    ) {
        val p1 = if (first.visible) first else InfraredPoint(1023, 1023, visible = false)
        val p2 = if (second.visible) second else InfraredPoint(1023, 1023, visible = false)

        output[offset] = p1.x.toByte()
        output[offset + 1] = p1.y.toByte()
        output[offset + 2] = (
            (((p1.y shr 8) and 0x03) shl 6) or
                (((p1.x shr 8) and 0x03) shl 4) or
                (((p2.y shr 8) and 0x03) shl 2) or
                ((p2.x shr 8) and 0x03)
            ).toByte()
        output[offset + 3] = p2.x.toByte()
        output[offset + 4] = p2.y.toByte()
    }

    private fun normalized(point: InfraredPoint): InfraredPoint =
        point.copy(
            x = point.x.coerceIn(0, 1023),
            y = point.y.coerceIn(0, 1023),
            size = point.size.coerceIn(0, 15),
        )
}
