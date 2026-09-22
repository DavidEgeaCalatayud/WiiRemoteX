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
}
