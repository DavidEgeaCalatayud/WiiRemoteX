package io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeProtocolRobustnessTest {
    @Test
    fun `deterministic randomized payloads round trip across every bridge message type`() {
        val random = Random(0x574949)
        var sequence = 0

        repeat(1_000) {
            val size = random.nextInt(0, BridgeFrameCodec.MAX_MESSAGE_SIZE + 1)
            val payload = ByteArray(size) { random.nextInt(0, 256).toByte() }
            val type = BridgeMessageType.entries[random.nextInt(BridgeMessageType.entries.size)]
            val packets = BridgeFrameCodec.encode(type, sequence, payload).toMutableList()
            packets.shuffle(random)

            val reassembler = BridgeFrameReassembler()
            var message: BridgeMessage? = null
            packets.forEach { packet ->
                val result = reassembler.accept(packet)
                if (result != null) message = result
            }

            val complete = requireNotNull(message)
            assertEquals(type, complete.type)
            assertEquals(sequence, complete.sequence)
            assertContentEquals(payload, complete.payload)
            assertTrue(packets.all { it.size <= BridgeFrameCodec.MAX_PACKET_SIZE })

            sequence = (sequence + 1) and 0xFFFF
        }
    }

    @Test
    fun `random malformed packets never throw and only valid frames decode`() {
        val random = Random(0x42524944)
        val reassembler = BridgeFrameReassembler()

        repeat(5_000) {
            val packet = ByteArray(random.nextInt(0, 40)) {
                random.nextInt(0, 256).toByte()
            }

            runCatching { reassembler.accept(packet) }
                .getOrElse { error("Malformed packet escaped decoder: ${it.message}") }
        }
    }

    @Test
    fun `duplicate fragments are idempotent`() {
        val payload = ByteArray(40) { it.toByte() }
        val packets = BridgeFrameCodec.encode(
            type = BridgeMessageType.INPUT_REPORT,
            sequence = 77,
            payload = payload,
        )
        val reassembler = BridgeFrameReassembler()

        assertNull(reassembler.accept(packets[0]))
        assertNull(reassembler.accept(packets[0]))
        assertNull(reassembler.accept(packets[1]))
        val message = reassembler.accept(packets[2])

        assertContentEquals(payload, requireNotNull(message).payload)
    }

    @Test
    fun `conflicting metadata for one sequence invalidates the partial message`() {
        val first = BridgeFrameCodec.encode(
            BridgeMessageType.INPUT_REPORT,
            123,
            ByteArray(30) { 0x11 },
        )
        val conflict = BridgeFrameCodec.encode(
            BridgeMessageType.STATUS,
            123,
            ByteArray(30) { 0x22 },
        )
        val reassembler = BridgeFrameReassembler()

        assertNull(reassembler.accept(first[0]))
        assertNull(reassembler.accept(conflict[1]))
        assertNull(reassembler.accept(first[1]))
    }

    @Test
    fun `truncated headers and impossible fragment indexes are rejected`() {
        for (size in 0 until BridgeFrameCodec.HEADER_SIZE) {
            assertNull(BridgeFrameCodec.decode(ByteArray(size)))
        }

        val packet = BridgeFrameCodec.encode(
            BridgeMessageType.STATUS,
            1,
            byteArrayOf(0x01, 0x02),
        ).single()

        packet[4] = 1
        packet[5] = 1
        assertNull(BridgeFrameCodec.decode(packet))
    }

    @Test
    fun `oversized BLE packets are rejected even with otherwise valid headers`() {
        val packet = ByteArray(BridgeFrameCodec.MAX_PACKET_SIZE + 1)
        packet[0] = BridgeFrameCodec.VERSION.toByte()
        packet[1] = BridgeMessageType.STATUS.wireValue.toByte()
        packet[2] = 1
        packet[3] = 0
        packet[4] = 0
        packet[5] = 1

        assertNull(BridgeFrameCodec.decode(packet))
    }
}
