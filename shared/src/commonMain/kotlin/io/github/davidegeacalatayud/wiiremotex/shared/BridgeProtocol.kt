package io.github.davidegeacalatayud.wiiremotex.shared

/**
 * Swift-facing compatibility facade.
 *
 * The protocol implementation now lives in core:protocol so Android, iOS and any
 * future transport use exactly the same framing and reassembly rules. Keep these
 * names stable for the generated Kotlin/Native framework while callers migrate.
 */
typealias BridgeMessageType =
    io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeMessageType
typealias BridgeMessage =
    io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeMessage
typealias BridgeFragment =
    io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFragment

typealias CoreBridgeFrameReassembler =
    io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameReassembler

object BridgeControlCode {
    const val START_WII_PAIRING =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeControlCode.START_WII_PAIRING
    const val STOP_WII_PAIRING =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeControlCode.STOP_WII_PAIRING
    const val CLEAR_WII_BOND =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeControlCode.CLEAR_WII_BOND
}

object BridgeStatusCode {
    const val WII_CONNECTION =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeStatusCode.WII_CONNECTION
    const val BRIDGE_READY =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeStatusCode.BRIDGE_READY
    const val ERROR =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeStatusCode.ERROR
}

object WiiConnectionState {
    const val DISCONNECTED =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.WiiConnectionState.DISCONNECTED
    const val CONNECTING =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.WiiConnectionState.CONNECTING
    const val CONNECTED =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.WiiConnectionState.CONNECTED
}

object BridgeFrameCodec {
    const val VERSION =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.VERSION
    const val HEADER_SIZE =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.HEADER_SIZE
    const val MAX_PACKET_SIZE =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.MAX_PACKET_SIZE
    const val MAX_FRAGMENT_PAYLOAD =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.MAX_FRAGMENT_PAYLOAD
    const val MAX_MESSAGE_SIZE =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.MAX_MESSAGE_SIZE
    const val MAX_FRAGMENTS =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.MAX_FRAGMENTS

    fun encode(
        type: BridgeMessageType,
        sequence: Int,
        payload: ByteArray,
    ): List<ByteArray> =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.encode(
            type = type,
            sequence = sequence,
            payload = payload,
        )

    fun decode(packet: ByteArray): BridgeFragment? =
        io.github.davidegeacalatayud.wiiremotex.core.protocol.bridge.BridgeFrameCodec.decode(packet)
}

class BridgeFrameReassembler {
    private val delegate = CoreBridgeFrameReassembler()

    fun accept(packet: ByteArray): BridgeMessage? = delegate.accept(packet)

    fun reset() = delegate.reset()
}
