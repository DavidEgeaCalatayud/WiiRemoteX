package io.github.davidegeacalatayud.wiiremotex.feature.controller

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionType
import io.github.davidegeacalatayud.wiiremotex.core.model.PointerMode
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import kotlin.math.roundToInt

@Composable
fun ControllerScreen(
    connectionLabel: String,
    wiimoteState: WiimoteState,
    diagnosticLines: List<String>,
    lastError: String?,
    pointerCalibrated: Boolean,
    pointerMode: PointerMode,
    sensorAccelerometer: Boolean,
    sensorGyroscope: Boolean,
    sensorRotationVector: Boolean,
    onCalibratePointer: () -> Unit,
    onPointerModeChanged: (PointerMode) -> Unit,
    onTouchPointer: (Float, Float) -> Unit,
    onSelectExtension: (ExtensionType) -> Unit,
    onNunchukStick: (Int, Int) -> Unit,
    onNunchukCChanged: (Boolean) -> Unit,
    onNunchukZChanged: (Boolean) -> Unit,
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
                text = "Android → real Wii · Bluetooth HID",
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

        AdvancedFeaturesCard(
            state = wiimoteState,
            pointerCalibrated = pointerCalibrated,
            pointerMode = pointerMode,
            sensorAccelerometer = sensorAccelerometer,
            sensorGyroscope = sensorGyroscope,
            sensorRotationVector = sensorRotationVector,
            onCalibratePointer = onCalibratePointer,
            onPointerModeChanged = onPointerModeChanged,
            onTouchPointer = onTouchPointer,
            onSelectExtension = onSelectExtension,
            onNunchukStick = onNunchukStick,
            onNunchukCChanged = onNunchukCChanged,
            onNunchukZChanged = onNunchukZChanged,
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
                Button(onClick = onStartHid) { Text("Start HID") }
                Button(onClick = onMakeDiscoverable) { Text("Visible") }
                Button(onClick = onStopHid) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun AdvancedFeaturesCard(
    state: WiimoteState,
    pointerCalibrated: Boolean,
    pointerMode: PointerMode,
    sensorAccelerometer: Boolean,
    sensorGyroscope: Boolean,
    sensorRotationVector: Boolean,
    onCalibratePointer: () -> Unit,
    onPointerModeChanged: (PointerMode) -> Unit,
    onTouchPointer: (Float, Float) -> Unit,
    onSelectExtension: (ExtensionType) -> Unit,
    onNunchukStick: (Int, Int) -> Unit,
    onNunchukCChanged: (Boolean) -> Unit,
    onNunchukZChanged: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Experimental · Motion / IR / Extensions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "Sensors  ACC ${mark(sensorAccelerometer)}  GYRO ${mark(sensorGyroscope)}  ROT ${mark(sensorRotationVector)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            val accel = state.motion.accelerationG
            val gyro = state.motion.angularVelocityDegPerSec
            val orientation = state.motion.orientation

            Text(
                text = "Accel g  x=${accel.x.f1()} y=${accel.y.f1()} z=${accel.z.f1()}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Gyro °/s x=${gyro.x.f1()} y=${gyro.y.f1()} z=${gyro.z.f1()}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Orientation yaw=${orientation.yawDegrees.f1()} pitch=${orientation.pitchDegrees.f1()} roll=${orientation.rollDegrees.f1()}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = "Pointer mode",
                style = MaterialTheme.typography.labelLarge,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onPointerModeChanged(PointerMode.MOTION) },
                ) {
                    Text(if (pointerMode == PointerMode.MOTION) "Motion ✓" else "Motion")
                }
                Button(
                    onClick = { onPointerModeChanged(PointerMode.TOUCH) },
                ) {
                    Text(if (pointerMode == PointerMode.TOUCH) "Touch ✓" else "Touch")
                }
            }

            if (pointerMode == PointerMode.MOTION) {
                Button(
                    onClick = onCalibratePointer,
                    enabled = sensorRotationVector,
                ) {
                    Text(if (pointerCalibrated) "Recalibrate IR center" else "Calibrate IR center")
                }
            } else {
                TouchPointerPad(onTouchPointer = onTouchPointer)
            }

            Text(
                text = "IR host=${if (state.infrared.enabled) "ON" else "OFF"} · calibrated=${if (pointerCalibrated) "YES" else "NO"} · ${irSummary(state)}",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "Virtual extension",
                style = MaterialTheme.typography.labelLarge,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onSelectExtension(ExtensionType.NONE) }) { Text("None") }
                Button(onClick = { onSelectExtension(ExtensionType.NUNCHUK) }) { Text("Nunchuk") }
                Button(onClick = { onSelectExtension(ExtensionType.MOTION_PLUS) }) { Text("Motion+") }
            }

            when (val extension = state.extension) {
                ExtensionState.None -> {
                    Text("No virtual extension selected.", style = MaterialTheme.typography.bodySmall)
                }

                is ExtensionState.Nunchuk -> {
                    val n = extension.value
                    Text(
                        text = "Nunchuk stick X=${n.stickX} Y=${n.stickY}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = n.stickX.toFloat(),
                        onValueChange = { value -> onNunchukStick(value.roundToInt(), n.stickY) },
                        valueRange = 35f..228f,
                    )
                    Slider(
                        value = n.stickY.toFloat(),
                        onValueChange = { value -> onNunchukStick(n.stickX, value.roundToInt()) },
                        valueRange = 27f..220f,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        HoldButton("C", n.cPressed, onNunchukCChanged)
                        HoldButton("Z", n.zPressed, onNunchukZChanged)
                    }
                }

                is ExtensionState.MotionPlus -> {
                    val m = extension.value
                    Text(
                        text = "MotionPlus ${if (m.active) "ACTIVE" else "waiting for 0xA600FE activation"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "yaw=${m.yawDegPerSec.f1()} roll=${m.rollDegPerSec.f1()} pitch=${m.pitchDegPerSec.f1()} °/s",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Text(
                text = "The Wii selects the actual stream with report mode 0x31/0x33/0x35/0x37.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TouchPointerPad(
    onTouchPointer: (Float, Float) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(onTouchPointer) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    fun emit(x: Float, y: Float) {
                        if (size.width <= 0 || size.height <= 0) return
                        onTouchPointer(
                            (x / size.width).coerceIn(0f, 1f),
                            (y / size.height).coerceIn(0f, 1f),
                        )
                    }

                    emit(down.position.x, down.position.y)

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            emit(change.position.x, change.position.y)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Touch / drag here to move the Wii pointer",
                style = MaterialTheme.typography.bodyMedium,
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
            Text(
                text = "Diagnostics",
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
            Button(onClick = onShareDiagnostics) { Text("Share diagnostics") }
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
            Text(label, fontWeight = if (pressed) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun HoldButton(
    label: String,
    externallyPressed: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    var pressed by remember(label, externallyPressed) { mutableStateOf(externallyPressed) }

    Surface(
        modifier = Modifier
            .size(56.dp)
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onChanged(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onChanged(false)
                }
            },
        shape = CircleShape,
        tonalElevation = if (pressed) 8.dp else 2.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, fontWeight = if (pressed) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

private fun ledText(state: WiimoteState): String {
    val leds = listOf(state.leds.one, state.leds.two, state.leds.three, state.leds.four)
    return leds.mapIndexed { index, enabled ->
        if (enabled) "${index + 1}●" else "${index + 1}○"
    }.joinToString("  ")
}

private fun batteryPercent(state: WiimoteState): Int =
    ((state.batteryLevel.coerceIn(0, 0xFF) / 255.0) * 100.0).toInt()

private fun mark(value: Boolean): String = if (value) "✓" else "—"

private fun Float.f1(): String = "%.1f".format(this)

private fun irSummary(state: WiimoteState): String {
    val visible = state.infrared.points.filter { it.visible }
    if (visible.isEmpty()) return "no visible virtual dots"
    return visible.take(2).joinToString(" · ") { point ->
        "(${point.x},${point.y})"
    }
}
