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
import io.github.davidegeacalatayud.wiiremotex.core.protocol.WiimoteEeprom
import io.github.davidegeacalatayud.wiiremotex.core.protocol.WiimoteRegisterBank
import io.github.davidegeacalatayud.wiiremotex.core.protocol.WiimoteDataReportEncoder
import kotlin.math.abs

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
    private val eeprom: WiimoteEeprom = WiimoteEeprom(),
    private val decoder: HostCommandDecoder = HostCommandDecoder(),
) {
    private var nextInterleavedReportId: Int = 0x3E
    private var nextPassThroughNunchukSample: Boolean = false

    var state: WiimoteState = initialState
        private set

    @Synchronized
    fun setButton(button: WiiButton, pressed: Boolean): SessionResult {
        val buttons = state.pressedButtons.toMutableSet().apply {
            if (pressed) add(button) else remove(button)
        }
        state = state.copy(pressedButtons = buttons)
        return withCurrentDataReportIf(!state.continuousReporting)
    }

    @Synchronized
    fun setMotion(motion: MotionState): SessionResult {
        state = state.copy(
            motion = motion,
            motionPlus = state.motionPlus.copy(
                yawSlow = abs(motion.gyroYaw - MOTION_PLUS_ZERO) < MOTION_PLUS_FAST_THRESHOLD,
                rollSlow = abs(motion.gyroRoll - MOTION_PLUS_ZERO) < MOTION_PLUS_FAST_THRESHOLD,
                pitchSlow = abs(motion.gyroPitch - MOTION_PLUS_ZERO) < MOTION_PLUS_FAST_THRESHOLD,
            ),
            nunchuk = if (state.nunchuk.connected) {
                state.nunchuk.copy(
                    accelerationX = motion.accelerationX,
                    accelerationY = motion.accelerationY,
                    accelerationZ = motion.accelerationZ,
                )
            } else {
                state.nunchuk
            },
        )
        return withCurrentDataReportIf(
            reportModeIncludesMotion(state.reportMode) && !state.continuousReporting,
        )
    }

    @Synchronized
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
        return withCurrentDataReportIf(
            reportModeIncludesIr(state.reportMode) && !state.continuousReporting,
        )
    }

    @Synchronized
    fun setNunchuk(nunchuk: NunchukState): SessionResult {
        state = state.copy(
            nunchuk = nunchuk,
            motionPlus = state.motionPlus.copy(
                extensionConnected = nunchuk.connected,
            ),
        )
        return withCurrentDataReportIf(
            reportModeIncludesExtension(state.reportMode) && !state.continuousReporting,
        )
    }

    @Synchronized
    fun setMotionPlus(motionPlus: MotionPlusState): SessionResult {
        state = state.copy(motionPlus = motionPlus)
        return withCurrentDataReportIf(
            reportModeIncludesExtension(state.reportMode) && !state.continuousReporting,
        )
    }

    @Synchronized
    fun setBatteryLevel(level: Int): WiimoteState {
        state = state.copy(batteryLevel = level.coerceIn(0, 0xFF))
        return state
    }

    @Synchronized
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
                if (command.reportMode == 0x3E || command.reportMode == 0x3F) {
                    nextInterleavedReportId = 0x3E
                }

                state = state.copy(
                    reportMode = command.reportMode,
                    continuousReporting = command.continuous,
                    rumbleEnabled = command.rumbleEnabled,
                )
                listOf(WiimoteEffect.SendReport(encodeCurrentDataReport()))
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

                if (!command.registerSpace) {
                    val success = eeprom.write(
                        address = command.address,
                        data = command.data,
                    )

                    listOf(
                        WiimoteEffect.SendReport(
                            memoryEncoder.encodeAck(
                                state = state,
                                outputReportId = 0x16,
                                error = if (success) 0x00 else 0x08,
                            ),
                        ),
                    )
                } else {
                    val result = registerBank.write(
                        state = state,
                        address = command.address,
                        data = command.data,
                    )

                    if (result.activateMotionPlus) {
                        state = state.copy(
                            motionPlus = state.motionPlus.copy(
                                present = true,
                                active = true,
                                passThroughNunchuk = result.motionPlusMode == 0x05,
                                extensionConnected = state.nunchuk.connected,
                            ),
                        )
                    }

                    if (result.deactivateMotionPlus) {
                        state = state.copy(
                            motionPlus = state.motionPlus.copy(
                                active = false,
                                passThroughNunchuk = false,
                            ),
                        )
                    }

                    buildList {
                        add(
                            WiimoteEffect.SendReport(
                                memoryEncoder.encodeAck(
                                    state = state,
                                    outputReportId = 0x16,
                                    error = if (result.success) 0x00 else 0x08,
                                ),
                            ),
                        )

                        if (result.activateMotionPlus || result.deactivateMotionPlus) {
                            add(
                                WiimoteEffect.SendReport(
                                    statusEncoder.encode(state),
                                ),
                            )
                        }
                    }
                }
            }

            is HostCommand.ReadMemory -> {
                state = state.copy(rumbleEnabled = command.rumbleEnabled)

                val requestedSize = command.size.coerceAtLeast(1)
                var offset = 0
                val reports = mutableListOf<WiimoteEffect>()

                while (offset < requestedSize) {
                    val chunkSize = minOf(16, requestedSize - offset)
                    val address = command.address + offset

                    val data = if (command.registerSpace) {
                        registerBank.read(
                            state = state,
                            address = address,
                            size = chunkSize,
                        )
                    } else {
                        eeprom.read(
                            address = address,
                            size = chunkSize,
                        )
                    }

                    if (data == null || data.isEmpty()) {
                        reports += WiimoteEffect.SendReport(
                            memoryEncoder.encodeRead(
                                state = state,
                                address = address,
                                data = byteArrayOf(0x00),
                                error = 0x07,
                            ),
                        )
                        break
                    }

                    reports += WiimoteEffect.SendReport(
                        memoryEncoder.encodeRead(
                            state = state,
                            address = address,
                            data = data,
                        ),
                    )

                    offset += data.size
                    if (data.size < chunkSize) break
                }

                reports
            }

            is HostCommand.Unknown -> emptyList()
        }

        return SessionResult(state = state, effects = effects)
    }

    @Synchronized
    fun nextContinuousReport(): SessionResult =
        if (state.continuousReporting) {
            SessionResult(
                state = state,
                effects = listOf(
                    WiimoteEffect.SendReport(encodeCurrentDataReport()),
                ),
            )
        } else {
            SessionResult(state = state)
        }

    private fun withCurrentDataReport(): SessionResult =
        SessionResult(
            state = state,
            effects = listOf(
                WiimoteEffect.SendReport(encodeCurrentDataReport()),
            ),
        )

    private fun encodeCurrentDataReport(): HidInputReport {
        if (state.reportMode == 0x3E || state.reportMode == 0x3F) {
            val reportId = nextInterleavedReportId
            nextInterleavedReportId =
                if (nextInterleavedReportId == 0x3E) 0x3F else 0x3E
            return dataEncoder.encodeInterleaved(state, reportId)
        }

        val usePassThroughNunchuk =
            state.motionPlus.active &&
                state.motionPlus.passThroughNunchuk &&
                state.nunchuk.connected &&
                nextPassThroughNunchukSample

        if (
            state.motionPlus.active &&
            state.motionPlus.passThroughNunchuk &&
            state.nunchuk.connected &&
            reportModeIncludesExtension(state.reportMode)
        ) {
            nextPassThroughNunchukSample = !nextPassThroughNunchukSample
        }

        return dataEncoder.encode(
            state = state,
            passThroughNunchukSample = usePassThroughNunchuk,
        )
    }

    private fun withCurrentDataReportIf(condition: Boolean): SessionResult =
        if (condition) {
            withCurrentDataReport()
        } else {
            SessionResult(state = state)
        }

    private fun reportModeIncludesMotion(mode: Int): Boolean =
        mode in setOf(0x31, 0x33, 0x35, 0x37, 0x3E, 0x3F)

    private fun reportModeIncludesIr(mode: Int): Boolean =
        mode in setOf(0x33, 0x36, 0x37, 0x3E, 0x3F)

    private fun reportModeIncludesExtension(mode: Int): Boolean =
        mode in setOf(0x32, 0x34, 0x35, 0x36, 0x37, 0x3D)

    private companion object {
        const val MOTION_PLUS_ZERO = 0x1F7F
        const val MOTION_PLUS_FAST_THRESHOLD = 4_000
    }
}
