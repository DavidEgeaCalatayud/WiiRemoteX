package io.github.davidegeacalatayud.wiiremotex

import android.os.Build
import android.os.SystemClock
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

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

class HardwareTraceRecorder(
    private val maxEvents: Int = 8_000,
) {
    private val events = ArrayDeque<HardwareTraceEvent>()

    @Synchronized
    fun record(
        direction: String,
        event: String,
        connectionState: String,
        state: WiimoteState,
        reportId: Int? = null,
        payload: ByteArray = byteArrayOf(),
        transport: String = "android-bluetooth-hid-device",
    ) {
        events.addLast(
            HardwareTraceEvent(
                timestampNs = System.currentTimeMillis() * 1_000_000L,
                elapsedRealtimeNs = SystemClock.elapsedRealtimeNanos(),
                direction = direction,
                transport = transport,
                event = event,
                reportId = reportId,
                payload = payload.copyOf(),
                connectionState = connectionState,
                reportMode = state.reportMode,
                extensionState =
                    "nunchuk.connected=${state.nunchuk.connected};" +
                        "nunchuk.initialized=${state.nunchuk.initialized}",
                irState =
                    "enabled=${state.infrared.enabled};configured=${state.infrared.configured};" +
                        "mode=${state.infrared.mode}",
                motionPlusState =
                    "present=${state.motionPlus.present};initialized=${state.motionPlus.initialized};" +
                        "active=${state.motionPlus.active};mode=${state.motionPlus.activationMode};" +
                        "passthrough=${state.motionPlus.passThroughNunchuk}",
            ),
        )
        while (events.size > maxEvents) events.removeFirst()
    }

    @Synchronized
    fun clear() = events.clear()

    @Synchronized
    fun exportJson(): String = buildString {
        append("{\n")
        append("  \"schema\": \"wiiremotex-hardware-trace-v1\",\n")
        append("  \"device\": {")
        append("\"manufacturer\":\"${escape(Build.MANUFACTURER)}\",")
        append("\"model\":\"${escape(Build.MODEL)}\",")
        append("\"android_release\":\"${escape(Build.VERSION.RELEASE)}\",")
        append("\"api\":${Build.VERSION.SDK_INT}")
        append("},\n")
        append("  \"events\": [\n")
        events.forEachIndexed { index, item ->
            append("    {")
            append("\"timestamp_ns\":${item.timestampNs},")
            append("\"elapsed_realtime_ns\":${item.elapsedRealtimeNs},")
            append("\"direction\":\"${escape(item.direction)}\",")
            append("\"transport\":\"${escape(item.transport)}\",")
            append("\"event\":\"${escape(item.event)}\",")
            append("\"report_id\":")
            if (item.reportId == null) append("null") else append(item.reportId)
            append(",\"report_id_hex\":")
            if (item.reportId == null) {
                append("null")
            } else {
                append("\"0x${item.reportId.toString(16).uppercase().padStart(2, '0')}\"")
            }
            append(",\"payload_hex\":\"${item.payload.toHex()}\",")
            append("\"connection_state\":\"${escape(item.connectionState)}\",")
            append("\"report_mode\":${item.reportMode},")
            append("\"report_mode_hex\":\"0x${item.reportMode.toString(16).uppercase().padStart(2, '0')}\",")
            append("\"extension_state\":\"${escape(item.extensionState)}\",")
            append("\"ir_state\":\"${escape(item.irState)}\",")
            append("\"motion_plus_state\":\"${escape(item.motionPlusState)}\"")
            append("}")
            if (index != events.lastIndex) append(",")
            append("\n")
        }
        append("  ]\n")
        append("}\n")
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { byte ->
            (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
        }

    private fun escape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
