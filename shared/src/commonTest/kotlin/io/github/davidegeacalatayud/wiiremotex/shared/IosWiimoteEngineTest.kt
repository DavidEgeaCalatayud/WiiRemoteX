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
    fun `bridge ready status exposes protocol compatibility`() {
        val engine = IosWiimoteEngine()
        assertFalse(engine.bridgeReady)
        assertFalse(engine.bridgeProtocolCompatible)

        val packets = BridgeFrameCodec.encode(
            type = BridgeMessageType.STATUS,
            sequence = 3,
            payload = byteArrayOf(
                BridgeStatusCode.BRIDGE_READY.toByte(),
                BridgeFrameCodec.VERSION.toByte(),
            ),
        )

        packets.forEach(engine::acceptBridgePacket)

        assertTrue(engine.bridgeReady)
        assertTrue(engine.bridgeProtocolCompatible)
        assertEquals(BridgeFrameCodec.VERSION, engine.bridgeProtocolVersion)
        assertEquals("Ready v1", engine.bridgeProtocolStatusLabel)
    }

    @Test
    fun `bridge error status is propagated and reset with transport session`() {
        val engine = IosWiimoteEngine()

        BridgeFrameCodec.encode(
            type = BridgeMessageType.STATUS,
            sequence = 4,
            payload = byteArrayOf(
                BridgeStatusCode.ERROR.toByte(),
                0x2A,
            ),
        ).forEach(engine::acceptBridgePacket)

        assertEquals(0x2A, engine.bridgeErrorCode)

        engine.resetBridgeSession()

        assertEquals(0, engine.bridgeErrorCode)
        assertFalse(engine.bridgeReady)
        assertEquals(0, engine.bridgeProtocolVersion)
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
