package io.github.davidegeacalatayud.wiiremotex.feature.controller

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton

@Composable
fun ControllerScreen(
    connectionLabel: String,
    onButtonChanged: (WiiButton, Boolean) -> Unit = { _, _ -> },
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("WiiRemoteX", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(connectionLabel, style = MaterialTheme.typography.bodyMedium)
        }

        DPad(onButtonChanged)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteButton("A", WiiButton.A, onButtonChanged, 78)
            Spacer(Modifier.height(14.dp))
            RemoteButton("B", WiiButton.B, onButtonChanged, 62)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteButton("−", WiiButton.MINUS, onButtonChanged, 48)
            RemoteButton("HOME", WiiButton.HOME, onButtonChanged, 64)
            RemoteButton("+", WiiButton.PLUS, onButtonChanged, 48)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RemoteButton("1", WiiButton.ONE, onButtonChanged, 54)
            RemoteButton("2", WiiButton.TWO, onButtonChanged, 54)
        }

        Text(
            "Pointer · Motion · Nunchuk come after the HID PoC",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DPad(onButtonChanged: (WiiButton, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteButton("▲", WiiButton.UP, onButtonChanged, 48)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteButton("◀", WiiButton.LEFT, onButtonChanged, 48)
            Spacer(Modifier.width(48.dp))
            RemoteButton("▶", WiiButton.RIGHT, onButtonChanged, 48)
        }
        RemoteButton("▼", WiiButton.DOWN, onButtonChanged, 48)
    }
}

@Composable
private fun RemoteButton(
    label: String,
    button: WiiButton,
    onButtonChanged: (WiiButton, Boolean) -> Unit,
    size: Int,
) {
    var pressed by remember(button) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .size(size.dp)
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onButtonChanged(button, true)
                    waitForUpOrCancellation()
                    pressed = false
                    onButtonChanged(button, false)
                }
            },
        shape = if (label == "HOME") RoundedCornerShape(20.dp) else CircleShape,
        tonalElevation = if (pressed) 8.dp else 2.dp,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, fontWeight = if (pressed) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
