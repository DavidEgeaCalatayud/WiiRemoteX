package io.github.davidegeacalatayud.wiiremotex.feature.controller

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

@Composable
fun ControllerScreen(
    connectionLabel: String,
    wiimoteState: WiimoteState,
    diagnosticLines: List<String>,
    lastError: String?,
    onStartHid: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onStopHid: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onButtonChanged: (WiiButton, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "WiiRemoteX",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Android → real Wii · Bluetooth HID PoC",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        ConnectionCard(
            connectionLabel = connectionLabel,
            wiimoteState = wiimoteState,
            lastError = lastError,
            onStartHid = onStartHid,
            onMakeDiscoverable = onMakeDiscoverable,
            onStopHid = onStopHid,
        )

        Text(
            text = "Controller",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        DPad(onButtonChanged)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteButton("B", WiiButton.B, onButtonChanged, 62)
            RemoteButton("A", WiiButton.A, onButtonChanged, 78)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteButton("−", WiiButton.MINUS, onButtonChanged, 48)
            RemoteButton("HOME", WiiButton.HOME, onButtonChanged, 64)
            RemoteButton("+", WiiButton.PLUS, onButtonChanged, 48)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            RemoteButton("1", WiiButton.ONE, onButtonChanged, 54)
            RemoteButton("2", WiiButton.TWO, onButtonChanged, 54)
        }

        DiagnosticsCard(
            lines = diagnosticLines,
            onShareDiagnostics = onShareDiagnostics,
        )

        Text(
            text = "Pointer · Motion · Nunchuk remain intentionally deferred until the real-Wii HID link works.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ConnectionCard(
    connectionLabel: String,
    wiimoteState: WiimoteState,
    lastError: String?,
    onStartHid: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onStopHid: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Connection · $connectionLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "Report mode: 0x${wiimoteState.reportMode.toString(16).uppercase().padStart(2, '0')} · " +
                    "Rumble: ${if (wiimoteState.rumbleEnabled) "ON" else "OFF"}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "LEDs: ${ledText(wiimoteState)}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Battery: ${batteryPercent(wiimoteState)}% · Wii byte 0x" +
                    wiimoteState.batteryLevel.toString(16).uppercase().padStart(2, '0'),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (lastError != null) {
                Text(
                    text = lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onStartHid) {
                    Text("Start HID")
                }
                Button(onClick = onMakeDiscoverable) {
                    Text("Visible 5 min")
                }
                Button(onClick = onStopHid) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(
    lines: List<String>,
    onShareDiagnostics: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (lines.isEmpty()) {
                Text(
                    text = "No Bluetooth events yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Button(onClick = onShareDiagnostics) {
                Text("Share diagnostics")
            }
        }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontWeight = if (pressed) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private fun ledText(state: WiimoteState): String {
    val leds = listOf(
        state.leds.one,
        state.leds.two,
        state.leds.three,
        state.leds.four,
    )

    return leds.mapIndexed { index, enabled ->
        if (enabled) "${index + 1}●" else "${index + 1}○"
    }.joinToString("  ")
}

private fun batteryPercent(state: WiimoteState): Int =
    ((state.batteryLevel.coerceIn(0, 0xFF) / 255.0) * 100.0).toInt()
