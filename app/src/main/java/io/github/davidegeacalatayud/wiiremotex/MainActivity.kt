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

        if (granted) {
            action?.invoke()
        }
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
