package io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BridgeProtocolTest {
    @Test
    fun `large Wii output report survives 20 byte BLE fragmentation`() {
        val payload = ByteArray(22) { index -> index.toByte() }

        val packets = BridgeFrameCodec.encode(
            type = BridgeMessageType.OUTPUT_REPORT,
            sequence = 42,
            payload = payload,
        )

        assertEquals(2, packets.size)
        packets.forEach { packet ->
            assertEquals(true, packet.size <= BridgeFrameCodec.MAX_PACKET_SIZE)
        }

        val reassembler = BridgeFrameReassembler()
        assertNull(reassembler.accept(packets[0]))
        val message = reassembler.accept(packets[1])!!

        assertEquals(BridgeMessageType.OUTPUT_REPORT, message.type)
        assertEquals(42, message.sequence)
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun `fragments may arrive out of order`() {
        val payload = ByteArray(30) { index -> (0x80 + index).toByte() }
        val packets = BridgeFrameCodec.encode(
            type = BridgeMessageType.INPUT_REPORT,
            sequence = 7,
            payload = payload,
        )

        val reassembler = BridgeFrameReassembler()
        assertNull(reassembler.accept(packets[2]))
        assertNull(reassembler.accept(packets[0]))
        val message = reassembler.accept(packets[1])!!

        assertContentEquals(payload, message.payload)
    }

    @Test
    fun `oversized bridge messages are rejected consistently with firmware`() {
        assertFailsWith<IllegalArgumentException> {
            BridgeFrameCodec.encode(
                type = BridgeMessageType.INPUT_REPORT,
                sequence = 1,
                payload = ByteArray(BridgeFrameCodec.MAX_MESSAGE_SIZE + 1),
            )
        }
    }

    @Test
    fun `fragment counts above firmware limit are rejected`() {
        val packet = BridgeFrameCodec.encode(
            type = BridgeMessageType.STATUS,
            sequence = 1,
            payload = byteArrayOf(1, 2),
        ).single()

        packet[5] = (BridgeFrameCodec.MAX_FRAGMENTS + 1).toByte()

        assertNull(BridgeFrameCodec.decode(packet))
    }

    @Test
    fun `invalid protocol version is rejected`() {
        val packet = BridgeFrameCodec.encode(
            type = BridgeMessageType.STATUS,
            sequence = 1,
            payload = byteArrayOf(1, 2),
        ).single()

        packet[0] = 99

        assertNull(BridgeFrameCodec.decode(packet))
    }
}
