package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.InfraredMode
import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

data class RegisterWriteResult(
    val success: Boolean,
    val activateMotionPlus: Boolean = false,
    val motionPlusMode: Int? = null,
    val deactivateMotionPlus: Boolean = false,
    val motionPlusInitialized: Boolean = false,
    val extensionInitialized: Boolean = false,
    val extensionEncryptionDisabled: Boolean = false,
    val infraredMode: InfraredMode? = null,
    val infraredConfigured: Boolean? = null,
)

class WiimoteRegisterBank(
    private val dataEncoder: WiimoteDataReportEncoder = WiimoteDataReportEncoder(),
) {
    private val extensionRegisters = ByteArray(0x100)
    private val motionPlusRegisters = ByteArray(0x100)
    private val infraredRegisters = ByteArray(0x100)

    private var extensionInitialized = false
    private var extensionEncryptionDisabled = false
    private var motionPlusInitialized = false

    init {
        NUNCHUK_ID.copyInto(extensionRegisters, destinationOffset = 0xFA)
        MOTION_PLUS_INACTIVE_ID.copyInto(motionPlusRegisters, destinationOffset = 0xFA)
        MOTION_PLUS_CALIBRATION.copyInto(motionPlusRegisters, destinationOffset = 0x20)
        motionPlusRegisters[0xF7] = 0x02
    }

    @Synchronized
    fun resetExtension() {
        extensionInitialized = false
        extensionEncryptionDisabled = false
        extensionRegisters.fill(0)
        NUNCHUK_ID.copyInto(extensionRegisters, destinationOffset = 0xFA)
    }

    @Synchronized
    fun resetMotionPlus() {
        motionPlusInitialized = false
        motionPlusRegisters.fill(0)
        MOTION_PLUS_INACTIVE_ID.copyInto(motionPlusRegisters, destinationOffset = 0xFA)
        MOTION_PLUS_CALIBRATION.copyInto(motionPlusRegisters, destinationOffset = 0x20)
        motionPlusRegisters[0xF7] = 0x02
    }

    @Synchronized
    fun read(
        state: WiimoteState,
        address: Int,
        size: Int,
    ): ByteArray? {
        val normalized = canonicalAddress(address)
        val requested = size.coerceIn(1, 16)
        val offset = normalized and 0xFF

        return when {
            normalized in EXTENSION_REGISTER_START..EXTENSION_REGISTER_END -> {
                when {
                    offset in 0x08..0x0D -> {
                        if (!extensionReadable(state)) return null
                        slice(
                            dataEncoder.encodeExtensionPayload(state),
                            offset - 0x08,
                            requested,
                        )
                    }

                    offset in 0xFA..0xFF -> {
                        val id = when {
                            state.motionPlus.active -> motionPlusActiveId(state)
                            state.nunchuk.connected && extensionInitialized -> NUNCHUK_ID
                            else -> return null
                        }
                        slice(id, offset - 0xFA, requested)
                    }

                    else -> slice(extensionRegisters, offset, requested)
                }
            }

            normalized in MOTION_PLUS_REGISTER_START..MOTION_PLUS_REGISTER_END -> {
                if (!state.motionPlus.present || state.motionPlus.active) return null

                when {
                    offset in 0x20..0x3F -> {
                        slice(
                            MOTION_PLUS_CALIBRATION,
                            offset - 0x20,
                            requested,
                        )
                    }

                    offset in 0xFA..0xFF -> {
                        slice(
                            MOTION_PLUS_INACTIVE_ID,
                            offset - 0xFA,
                            requested,
                        )
                    }

                    else -> slice(motionPlusRegisters, offset, requested)
                }
            }

            normalized in IR_REGISTER_START..IR_REGISTER_END -> {
                slice(infraredRegisters, offset, requested)
            }

            else -> null
        }
    }

    @Synchronized
    fun write(
        state: WiimoteState,
        address: Int,
        data: ByteArray,
    ): RegisterWriteResult {
        val normalized = canonicalAddress(address)
        val offset = normalized and 0xFF

        return when {
            normalized in EXTENSION_REGISTER_START..EXTENSION_REGISTER_END -> {
                writeRegisters(extensionRegisters, offset, data)

                when {
                    offset == EXTENSION_INIT_REGISTER &&
                        data.firstOrNull()?.toInt()?.and(0xFF) == 0x55 -> {
                        extensionInitialized = true
                        RegisterWriteResult(
                            success = true,
                            extensionInitialized = true,
                            deactivateMotionPlus = state.motionPlus.active,
                        )
                    }

                    offset == EXTENSION_DISABLE_ENCRYPTION_REGISTER &&
                        data.firstOrNull()?.toInt()?.and(0xFF) == 0x00 -> {
                        extensionEncryptionDisabled = true
                        RegisterWriteResult(
                            success = true,
                            extensionInitialized = extensionInitialized,
                            extensionEncryptionDisabled = true,
                        )
                    }

                    offset == EXTENSION_OLD_INIT_REGISTER &&
                        data.firstOrNull()?.toInt()?.and(0xFF) == 0x00 -> {
                        extensionInitialized = true
                        RegisterWriteResult(
                            success = true,
                            extensionInitialized = true,
                        )
                    }

                    else -> RegisterWriteResult(success = true)
                }
            }

            normalized in MOTION_PLUS_REGISTER_START..MOTION_PLUS_REGISTER_END -> {
                if (!state.motionPlus.present) {
                    return RegisterWriteResult(success = false)
                }

                writeRegisters(motionPlusRegisters, offset, data)

                when {
                    offset == MOTION_PLUS_INIT_REGISTER &&
                        data.firstOrNull()?.toInt()?.and(0xFF) == 0x55 -> {
                        motionPlusInitialized = true
                        motionPlusRegisters[0xF7] = 0x0E
                        RegisterWriteResult(
                            success = true,
                            motionPlusInitialized = true,
                        )
                    }

                    offset == MOTION_PLUS_ACTIVATION_REGISTER && data.isNotEmpty() -> {
                        if (!motionPlusInitialized) {
                            return RegisterWriteResult(success = false)
                        }

                        when (val mode = data[0].toInt() and 0xFF) {
                            0x04, 0x05, 0x07 -> RegisterWriteResult(
                                success = true,
                                activateMotionPlus = true,
                                motionPlusMode = mode,
                                motionPlusInitialized = motionPlusInitialized,
                            )

                            else -> RegisterWriteResult(success = true)
                        }
                    }

                    else -> RegisterWriteResult(success = true)
                }
            }

            normalized in IR_REGISTER_START..IR_REGISTER_END -> {
                writeRegisters(infraredRegisters, offset, data)

                when {
                    offset == IR_MODE_REGISTER && data.isNotEmpty() -> {
                        RegisterWriteResult(
                            success = true,
                            infraredMode = InfraredMode.fromRegisterValue(
                                data[0].toInt() and 0xFF,
                            ),
                        )
                    }

                    offset == IR_CONTROL_REGISTER && data.isNotEmpty() -> {
                        val control = data[0].toInt() and 0xFF
                        val mode = InfraredMode.fromRegisterValue(
                            infraredRegisters[IR_MODE_REGISTER].toInt() and 0xFF,
                        )
                        val configured =
                            control == 0x08 &&
                                state.infrared.pixelClockEnabled &&
                                state.infrared.logicEnabled &&
                                mode != InfraredMode.OFF

                        RegisterWriteResult(
                            success = true,
                            infraredConfigured = configured,
                        )
                    }

                    else -> RegisterWriteResult(success = true)
                }
            }

            else -> RegisterWriteResult(success = false)
        }
    }

    private fun extensionReadable(state: WiimoteState): Boolean =
        when {
            state.motionPlus.active -> true
            state.nunchuk.connected -> extensionInitialized
            else -> false
        }

    private fun motionPlusActiveId(state: WiimoteState): ByteArray =
        byteArrayOf(
            0x00,
            0x00,
            0xA4.toByte(),
            0x20,
            state.motionPlus.activationMode.coerceIn(0, 0xFF).toByte(),
            0x05,
        )

    private fun canonicalAddress(address: Int): Int {
        val normalized = address and 0xFFFFFF
        val high = normalized and 0xFF0000
        val offset = normalized and 0xFF

        return when (high) {
            0xA40000, 0xA50000 -> EXTENSION_REGISTER_START or offset
            0xA60000, 0xA70000 -> MOTION_PLUS_REGISTER_START or offset
            0xB00000, 0xB10000 -> IR_REGISTER_START or offset
            else -> normalized
        }
    }

    private fun writeRegisters(
        target: ByteArray,
        offset: Int,
        data: ByteArray,
    ) {
        if (offset !in target.indices) return
        val count = minOf(data.size, target.size - offset)
        data.copyInto(
            destination = target,
            destinationOffset = offset,
            startIndex = 0,
            endIndex = count,
        )
    }

    private fun slice(
        source: ByteArray,
        offset: Int,
        size: Int,
    ): ByteArray {
        if (offset !in source.indices) return ByteArray(size)
        val end = (offset + size).coerceAtMost(source.size)
        return source.copyOfRange(offset, end)
    }

    private companion object {
        const val EXTENSION_REGISTER_START = 0xA40000
        const val EXTENSION_REGISTER_END = 0xA400FF
        const val EXTENSION_INIT_REGISTER = 0xF0
        const val EXTENSION_DISABLE_ENCRYPTION_REGISTER = 0xFB
        const val EXTENSION_OLD_INIT_REGISTER = 0x40

        const val MOTION_PLUS_REGISTER_START = 0xA60000
        const val MOTION_PLUS_REGISTER_END = 0xA600FF
        const val MOTION_PLUS_INIT_REGISTER = 0xF0
        const val MOTION_PLUS_ACTIVATION_REGISTER = 0xFE

        const val IR_REGISTER_START = 0xB00000
        const val IR_REGISTER_END = 0xB00033
        const val IR_CONTROL_REGISTER = 0x30
        const val IR_MODE_REGISTER = 0x33

        val MOTION_PLUS_CALIBRATION = byteArrayOf(
            0x78, 0xD9.toByte(), 0x78, 0x38, 0x77, 0x9D.toByte(), 0x2F, 0x0C,
            0xCF.toByte(), 0xF0.toByte(), 0x31, 0xAD.toByte(), 0xC8.toByte(), 0x0B, 0x5E, 0x39,
            0x6F, 0x81.toByte(), 0x7B, 0x89.toByte(), 0x78, 0x51, 0x33, 0x60,
            0xC9.toByte(), 0xF5.toByte(), 0x37, 0xC1.toByte(), 0x2D, 0xE9.toByte(), 0x15, 0x8D.toByte(),
        )

        val NUNCHUK_ID = byteArrayOf(
            0x00, 0x00, 0xA4.toByte(), 0x20, 0x00, 0x00,
        )

        val MOTION_PLUS_INACTIVE_ID = byteArrayOf(
            0x00, 0x00, 0xA6.toByte(), 0x20, 0x00, 0x05,
        )
    }
}
