package io.github.davidegeacalatayud.wiiremotex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import io.github.davidegeacalatayud.wiiremotex.feature.controller.ControllerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    ControllerScreen(connectionLabel = "Milestone 0 · HID PoC")
                }
            }
        }
    }
}
