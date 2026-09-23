package io.github.davidegeacalatayud.wiiremotex

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.davidegeacalatayud.wiiremotex.feature.controller.ControllerScreen
import io.github.davidegeacalatayud.wiiremotex.platform.sensors.PointerTuning

class MainActivity : ComponentActivity() {
    private val viewModel: WiiRemoteViewModel by viewModels()

    private var afterPermissionGranted: (() -> Unit)? = null
    private var bluetoothReceiverRegistered = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val adapterState = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR,
            )
            if (adapterState != BluetoothAdapter.STATE_ON) return

            val state = viewModel.uiState.value
            if (
                state.transportMode == TransportMode.ESP32_BRIDGE &&
                state.hidStage == HidStage.ERROR &&
                hasBluetoothPermissions(TransportMode.ESP32_BRIDGE)
            ) {
                viewModel.startHid()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.all { it }
        val action = afterPermissionGranted
        afterPermissionGranted = null

        if (granted) {
            action?.invoke()
        }
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        // Result code is the number of seconds granted, or RESULT_CANCELED.
        viewModel.recordDiscoverabilityResult(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var pointerSensitivity by remember {
                mutableFloatStateOf(PointerTuning.get(this@MainActivity))
            }

            MaterialTheme {
                Surface {
                    ControllerScreen(
                        connectionLabel = state.hidStage.name,
                        useEsp32Bridge = state.transportMode == TransportMode.ESP32_BRIDGE,
                        bridgeReady = state.bridgeReady,
                        bridgeProtocolVersion = state.bridgeProtocolVersion,
                        bridgeFirmwareVersion = state.bridgeFirmwareVersion,
                        wiimoteState = state.wiimote,
                        diagnosticLines = state.diagnostics.takeLast(8).map { entry ->
                            "${entry.timestamp} ${entry.direction}  ${entry.message}"
                        },
                        lastError = state.lastError,
                        pointerSensitivity = pointerSensitivity,
                        onPointerSensitivityChanged = { value ->
                            pointerSensitivity = PointerTuning.set(this@MainActivity, value)
                        },
                        onTransportChanged = { useBridge ->
                            viewModel.selectTransport(
                                if (useBridge) TransportMode.ESP32_BRIDGE
                                else TransportMode.DIRECT_ANDROID_HID,
                            )
                        },
                        onStartHid = {
                            withBluetoothPermissions(state.transportMode) {
                                viewModel.startHid()
                            }
                        },
                        onMakeDiscoverable = {
                            withBluetoothPermissions(TransportMode.DIRECT_ANDROID_HID) {
                                requestDiscoverable()
                            }
                        },
                        onStopHid = viewModel::stopHid,
                        onStartWiiPairing = viewModel::startWiiPairing,
                        onStopWiiPairing = viewModel::stopWiiPairing,
                        onClearWiiBond = viewModel::clearWiiBond,
                        onShareDiagnostics = {
                            shareDiagnostics(state, pointerSensitivity)
                        },
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

    override fun onStart() {
        super.onStart()
        registerBluetoothStateReceiver()
    }

    override fun onStop() {
        unregisterBluetoothStateReceiver()
        super.onStop()
    }

    private fun registerBluetoothStateReceiver() {
        if (bluetoothReceiverRegistered) return
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothStateReceiver, filter)
        }
        bluetoothReceiverRegistered = true
    }

    private fun unregisterBluetoothStateReceiver() {
        if (!bluetoothReceiverRegistered) return
        unregisterReceiver(bluetoothStateReceiver)
        bluetoothReceiverRegistered = false
    }

    private fun withBluetoothPermissions(
        mode: TransportMode,
        action: () -> Unit,
    ) {
        val missing = requiredBluetoothPermissions(mode).filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            action()
            return
        }

        afterPermissionGranted = action
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hasBluetoothPermissions(mode: TransportMode): Boolean =
        requiredBluetoothPermissions(mode).all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun requiredBluetoothPermissions(mode: TransportMode): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            mode == TransportMode.ESP32_BRIDGE -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        }

        mode == TransportMode.ESP32_BRIDGE -> {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        else -> emptyArray()
    }

    private fun shareDiagnostics(
        state: WiiRemoteUiState,
        pointerSensitivity: Float,
    ) {
        val diagnostics = buildString {
            appendLine("WiiRemoteX diagnostics")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Transport: ${state.transportMode.displayName}")
            appendLine("HID stage: ${state.hidStage}")
            if (state.transportMode == TransportMode.ESP32_BRIDGE) {
                appendLine("Bridge ready: ${state.bridgeReady}")
                appendLine("Bridge protocol: ${state.bridgeProtocolVersion ?: "unknown"}")
                appendLine("Bridge firmware: ${state.bridgeFirmwareVersion ?: "unknown"}")
            }
            appendLine("Pointer sensitivity: ${pointerSensitivity}x")
            appendLine("Report mode: 0x${state.wiimote.reportMode.toString(16).uppercase().padStart(2, '0')}")
            appendLine("Data reporting enabled: ${state.wiimote.dataReportingEnabled}")
            appendLine("Continuous reporting: ${state.wiimote.continuousReporting}")
            appendLine(
                "IR: enabled=${state.wiimote.infrared.enabled}, " +
                    "configured=${state.wiimote.infrared.configured}, " +
                    "mode=${state.wiimote.infrared.mode}",
            )
            appendLine(
                "Nunchuk: connected=${state.wiimote.nunchuk.connected}, " +
                    "initialized=${state.wiimote.nunchuk.initialized}, " +
                    "plaintext=${state.wiimote.nunchuk.encryptionDisabled}",
            )
            appendLine(
                "MotionPlus: present=${state.wiimote.motionPlus.present}, " +
                    "initialized=${state.wiimote.motionPlus.initialized}, " +
                    "active=${state.wiimote.motionPlus.active}, " +
                    "mode=0x${state.wiimote.motionPlus.activationMode.toString(16).uppercase()}, " +
                    "passthrough=${state.wiimote.motionPlus.passThroughNunchuk}",
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

        startActivity(
            Intent.createChooser(intent, "Share WiiRemoteX diagnostics"),
        )
    }

    private fun shareHardwareTrace(json: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "WiiRemoteX hardware trace")
            putExtra(Intent.EXTRA_TEXT, json)
        }

        startActivity(
            Intent.createChooser(intent, "Share WiiRemoteX hardware trace"),
        )
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
