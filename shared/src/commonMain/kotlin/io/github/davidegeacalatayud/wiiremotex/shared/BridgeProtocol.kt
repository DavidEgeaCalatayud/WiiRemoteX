package io.github.davidegeacalatayud.wiiremotex.shared

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

enum class BridgeMessageType(val wireValue: Int) {
    INPUT_REPORT(0x01),
    OUTPUT_REPORT(0x02),
    STATUS(0x03),
    CONTROL(0x04);

    companion object {
        fun fromWireValue(value: Int): BridgeMessageType? =
            entries.firstOrNull { it.wireValue == value }
    }
}

object BridgeControlCode {
    const val START_WII_PAIRING = 0x01
    const val STOP_WII_PAIRING = 0x02
    const val CLEAR_WII_BOND = 0x03
}

object BridgeStatusCode {
    const val WII_CONNECTION = 0x01
    const val BRIDGE_READY = 0x02
    const val ERROR = 0x7F
}

object WiiConnectionState {
    const val DISCONNECTED = 0
    const val CONNECTING = 1
    const val CONNECTED = 2
}

data class BridgeMessage(
    val type: BridgeMessageType,
    val sequence: Int,
    val payload: ByteArray,
)

data class BridgeFragment(
    val type: BridgeMessageType,
    val sequence: Int,
    val fragmentIndex: Int,
    val fragmentCount: Int,
    val payload: ByteArray,
)

object BridgeFrameCodec {
    const val VERSION = 1
    const val HEADER_SIZE = 6

    /**
     * 20 bytes keeps every packet valid even before a larger BLE ATT MTU is negotiated.
     * A Wii 0x16 output report therefore becomes two fragments instead of depending on
     * device-specific MTU behaviour.
     */
    const val MAX_PACKET_SIZE = 20
    const val MAX_FRAGMENT_PAYLOAD = MAX_PACKET_SIZE - HEADER_SIZE

    fun encode(
        type: BridgeMessageType,
        sequence: Int,
        payload: ByteArray,
    ): List<ByteArray> {
        require(sequence in 0..0xFFFF)

        val fragmentCount =
            maxOf(1, (payload.size + MAX_FRAGMENT_PAYLOAD - 1) / MAX_FRAGMENT_PAYLOAD)
        require(fragmentCount <= 0xFF)

        return List(fragmentCount) { index ->
            val start = index * MAX_FRAGMENT_PAYLOAD
            val end = minOf(start + MAX_FRAGMENT_PAYLOAD, payload.size)
            val fragmentPayload =
                if (start < payload.size) payload.copyOfRange(start, end) else byteArrayOf()

            ByteArray(HEADER_SIZE + fragmentPayload.size).also { packet ->
                packet[0] = VERSION.toByte()
                packet[1] = type.wireValue.toByte()
                packet[2] = (sequence and 0xFF).toByte()
                packet[3] = ((sequence ushr 8) and 0xFF).toByte()
                packet[4] = index.toByte()
                packet[5] = fragmentCount.toByte()
                fragmentPayload.copyInto(packet, destinationOffset = HEADER_SIZE)
            }
        }
    }

    fun decode(packet: ByteArray): BridgeFragment? {
        if (packet.size < HEADER_SIZE) return null
        if ((packet[0].toInt() and 0xFF) != VERSION) return null

        val type = BridgeMessageType.fromWireValue(packet[1].toInt() and 0xFF) ?: return null
        val sequence =
            (packet[2].toInt() and 0xFF) or
                ((packet[3].toInt() and 0xFF) shl 8)
        val fragmentIndex = packet[4].toInt() and 0xFF
        val fragmentCount = packet[5].toInt() and 0xFF

        if (fragmentCount == 0 || fragmentIndex >= fragmentCount) return null
        if (packet.size > MAX_PACKET_SIZE) return null

        return BridgeFragment(
            type = type,
            sequence = sequence,
            fragmentIndex = fragmentIndex,
            fragmentCount = fragmentCount,
            payload = packet.copyOfRange(HEADER_SIZE, packet.size),
        )
    }
}

class BridgeFrameReassembler {
    private data class PendingMessage(
        val type: BridgeMessageType,
        val fragmentCount: Int,
        val fragments: MutableMap<Int, ByteArray> = mutableMapOf(),
    )

    private val lock = SynchronizedObject()
    private val pending = mutableMapOf<Int, PendingMessage>()

    fun accept(packet: ByteArray): BridgeMessage? = synchronized(lock) {
        val fragment = BridgeFrameCodec.decode(packet) ?: return@synchronized null

        if (fragment.fragmentCount == 1) {
            return@synchronized BridgeMessage(
                type = fragment.type,
                sequence = fragment.sequence,
                payload = fragment.payload,
            )
        }

        val message = pending.getOrPut(fragment.sequence) {
            PendingMessage(
                type = fragment.type,
                fragmentCount = fragment.fragmentCount,
            )
        }

        if (
            message.type != fragment.type ||
            message.fragmentCount != fragment.fragmentCount
        ) {
            pending.remove(fragment.sequence)
            return@synchronized null
        }

        message.fragments[fragment.fragmentIndex] = fragment.payload

        if (message.fragments.size != message.fragmentCount) {
            return@synchronized null
        }

        var totalSize = 0
        for (index in 0 until message.fragmentCount) {
            val part = message.fragments[index] ?: return@synchronized null
            totalSize += part.size
        }

        val payload = ByteArray(totalSize)
        var offset = 0
        for (index in 0 until message.fragmentCount) {
            val part = message.fragments[index] ?: return@synchronized null
            part.copyInto(payload, destinationOffset = offset)
            offset += part.size
        }

        pending.remove(fragment.sequence)

        BridgeMessage(
            type = message.type,
            sequence = fragment.sequence,
            payload = payload,
        )
    }

    fun reset() = synchronized(lock) {
        pending.clear()
    }
}
