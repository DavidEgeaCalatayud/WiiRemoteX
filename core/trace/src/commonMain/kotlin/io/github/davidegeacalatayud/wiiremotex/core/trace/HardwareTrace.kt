package io.github.davidegeacalatayud.wiiremotex.core.trace

import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

const val HARDWARE_TRACE_SCHEMA = "wiiremotex-hardware-trace-v1"

data class HardwareTraceEvent(
    val timestampNs: Long,
    val elapsedRealtimeNs: Long,
    val direction: String,
    val transport: String,
    val event: String,
    val reportId: Int?,
    val payload: ByteArray,
    val connectionState: String,
    val reportMode: Int,
    val extensionState: String,
    val irState: String,
    val motionPlusState: String,
)

fun WiimoteState.traceExtensionState(): String =
    "nunchuk.connected=${nunchuk.connected};" +
        "nunchuk.initialized=${nunchuk.initialized}"

fun WiimoteState.traceInfraredState(): String =
    "enabled=${infrared.enabled};configured=${infrared.configured};" +
        "mode=${infrared.mode}"

fun WiimoteState.traceMotionPlusState(): String =
    "present=${motionPlus.present};initialized=${motionPlus.initialized};" +
        "active=${motionPlus.active};mode=${motionPlus.activationMode};" +
        "passthrough=${motionPlus.passThroughNunchuk}"
