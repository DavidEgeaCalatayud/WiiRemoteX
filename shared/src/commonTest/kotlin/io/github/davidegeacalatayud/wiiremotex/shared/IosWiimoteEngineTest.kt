package io.github.davidegeacalatayud.wiiremotex.shared

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosWiimoteEngineTest {
    @Test
    fun `A button becomes report 0x30 payload 00 08`() {
        val engine = IosWiimoteEngine()
        val packets = engine.buttonChanged("A", true)
        val message = reassemble(packets)

        assertEquals(BridgeMessageType.INPUT_REPORT, message.type)
        assertEquals(0x30, message.payload[0].toInt() and 0xFF)
        assertContentEquals(
            byteArrayOf(0x00, 0x08),
            message.payload.copyOfRange(1, message.payload.size),
        )
    }

    @Test
    fun `host report 0x10 updates shared rumble state`() {
        val engine = IosWiimoteEngine()
        assertFalse(engine.rumbleEnabled)

        val hostPackets = BridgeFrameCodec.encode(
            type = BridgeMessageType.OUTPUT_REPORT,
            sequence = 9,
            payload = byteArrayOf(0x10, 0x01),
        )

        hostPackets.forEach(engine::acceptBridgePacket)

        assertTrue(engine.rumbleEnabled)
    }

    @Test
    fun `Wii connection status is propagated from bridge`() {
        val engine = IosWiimoteEngine()
        val packets = BridgeFrameCodec.encode(
            type = BridgeMessageType.STATUS,
            sequence = 4,
            payload = byteArrayOf(
                BridgeStatusCode.WII_CONNECTION.toByte(),
                WiiConnectionState.CONNECTED.toByte(),
            ),
        )

        packets.forEach(engine::acceptBridgePacket)

        assertEquals(WiiConnectionState.CONNECTED, engine.wiiConnectionState)
    }

    private fun reassemble(packets: List<ByteArray>): BridgeMessage {
        val reassembler = BridgeFrameReassembler()
        var result: BridgeMessage? = null
        packets.forEach { packet ->
            result = reassembler.accept(packet) ?: result
        }
        return result ?: error("Expected a complete bridge message")
    }
}
