package io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BridgeProtocolTest {
    @Test
    fun `single packet input report round trips`() {
        val payload = byteArrayOf(0x30, 0x00, 0x08)
        val packet = BridgeFrameCodec.encode(
            type = BridgeMessageType.INPUT_REPORT,
            sequence = 11,
            payload = payload,
        ).single()

        val message = BridgeFrameReassembler().accept(packet)!!

        assertEquals(BridgeMessageType.INPUT_REPORT, message.type)
        assertEquals(11, message.sequence)
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun `large Wii report survives twenty byte fragmentation`() {
        val payload = ByteArray(22) { it.toByte() }
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
        assertContentEquals(payload, message.payload)
    }

    @Test
    fun `fragments may be reassembled out of order`() {
        val payload = ByteArray(30) { (0x40 + it).toByte() }
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
    fun `payload larger than firmware ceiling is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            BridgeFrameCodec.encode(
                type = BridgeMessageType.INPUT_REPORT,
                sequence = 1,
                payload = ByteArray(BridgeFrameCodec.MAX_MESSAGE_SIZE + 1),
            )
        }
    }

    @Test
    fun `invalid protocol version is rejected`() {
        val packet = BridgeFrameCodec.encode(
            type = BridgeMessageType.STATUS,
            sequence = 1,
            payload = byteArrayOf(BridgeStatusCode.BRIDGE_READY.toByte(), 1),
        ).single()

        packet[0] = (BridgeFrameCodec.VERSION + 1).toByte()

        assertNull(BridgeFrameCodec.decode(packet))
    }

    @Test
    fun `fragment counts beyond firmware capacity are rejected`() {
        val packet = BridgeFrameCodec.encode(
            type = BridgeMessageType.STATUS,
            sequence = 1,
            payload = byteArrayOf(1, 2),
        ).single()

        packet[5] = (BridgeFrameCodec.MAX_FRAGMENTS + 1).toByte()

        assertNull(BridgeFrameCodec.decode(packet))
    }
}
