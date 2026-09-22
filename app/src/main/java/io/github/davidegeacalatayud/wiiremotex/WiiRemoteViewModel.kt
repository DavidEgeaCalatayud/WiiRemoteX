package io.github.davidegeacalatayud.wiiremotex

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionType
import io.github.davidegeacalatayud.wiiremotex.core.model.PointerMode
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import kotlinx.coroutines.flow.StateFlow

class WiiRemoteViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as WiiRemoteXApplication
    private val runtime = app.runtime

    val uiState: StateFlow<WiiRemoteUiState> = runtime.uiState

    fun startHid() {
        val intent = Intent(app, WiiRemoteForegroundService::class.java).apply {
            action = WiiRemoteForegroundService.ACTION_START_HID
        }
        app.startForegroundService(intent)
    }

    fun stopHid() {
        app.stopService(
            Intent(app, WiiRemoteForegroundService::class.java),
        )
    }

    fun onButtonChanged(button: WiiButton, pressed: Boolean) {
        runtime.onButtonChanged(button, pressed)
    }

    fun calibratePointer() {
        runtime.calibratePointer()
    }

    fun setPointerMode(mode: PointerMode) {
        runtime.setPointerMode(mode)
    }

    fun setTouchPointer(x: Float, y: Float) {
        runtime.setTouchPointer(x, y)
    }

    fun selectExtension(type: ExtensionType) {
        runtime.selectExtension(type)
    }

    fun setNunchukStick(x: Int, y: Int) {
        runtime.setNunchukStick(x, y)
    }

    fun setNunchukCPressed(pressed: Boolean) {
        runtime.setNunchukCPressed(pressed)
    }

    fun setNunchukZPressed(pressed: Boolean) {
        runtime.setNunchukZPressed(pressed)
    }
}
