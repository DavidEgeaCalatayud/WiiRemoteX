package io.github.davidegeacalatayud.wiiremotex.feature.controller

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.FilterChip
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
    useEsp32Bridge: Boolean,
    bridgeReady: Boolean,
    bridgeProtocolVersion: Int?,
    bridgeFirmwareVersion: String?,
    wiimoteState: WiimoteState,
    diagnosticLines: List<String>,
    lastError: String?,
    pointerSensitivity: Float,
    onPointerSensitivityChanged: (Float) -> Unit,
    onTransportChanged: (Boolean) -> Unit,
    onStartHid: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onStopHid: () -> Unit,
    onStartWiiPairing: () -> Unit,
    onStopWiiPairing: () -> Unit,
    onClearWiiBond: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onShareHardwareTrace: () -> Unit,
    onClearHardwareTrace: () -> Unit,
    onButtonChanged: (WiiButton, Boolean) -> Unit,
    onIrPointer: (Float, Float) -> Unit,
    onIrEnabled: (Boolean) -> Unit,
    onNunchukEnabled: (Boolean) -> Unit,
    onNunchukStick: (Float, Float) -> Unit,
    onNunchukC: (Boolean) -> Unit,
    onNunchukZ: (Boolean) -> Unit,
    onMotionPlusEnabled: (Boolean) -> Unit,
    motionPointerEnabled: Boolean,
    onMotionPointerEnabled: (Boolean) -> Unit,
    onRecenterMotionPointer: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= WIDE_LAYOUT_MIN_WIDTH

        if (wideLayout) {
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ControllerHeader(useEsp32Bridge)
                    ConnectionCard(
                        connectionLabel = connectionLabel,
                        useEsp32Bridge = useEsp32Bridge,
                        bridgeReady = bridgeReady,
                        bridgeProtocolVersion = bridgeProtocolVersion,
                        bridgeFirmwareVersion = bridgeFirmwareVersion,
                        wiimoteState = wiimoteState,
                        lastError = lastError,
                        onTransportChanged = onTransportChanged,
                        onStartHid = onStartHid,
                        onMakeDiscoverable = onMakeDiscoverable,
                        onStopHid = onStopHid,
                        onStartWiiPairing = onStartWiiPairing,
                        onStopWiiPairing = onStopWiiPairing,
                        onClearWiiBond = onClearWiiBond,
                    )
                    RemoteControls(onButtonChanged)
                }

                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    MotionCard(wiimoteState)
                    IrPointerCard(
                        enabled = wiimoteState.infrared.enabled,
                        motionPointerEnabled = motionPointerEnabled,
                        pointerSensitivity = pointerSensitivity,
                        onPointerSensitivityChanged = onPointerSensitivityChanged,
                        onEnabled = onIrEnabled,
                        onPointer = onIrPointer,
                        onMotionPointerEnabled = onMotionPointerEnabled,
                        onRecenterMotionPointer = onRecenterMotionPointer,
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
                        onShareHardwareTrace = onShareHardwareTrace,
                        onClearHardwareTrace = onClearHardwareTrace,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ControllerHeader(useEsp32Bridge)
                ConnectionCard(
                    connectionLabel = connectionLabel,
                    useEsp32Bridge = useEsp32Bridge,
                    bridgeReady = bridgeReady,
                    bridgeProtocolVersion = bridgeProtocolVersion,
                    bridgeFirmwareVersion = bridgeFirmwareVersion,
                    wiimoteState = wiimoteState,
                    lastError = lastError,
                    onTransportChanged = onTransportChanged,
                    onStartHid = onStartHid,
                    onMakeDiscoverable = onMakeDiscoverable,
                    onStopHid = onStopHid,
                    onStartWiiPairing = onStartWiiPairing,
                    onStopWiiPairing = onStopWiiPairing,
                    onClearWiiBond = onClearWiiBond,
                )
                RemoteControls(onButtonChanged)
                MotionCard(wiimoteState)
                IrPointerCard(
                    enabled = wiimoteState.infrared.enabled,
                    motionPointerEnabled = motionPointerEnabled,
                    pointerSensitivity = pointerSensitivity,
                    onPointerSensitivityChanged = onPointerSensitivityChanged,
                    onEnabled = onIrEnabled,
                    onPointer = onIrPointer,
                    onMotionPointerEnabled = onMotionPointerEnabled,
                    onRecenterMotionPointer = onRecenterMotionPointer,
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
                    onShareHardwareTrace = onShareHardwareTrace,
                    onClearHardwareTrace = onClearHardwareTrace,
                )
            }
        }
    }
}

