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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
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
    ) {
        // Result code is the number of seconds granted, or RESULT_CANCELED.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            MaterialTheme {
                Surface {
                    ControllerScreen(
                        connectionLabel = state.hidStage.name,
                        wiimoteState = state.wiimote,
                        diagnosticLines = state.diagnostics.takeLast(8).map { entry ->
                            "${entry.timestamp} ${entry.direction}  ${entry.message}"
                        },
                        lastError = state.lastError,
                        pointerCalibrated = state.pointerCalibrated,
                        pointerMode = state.pointerMode,
                        sensorAccelerometer = state.sensors.accelerometer,
                        sensorGyroscope = state.sensors.gyroscope,
                        sensorRotationVector = state.sensors.rotationVector,
                        onCalibratePointer = viewModel::calibratePointer,
                        onPointerModeChanged = viewModel::setPointerMode,
                        onTouchPointer = viewModel::setTouchPointer,
                        onSelectExtension = viewModel::selectExtension,
                        onNunchukStick = viewModel::setNunchukStick,
                        onNunchukCChanged = viewModel::setNunchukCPressed,
                        onNunchukZChanged = viewModel::setNunchukZPressed,
                        onStartHid = {
                            withBluetoothPermissions {
                                viewModel.startHid()
                            }
                        },
                        onMakeDiscoverable = {
                            withBluetoothPermissions {
                                requestDiscoverable()
                            }
                        },
                        onStopHid = viewModel::stopHid,
                        onShareDiagnostics = {
                            shareDiagnostics(state)
                        },
                        onButtonChanged = viewModel::onButtonChanged,
                    )
                }
            }
        }
    }

    private fun withBluetoothPermissions(action: () -> Unit) {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            emptyArray()
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
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("HID stage: ${state.hidStage}")
            appendLine("Report mode: 0x${state.wiimote.reportMode.toString(16).uppercase().padStart(2, '0')}")
            appendLine("Continuous reporting: ${state.wiimote.continuousReporting}")
            appendLine("Rumble: ${state.wiimote.rumbleEnabled}")
            appendLine("Battery byte: 0x${state.wiimote.batteryLevel.toString(16).uppercase().padStart(2, '0')}")
            appendLine("Pointer calibrated: ${state.pointerCalibrated}")
            appendLine("Pointer mode: ${state.pointerMode}")
            appendLine("Sensors: accel=${state.sensors.accelerometer} gyro=${state.sensors.gyroscope} rotation=${state.sensors.rotationVector}")
            appendLine("Extension: ${state.wiimote.extension}")
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

    private fun requestDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        }
        discoverableLauncher.launch(intent)
    }

    private companion object {
        const val DISCOVERABLE_SECONDS = 300
    }
}
