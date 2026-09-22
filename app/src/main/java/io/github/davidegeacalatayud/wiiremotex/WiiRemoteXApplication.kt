package io.github.davidegeacalatayud.wiiremotex

import android.app.Application

class WiiRemoteXApplication : Application() {
    val runtime: WiiRemoteRuntime by lazy {
        WiiRemoteRuntime(this)
    }
}
