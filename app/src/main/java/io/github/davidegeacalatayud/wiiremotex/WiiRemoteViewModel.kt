package io.github.davidegeacalatayud.wiiremotex

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import androidx.lifecycle.AndroidViewModel
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import io.github.davidegeacalatayud.wiiremotex.core.session.SessionResult
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteEffect
import io.github.davidegeacalatayud.wiiremotex.core.session.WiimoteSessionEngine
import io.github.davidegeacalatayud.wiiremotex.platform.bluetooth.AndroidHidTransport
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class HidStage {
    IDLE,
    STARTING,
    REGISTERED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class DiagnosticEntry(
    val timestamp: String,
    val direction: String,
    val message: String,
)

data class WiiRemoteUiState(
    val hidStage: HidStage = HidStage.IDLE,
    val wiimote: WiimoteState = WiimoteState(),
    val diagnostics: List<DiagnosticEntry> = emptyList(),
    val lastError: String? = null,
)

class WiiRemoteViewModel(
    application: Application,
) : AndroidViewModel(application), AndroidHidTransport.Listener {

    private val session = WiimoteSessionEngine()

    private val transport = AndroidHidTransport(
        context = application,
        listener = this,
    )

    private val _uiState = MutableStateFlow(WiiRemoteUiState())
    val uiState: StateFlow<WiiRemoteUiState> = _uiState.asStateFlow()

    fun startHid() {
        log("SYS", "Starting Android HID Device profile")
        _uiState.update {
            it.copy(
                hidStage = HidStage.STARTING,
                lastError = null,
            )
        }

        if (!transport.start()) {
            fail("Android did not accept the HID Device profile request")
        }
    }

    fun onButtonChanged(button: WiiButton, pressed: Boolean) {
        apply(session.setButton(button, pressed))
    }

    override fun onRegistrationChanged(registered: Boolean) {
        log(
            "SYS",
            if (registered) "HID application registered" else "HID application unregistered",
        )

        _uiState.update {
            it.copy(
                hidStage = if (registered) HidStage.REGISTERED else HidStage.IDLE,
                wiimote = session.state,
            )
        }
    }

    override fun onConnectionStateChanged(
        device: BluetoothDevice?,
        state: Int,
    ) {
        val stage = when (state) {
            BluetoothProfile.STATE_CONNECTING -> HidStage.CONNECTING
            BluetoothProfile.STATE_CONNECTED -> HidStage.CONNECTED
            BluetoothProfile.STATE_DISCONNECTING -> HidStage.REGISTERED
            BluetoothProfile.STATE_DISCONNECTED -> HidStage.REGISTERED
            else -> _uiState.value.hidStage
        }

        log("SYS", "Bluetooth connection state: ${connectionStateName(state)}")
        _uiState.update { it.copy(hidStage = stage) }
    }

    override fun onHostReport(
        reportId: Int,
        payload: ByteArray,
    ) {
        log("RX", "0x${reportId.hex2()} ${payload.toHex()}")
        apply(session.onHostReport(reportId, payload))
    }

    override fun onError(
        message: String,
        cause: Throwable?,
    ) {
        val detail = cause?.message ?: cause?.let { it::class.simpleName }
        fail(if (detail == null) message else "$message: $detail")
    }

    private fun apply(result: SessionResult) {
        _uiState.update { it.copy(wiimote = result.state) }

        result.effects.forEach { effect ->
            when (effect) {
                is WiimoteEffect.SendReport -> {
                    val report = effect.report
                    val sent = transport.send(report)
                    val suffix = if (sent) "✓" else "not sent (no HID host)"
                    log("TX", "0x${report.reportId.hex2()} ${report.payload.toHex()} $suffix")
                }
            }
        }
    }

    private fun fail(message: String) {
        log("ERR", message)
        _uiState.update {
            it.copy(
                hidStage = HidStage.ERROR,
                lastError = message,
            )
        }
    }

    private fun log(direction: String, message: String) {
        val entry = DiagnosticEntry(
            timestamp = LocalTime.now().format(TIME_FORMAT),
            direction = direction,
            message = message,
        )

        _uiState.update {
            it.copy(
                diagnostics = (it.diagnostics + entry).takeLast(MAX_LOG_LINES),
            )
        }
    }

    override fun onCleared() {
        transport.stop()
    }

    private fun connectionStateName(state: Int): String = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }

    private fun Int.hex2(): String =
        toString(16).uppercase().padStart(2, '0')

    private fun ByteArray.toHex(): String =
        joinToString(" ") { byte ->
            (byte.toInt() and 0xFF).hex2()
        }

    private companion object {
        const val MAX_LOG_LINES = 80
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
