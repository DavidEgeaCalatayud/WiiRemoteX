package io.github.davidegeacalatayud.wiiremotex

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davidegeacalatayud.wiiremotex.feature.controller.ControllerScreen

class MainActivity : ComponentActivity() {
    private val viewModel: WiiRemoteViewModel by viewModels()

    private var afterPermissionGranted: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.all { it }
        val action = afterPermissionGranted
        afterPermissionGranted = null
        if (granted) action?.invoke()
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.recordDiscoverabilityResult(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            MaterialTheme {
                Surface {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Transport", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.setTransportMode(TransportMode.DIRECT_HID) },
                                    enabled = state.transportMode != TransportMode.DIRECT_HID,
                                ) {
                                    Text("Direct HID")
                                }
                                Button(
                                    onClick = { viewModel.setTransportMode(TransportMode.ESP32_BRIDGE) },
                                    enabled = state.transportMode != TransportMode.ESP32_BRIDGE,
                                ) {
                                    Text("ESP32 Bridge")
                                }
                            }

                            if (state.transportMode == TransportMode.ESP32_BRIDGE) {
                                val protocol = if (state.bridgeProtocolReady) {
                                    "Ready v${state.bridgeProtocolVersion}"
                                } else {
                                    "Waiting"
                                }
                                Text(
                                    "Android → BLE → ESP32 → Bluetooth Classic HID → Wii · $protocol",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = viewModel::startWiiPairing,
                                        enabled = state.bridgeProtocolReady && state.hidStage != HidStage.CONNECTED,
                                    ) { Text("Pair Wii") }
                                    Button(
                                        onClick = viewModel::stopWiiPairing,
                                        enabled = state.bridgeProtocolReady && state.hidStage == HidStage.CONNECTING,
                                    ) { Text("Stop pairing") }
                                    Button(
                                        onClick = viewModel::clearWiiBond,
                                        enabled = state.bridgeProtocolReady,
                                    ) { Text("Clear bond") }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            ControllerScreen(
                                connectionLabel = "${state.transportMode.name} · ${state.hidStage.name}",
                                wiimoteState = state.wiimote,
                                diagnosticLines = state.diagnostics.takeLast(8).map { entry ->
                                    "${entry.timestamp} ${entry.direction}  ${entry.message}"
                                },
                                lastError = state.lastError,
                                onStartHid = {
                                    withBluetoothPermissions {
                                        viewModel.startHid()
                                    }
                                },
                                onMakeDiscoverable = {
                                    if (state.transportMode == TransportMode.DIRECT_HID) {
                                        withBluetoothPermissions { requestDiscoverable() }
                                    }
                                },
                                onStopHid = viewModel::stopHid,
                                onShareDiagnostics = { shareDiagnostics(state) },
                                onShareHardwareTrace = {
                                    shareHardwareTrace(viewModel.exportHardwareTraceJson())
                                },
                                onClearHardwareTrace = viewModel::clearHardwareTrace,
                                onButtonChanged = viewModel::onButtonChanged,
                                onIrPointer = viewModel::setIrPointer,
                                onIrEnabled = viewModel::setIrEnabled,
                                onNunchukEnabled = viewModel::setNunchukEnabled,
                                onNunchukStick = viewModel::setNunchukStick,
                                onNunchukC = viewModel::setNunchukC,
                                onNunchukZ = viewModel::setNunchukZ,
                                onMotionPlusEnabled = viewModel::setMotionPlusEnabled,
                                motionPointerEnabled = state.motionPointerEnabled,
                                onMotionPointerEnabled = viewModel::setMotionPointerEnabled,
                                onRecenterMotionPointer = viewModel::recenterMotionPointer,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun withBluetoothPermissions(action: () -> Unit) {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = required.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            action()
            return
        }

        afterPermissionGranted = action
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun shareDiagnostics(state: WiiRemoteUiState) {
        val diagnostics = buildString {
            appendLine("WiiRemoteX diagnostics")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Transport: ${state.transportMode}")
            appendLine("Connection stage: ${state.hidStage}")
            if (state.transportMode == TransportMode.ESP32_BRIDGE) {
                appendLine("Bridge ready: ${state.bridgeProtocolReady}")
                appendLine("Bridge protocol: v${state.bridgeProtocolVersion}")
            }
            appendLine("Report mode: 0x${state.wiimote.reportMode.toString(16).uppercase().padStart(2, '0')}")
            appendLine("Data reporting enabled: ${state.wiimote.dataReportingEnabled}")
            appendLine("Continuous reporting: ${state.wiimote.continuousReporting}")
            appendLine(
                "IR: enabled=${state.wiimote.infrared.enabled}, configured=${state.wiimote.infrared.configured}, mode=${state.wiimote.infrared.mode}",
            )
            appendLine(
                "Nunchuk: connected=${state.wiimote.nunchuk.connected}, initialized=${state.wiimote.nunchuk.initialized}, plaintext=${state.wiimote.nunchuk.encryptionDisabled}",
            )
            appendLine(
                "MotionPlus: present=${state.wiimote.motionPlus.present}, initialized=${state.wiimote.motionPlus.initialized}, active=${state.wiimote.motionPlus.active}, mode=0x${state.wiimote.motionPlus.activationMode.toString(16).uppercase()}, passthrough=${state.wiimote.motionPlus.passThroughNunchuk}",
            )
            appendLine("Rumble: ${state.wiimote.rumbleEnabled}")
            appendLine("Battery byte: 0x${state.wiimote.batteryLevel.toString(16).uppercase().padStart(2, '0')}")
            appendLine()
            appendLine("Events:")
            state.diagnostics.forEach { entry ->
                appendLine("${entry.timestamp} ${entry.direction}  ${entry.message}")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "WiiRemoteX diagnostics")
            putExtra(Intent.EXTRA_TEXT, diagnostics)
        }
        startActivity(Intent.createChooser(intent, "Share WiiRemoteX diagnostics"))
    }

    private fun shareHardwareTrace(json: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "WiiRemoteX hardware trace")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, "Share WiiRemoteX hardware trace"))
    }

    private fun requestDiscoverable() {
        viewModel.recordDiscoverabilityRequested(DISCOVERABLE_SECONDS)
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        }
        discoverableLauncher.launch(intent)
    }

    private companion object {
        const val DISCOVERABLE_SECONDS = 300
    }
}
