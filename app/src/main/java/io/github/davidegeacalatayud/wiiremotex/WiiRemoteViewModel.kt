package io.github.davidegeacalatayud.wiiremotex

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import kotlinx.coroutines.flow.StateFlow

class WiiRemoteViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as WiiRemoteXApplication
    private val runtime = app.runtime

    val uiState: StateFlow<WiiRemoteUiState> = runtime.uiState

    fun setTransportMode(mode: TransportMode) {
        runtime.setTransportMode(mode)
    }

    fun startHid() {
        val intent = Intent(app, WiiRemoteForegroundService::class.java).apply {
            action = WiiRemoteForegroundService.ACTION_START_HID
        }
        app.startForegroundService(intent)
    }

    fun stopHid() {
        app.stopService(Intent(app, WiiRemoteForegroundService::class.java))
    }

    fun startWiiPairing() = runtime.startWiiPairing()
    fun stopWiiPairing() = runtime.stopWiiPairing()
    fun clearWiiBond() = runtime.clearWiiBond()

    fun recordDiscoverabilityRequested(durationSeconds: Int) {
        runtime.recordDiscoverabilityRequested(durationSeconds)
    }

    fun recordDiscoverabilityResult(resultCode: Int) {
        runtime.recordDiscoverabilityResult(resultCode)
    }

    fun onButtonChanged(button: WiiButton, pressed: Boolean) {
        runtime.onButtonChanged(button, pressed)
    }

    fun setIrPointer(x: Float, y: Float) = runtime.setIrPointer(x, y)
    fun setIrEnabled(enabled: Boolean) = runtime.setIrEnabled(enabled)
    fun setNunchukEnabled(enabled: Boolean) = runtime.setNunchukEnabled(enabled)
    fun setNunchukStick(x: Float, y: Float) = runtime.setNunchukStick(x, y)
    fun setNunchukC(pressed: Boolean) = runtime.setNunchukButton(cPressed = pressed)
    fun setNunchukZ(pressed: Boolean) = runtime.setNunchukButton(zPressed = pressed)
    fun setMotionPlusEnabled(enabled: Boolean) = runtime.setMotionPlusEnabled(enabled)
    fun setMotionPointerEnabled(enabled: Boolean) = runtime.setMotionPointerEnabled(enabled)
    fun recenterMotionPointer() = runtime.recenterMotionPointer()
    fun exportHardwareTraceJson(): String = runtime.exportHardwareTraceJson()
    fun clearHardwareTrace() = runtime.clearHardwareTrace()
}
