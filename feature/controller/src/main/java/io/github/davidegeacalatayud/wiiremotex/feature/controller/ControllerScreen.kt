package io.github.davidegeacalatayud.wiiremotex.feature.controller

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    onIrPointer: (Float, Float) -> Unit,
    onIrEnabled: (Boolean) -> Unit,
    onNunchukEnabled: (Boolean) -> Unit,
    onNunchukStick: (Float, Float) -> Unit,
    onNunchukC: (Boolean) -> Unit,
    onNunchukZ: (Boolean) -> Unit,
    onMotionPlusEnabled: (Boolean) -> Unit,
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
                text = "Android → real Wii · Bluetooth HID emulator",
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

        Text("Wii Remote", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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

        MotionCard(wiimoteState)

        IrPointerCard(
            enabled = wiimoteState.infrared.enabled,
            onEnabled = onIrEnabled,
            onPointer = onIrPointer,
        )

        NunchukCard(
            state = wiimoteState,
            onEnabled = onNunchukEnabled,
            onStick = onNunchukStick,
            onC = onNunchukC,
            onZ = onNunchukZ,
        )

        MotionPlusCard(
            state = wiimoteState,
            onEnabled = onMotionPlusEnabled,
        )

        DiagnosticsCard(
            lines = diagnosticLines,
            onShareDiagnostics = onShareDiagnostics,
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
            )
            Text(text = "LEDs: ${ledText(wiimoteState)}")
            Text(
                text = "Battery: ${batteryPercent(wiimoteState)}% · Wii byte 0x" +
                    wiimoteState.batteryLevel.toString(16).uppercase().padStart(2, '0'),
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
                Button(onClick = onStartHid) { Text("Start HID") }
                Button(onClick = onMakeDiscoverable) { Text("Visible") }
                Button(onClick = onStopHid) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun MotionCard(state: WiimoteState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Motion sensors · live", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Accel  X ${state.motion.accelerationX} · Y ${state.motion.accelerationY} · Z ${state.motion.accelerationZ}",
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Gyro   Yaw ${state.motion.gyroYaw} · Roll ${state.motion.gyroRoll} · Pitch ${state.motion.gyroPitch}",
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Sent automatically when Wii selects a motion-capable report mode.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun IrPointerCard(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    onPointer: (Float, Float) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Virtual IR pointer", fontWeight = FontWeight.SemiBold)
                    Text("Touch/drag trackpad → two virtual IR dots", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .pointerInput(enabled) {
                        detectDragGestures(
                            onDragStart = { position ->
                                if (enabled) {
                                    onPointer(
                                        position.x / size.width.toFloat(),
                                        position.y / size.height.toFloat(),
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                if (enabled) {
                                    onPointer(
                                        change.position.x / size.width.toFloat(),
                                        change.position.y / size.height.toFloat(),
                                    )
                                }
                            },
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (enabled) "Drag here to point" else "Enable IR pointer")
                }
            }
        }
    }
}

@Composable
private fun NunchukCard(
    state: WiimoteState,
    onEnabled: (Boolean) -> Unit,
    onStick: (Float, Float) -> Unit,
    onC: (Boolean) -> Unit,
    onZ: (Boolean) -> Unit,
) {
    var stickX by remember { mutableFloatStateOf(0f) }
    var stickY by remember { mutableFloatStateOf(0f) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Virtual Nunchuk", fontWeight = FontWeight.SemiBold)
                Switch(
                    checked = state.nunchuk.connected,
                    onCheckedChange = onEnabled,
                )
            }

            Text("Stick X")
            Slider(
                value = stickX,
                onValueChange = {
                    stickX = it
                    onStick(stickX, stickY)
                },
                valueRange = -1f..1f,
                enabled = state.nunchuk.connected,
            )

            Text("Stick Y")
            Slider(
                value = stickY,
                onValueChange = {
                    stickY = it
                    onStick(stickX, stickY)
                },
                valueRange = -1f..1f,
                enabled = state.nunchuk.connected,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HoldButton("C", state.nunchuk.connected, onChanged = onC)
                HoldButton("Z", state.nunchuk.connected, onChanged = onZ)
            }

            Text(
                text = "Original six-byte Nunchuk extension payload.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MotionPlusCard(
    state: WiimoteState,
    onEnabled: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("MotionPlus emulation", fontWeight = FontWeight.SemiBold)
                Text(
                    "Android gyroscope → six-byte MotionPlus payload.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.motionPlus.enabled,
                onCheckedChange = onEnabled,
            )
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
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (lines.isEmpty()) {
                Text("No Bluetooth events yet.", style = MaterialTheme.typography.bodySmall)
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
    HoldButton(
        label = label,
        enabled = true,
        size = size,
        onChanged = { pressed -> onButtonChanged(button, pressed) },
    )
}

@Composable
private fun HoldButton(
    label: String,
    enabled: Boolean,
    size: Int = 58,
    onChanged: (Boolean) -> Unit,
) {
    var pressed by remember(label) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .size(size.dp)
            .pointerInput(label, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onChanged(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onChanged(false)
                }
            },
        shape = if (label == "HOME") RoundedCornerShape(20.dp) else CircleShape,
        tonalElevation = if (pressed) 8.dp else 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
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