@Composable
private fun ControllerHeader(useEsp32Bridge: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "WiiRemoteX",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (useEsp32Bridge) {
                "Android → BLE → ESP32 → Wii"
            } else {
                "Android → Wii · Bluetooth HID"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RemoteControls(onButtonChanged: (WiiButton, Boolean) -> Unit) {
    Text(
        "Wii Remote",
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
}

@Composable
private fun ConnectionCard(
    connectionLabel: String,
    useEsp32Bridge: Boolean,
    bridgeReady: Boolean,
    bridgeProtocolVersion: Int?,
    bridgeFirmwareVersion: String?,
    wiimoteState: WiimoteState,
    lastError: String?,
    onTransportChanged: (Boolean) -> Unit,
    onStartHid: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onStopHid: () -> Unit,
    onStartWiiPairing: () -> Unit,
    onStopWiiPairing: () -> Unit,
    onClearWiiBond: () -> Unit,
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

            Text("Transport", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !useEsp32Bridge,
                    onClick = { onTransportChanged(false) },
                    label = { Text("Direct HID") },
                )
                FilterChip(
                    selected = useEsp32Bridge,
                    onClick = { onTransportChanged(true) },
                    label = { Text("ESP32 Bridge") },
                )
            }

            ConnectionAssistant(
                connectionLabel = connectionLabel,
                useEsp32Bridge = useEsp32Bridge,
                bridgeReady = bridgeReady,
            )

            if (useEsp32Bridge) {
                Text(
                    text = buildString {
                        append("Bridge: ")
                        append(if (bridgeReady) "ready" else "waiting")
                        bridgeProtocolVersion?.let { append(" · protocol v$it") }
                        bridgeFirmwareVersion?.let { append(" · firmware $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

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
                Text(
                    text = recoveryHint(lastError, useEsp32Bridge),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onStartHid) {
                    Text(
                        if (lastError != null) {
                            "Retry"
                        } else if (useEsp32Bridge) {
                            "Connect bridge"
                        } else {
                            "Start HID"
                        },
                    )
                }
                if (!useEsp32Bridge) {
                    Button(onClick = onMakeDiscoverable) { Text("Visible") }
                }
                Button(onClick = onStopHid) { Text("Stop") }
            }

            if (useEsp32Bridge) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onStartWiiPairing,
                        enabled = bridgeReady,
                    ) {
                        Text("Pair Wii")
                    }
                    Button(
                        onClick = onStopWiiPairing,
                        enabled = bridgeReady,
                    ) {
                        Text("Stop pairing")
                    }
                }
                Button(
                    onClick = onClearWiiBond,
                    enabled = bridgeReady,
                ) {
                    Text("Clear Wii bond")
                }
            }
        }
    }
}

@Composable
private fun ConnectionAssistant(
    connectionLabel: String,
    useEsp32Bridge: Boolean,
    bridgeReady: Boolean,
) {
    val connected = connectionLabel == "CONNECTED"
    val transportReady = connectionLabel in setOf("REGISTERED", "CONNECTING", "CONNECTED")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Connection assistant",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "✓ 1. Transport selected: ${if (useEsp32Bridge) "ESP32 Bridge" else "Direct HID"}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (useEsp32Bridge) {
                "${if (bridgeReady) "✓" else "○"} 2. Connect ESP32 and wait for BRIDGE_READY"
            } else {
                "${if (transportReady) "✓" else "○"} 2. Start Android HID and make the phone visible"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (useEsp32Bridge) {
                "${if (connected) "✓" else "○"} 3. Pair Wii, then press the console red SYNC button"
            } else {
                "${if (connected) "✓" else "○"} 3. Sync the Wii while Android is discoverable"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MotionCard(state: WiimoteState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Motion sensors · live",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
    motionPointerEnabled: Boolean,
    pointerSensitivity: Float,
    onPointerSensitivityChanged: (Float) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onPointer: (Float, Float) -> Unit,
    onMotionPointerEnabled: (Boolean) -> Unit,
    onRecenterMotionPointer: () -> Unit,
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
                    Text(
                        "Mode: ${if (enabled) "enabled" else "off"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Motion Pointer", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Phone orientation → virtual IR coordinates",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = motionPointerEnabled,
                    onCheckedChange = onMotionPointerEnabled,
                )
            }

            Text(
                "Calibration wizard",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "1. Hold the phone in a comfortable neutral pointing position, keep it still, then recenter.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onRecenterMotionPointer,
                enabled = motionPointerEnabled,
            ) {
                Text("Calibrate + recenter")
            }

            Text(
                text = "2. Pointer sensitivity · ${formatSensitivity(pointerSensitivity)}×",
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = pointerSensitivity,
                onValueChange = onPointerSensitivityChanged,
                valueRange = 0.5f..2f,
                steps = 5,
            )
            Text(
                "Lower values require more phone movement; higher values reach the screen edges faster.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "3. Test center and all four screen edges. Recenter whenever your neutral grip changes.",
                style = MaterialTheme.typography.bodySmall,
            )

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
                text = "Initialized: ${state.nunchuk.initialized} · " +
                    "plaintext: ${state.nunchuk.encryptionDisabled}",
                style = MaterialTheme.typography.bodySmall,
            )
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
                    "Present: ${state.motionPlus.present} · Init: ${state.motionPlus.initialized} · " +
                        "Active: ${state.motionPlus.active}\n" +
                        "Android gyroscope → six-byte MotionPlus payload.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.motionPlus.present,
                onCheckedChange = onEnabled,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(
    lines: List<String>,
    onShareDiagnostics: () -> Unit,
    onShareHardwareTrace: () -> Unit,
    onClearHardwareTrace: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShareDiagnostics) {
                    Text("Share diagnostics")
                }
                Button(onClick = onShareHardwareTrace) {
                    Text("Export JSON trace")
                }
            }
            Button(onClick = onClearHardwareTrace) {
                Text("Clear hardware trace")
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

private fun recoveryHint(error: String, useEsp32Bridge: Boolean): String =
    when {
        error.contains("permission", ignoreCase = true) ->
            "Open Android app permissions and allow the requested Bluetooth access, then retry."
        error.contains("protocol", ignoreCase = true) ->
            "App and ESP32 firmware use different bridge protocol versions. Flash the matching firmware release."
        error.contains("scan", ignoreCase = true) || error.contains("ESP32", ignoreCase = true) ->
            "Check that the ESP32 is powered, nearby and running WiiRemoteX firmware, then reconnect."
        useEsp32Bridge ->
            "Stop the bridge transport, power-cycle the ESP32 if needed, then reconnect and retry Wii pairing."
        else ->
            "Stop HID, start it again, make Android discoverable and retry Wii synchronization."
    }

private fun formatSensitivity(value: Float): String =
    ((value * 100).toInt() / 100f).toString()

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

private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
