package io.github.davidegeacalatayud.wiiremotex.core.session

import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredPoint
import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import io.github.davidegeacalatayud.wiiremotex.core.model.NunchukState
import io.github.davidegeacalatayud.wiiremotex.core.model.WiiButton
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HidInputReport
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HostCommand
import io.github.davidegeacalatayud.wiiremotex.core.protocol.HostCommandDecoder
import io.github.davidegeacalatayud.wiiremotex.core.protocol.MemoryReportEncoder
import io.github.davidegeacalatayud.wiiremotex.core.protocol.StatusReportEncoder
import io.github.davidegeacalatayud.wiiremotex.core.protocol.WiimoteRegisterBank
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
    private val registerBank: WiimoteRegisterBank = WiimoteRegisterBank(),
    private val decoder: HostCommandDecoder = HostCommandDecoder(),
) {
    var state: WiimoteState = initialState
        private set

    fun setButton(button: WiiButton, pressed: Boolean): SessionResult {
        val buttons = state.pressedButtons.toMutableSet().apply {
            if (pressed) add(button) else remove(button)
        }
        state = state.copy(pressedButtons = buttons)
        return withCurrentDataReport()
    }

    fun setMotion(motion: MotionState): SessionResult {
        state = state.copy(motion = motion)
        return withCurrentDataReport()
    }

    fun setInfrared(
        enabled: Boolean = state.infrared.enabled,
        points: List<InfraredPoint> = state.infrared.points,
    ): SessionResult {
        state = state.copy(
            infrared = InfraredState(
                enabled = enabled,
                points = points,
            ),
        )
        return withCurrentDataReport()
    }

    fun setNunchuk(nunchuk: NunchukState): SessionResult {
        state = state.copy(nunchuk = nunchuk)
        return withCurrentDataReport()
    }

    fun setMotionPlus(motionPlus: MotionPlusState): SessionResult {
        state = state.copy(motionPlus = motionPlus)
        return withCurrentDataReport()
    }

    fun setBatteryLevel(level: Int): WiimoteState {
        state = state.copy(batteryLevel = level.coerceIn(0, 0xFF))
        return state
    }

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
                listOf(WiimoteEffect.SendReport(dataEncoder.encode(state)))
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
                                outputReportId = command.outputReportId,
                            ),
                        ),
                    )
                } else {
                    emptyList()
                }
            }

            is HostCommand.StatusRequest -> {
                state = state.copy(rumbleEnabled = command.rumbleEnabled)
                listOf(WiimoteEffect.SendReport(statusEncoder.encode(state)))
            }

            is HostCommand.WriteMemory -> {
                state = state.copy(rumbleEnabled = command.rumbleEnabled)
                val result = registerBank.write(
                    state = state,
                    address = command.address,
                    data = command.data,
                )

                if (result.activateMotionPlus) {
                    state = state.copy(
                        motionPlus = state.motionPlus.copy(
                            enabled = true,
                            extensionConnected = state.nunchuk.connected,
                        ),
                    )
                }

                if (result.deactivateMotionPlus) {
                    state = state.copy(
                        motionPlus = state.motionPlus.copy(enabled = false),
                    )
                }

                listOf(
                    WiimoteEffect.SendReport(
                        memoryEncoder.encodeAck(
                            state = state,
                            outputReportId = 0x16,
                            error = if (result.success) 0x00 else 0x08,
                        ),
                    ),
                )
            }

            is HostCommand.ReadMemory -> {
                state = state.copy(rumbleEnabled = command.rumbleEnabled)
                val data = registerBank.read(
                    state = state,
                    address = command.address,
                    size = command.size,
                )

                if (data == null) {
                    listOf(
                        WiimoteEffect.SendReport(
                            memoryEncoder.encodeRead(
                                state = state,
                                address = command.address,
                                data = byteArrayOf(0x00),
                                error = 0x07,
                            ),
                        ),
                    )
                } else {
                    listOf(
                        WiimoteEffect.SendReport(
                            memoryEncoder.encodeRead(
                                state = state,
                                address = command.address,
                                data = data,
                            ),
                        ),
                    )
                }
            }

            is HostCommand.Unknown -> emptyList()
        }

        return SessionResult(state = state, effects = effects)
    }

    private fun withCurrentDataReport(): SessionResult =
        SessionResult(
            state = state,
            effects = listOf(
                WiimoteEffect.SendReport(dataEncoder.encode(state)),
            ),
        )
}
