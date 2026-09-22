package io.github.davidegeacalatayud.wiiremotex.core.session

import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import io.github.davidegeacalatayud.wiiremotex.core.protocol.ExtensionRegisterBank
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HidInputReport
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HostCommand
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HostCommandDecoder
import io.github.davidegeacalatayud.wiiremotex.core.protocol.MemoryReportEncoder
import io.github.davidegeacalatayud.wiiremotex.core.protocol.StatusReportEncoder
import io.github.davidegeacalatayud.wiiremotex.core.protocol.WiimoteDataReportEncoder

sealed interface WiimoteEffect {
    data class SendReport(val report: HidInputReport) : WiimoteEffect
}

data class SessionResult(
    val state: WiimoteState,
    val effects: List<WiimoteEffect> = emptyList(),
)

class WiimoteSessionEngine(
    initialState: WiimoteState = WiimoteState(),
    private val dataEncoder: WiimoteDataReportEncoder = WiimoteDataReportEncoder(),
    private val statusEncoder: StatusReportEncoder = StatusReportEncoder(),
    private val memoryEncoder: MemoryReportEncoder = MemoryReportEncoder(),
    private val registerBank: ExtensionRegisterBank = ExtensionRegisterBank(),
    private val decoder: HostCommandDecoder = HostCommandDecoder(),
) {
    var state: WiimoteState = initialState
        private set

    fun setButton(button: WiiButton, pressed: Boolean): SessionResult {
        val buttons = state.pressedButtons.toMutableSet().apply {
            if (pressed) add(button) else remove(button)
        }

        state = state.copy(pressedButtons = buttons)
        return emitCurrentDataReport()
    }

    fun setBatteryLevel(level: Int): WiimoteState {
        state = state.copy(batteryLevel = level.coerceIn(0, 0xFF))
        return state
    }

    fun setMotion(
        motion: MotionState,
        emitReport: Boolean = true,
    ): SessionResult {
        state = state.copy(motion = motion)
        return if (emitReport) emitCurrentDataReport() else SessionResult(state)
    }

    fun setInfrared(
        infrared: InfraredState,
        emitReport: Boolean = true,
    ): SessionResult {
        state = state.copy(infrared = infrared)
        return if (emitReport) emitCurrentDataReport() else SessionResult(state)
    }

    fun setExtension(
        extension: ExtensionState,
        emitReport: Boolean = true,
    ): SessionResult {
        state = state.copy(extension = extension)
        return if (emitReport) emitCurrentDataReport() else SessionResult(state)
    }

    fun emitCurrentDataReport(): SessionResult =
        SessionResult(
            state = state,
            effects = listOf(
                WiimoteEffect.SendReport(dataEncoder.encode(state)),
            ),
        )

    fun onHostReport(reportId: Int, payload: ByteArray): SessionResult {
        val effects = when (val command = decoder.decode(reportId, payload)) {
            is HostCommand.SetPlayerLeds -> {
                state = state.copy(
                    leds = command.leds,
                    rumbleEnabled = command.rumbleEnabled,
                )
                emptyList()
            }

            is HostCommand.SetReportMode -> {
                state = state.copy(
                    reportMode = command.reportMode,
                    continuousReporting = command.continuous,
                    rumbleEnabled = command.rumbleEnabled,
                )
                emptyList()
            }

            is HostCommand.SetIrEnabled -> {
                state = state.copy(
                    infrared = state.infrared.copy(enabled = command.enabled),
                    rumbleEnabled = command.rumbleEnabled,
                )

                if (command.acknowledge) {
                    listOf(
                        WiimoteEffect.SendReport(
                            memoryEncoder.encodeAck(
                                state = state,
                                outputReportId = command.reportId,
                            ),
                        ),
                    )
                } else {
                    emptyList()
                }
            }

            is HostCommand.StatusRequest -> {
                state = state.copy(rumbleEnabled = command.rumbleEnabled)
                listOf(
                    WiimoteEffect.SendReport(statusEncoder.encode(state)),
                )
            }

            is HostCommand.WriteMemory -> {
                state = state.copy(rumbleEnabled = command.rumbleEnabled)

                val result =
                    if (command.registerSpace) {
                        registerBank.write(
                            extension = state.extension,
                            address = command.address,
                            data = command.data,
                        )
                    } else {
                        io.github.davidegeacalatayud.wiiremotex.core.protocol.RegisterWriteResult(
                            extension = state.extension,
                            error = ExtensionRegisterBank.ERROR_INVALID_ADDRESS,
                        )
                    }

                state = state.copy(extension = result.extension)

                listOf(
                    WiimoteEffect.SendReport(
                        memoryEncoder.encodeAck(
                            state = state,
                            outputReportId = 0x16,
                            error = result.error,
                        ),
                    ),
                )
            }

            is HostCommand.ReadMemory -> {
                state = state.copy(rumbleEnabled = command.rumbleEnabled)

                if (!command.registerSpace) {
                    listOf(
                        WiimoteEffect.SendReport(
                            memoryEncoder.encodeRead(
                                state = state,
                                address = command.address,
                                data = byteArrayOf(0x00),
                                error = ExtensionRegisterBank.ERROR_INVALID_ADDRESS,
                            ),
                        ),
                    )
                } else {
                    encodeReadResponses(command)
                }
            }

            is HostCommand.Unknown -> emptyList()
        }

        return SessionResult(state = state, effects = effects)
    }

    private fun encodeReadResponses(
        command: HostCommand.ReadMemory,
    ): List<WiimoteEffect> {
        val requested = command.size.coerceAtLeast(1)
        val effects = mutableListOf<WiimoteEffect>()
        var offset = 0

        while (offset < requested) {
            val chunkSize = minOf(16, requested - offset)
            val address = command.address + offset
            val result = registerBank.read(
                extension = state.extension,
                address = address,
                size = chunkSize,
            )

            effects += WiimoteEffect.SendReport(
                memoryEncoder.encodeRead(
                    state = state,
                    address = address,
                    data = if (result.data.isEmpty()) byteArrayOf(0x00) else result.data,
                    error = result.error,
                ),
            )

            if (result.error != 0) break
            offset += chunkSize
        }

        return effects
    }
}
