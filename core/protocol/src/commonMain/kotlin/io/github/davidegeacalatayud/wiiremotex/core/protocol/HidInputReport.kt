package io.github.davidegeacalatayud.wiiremotex.core.protocol

data class HidInputReport(
    val reportId: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is HidInputReport && reportId == other.reportId && payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * reportId + payload.contentHashCode()
}
