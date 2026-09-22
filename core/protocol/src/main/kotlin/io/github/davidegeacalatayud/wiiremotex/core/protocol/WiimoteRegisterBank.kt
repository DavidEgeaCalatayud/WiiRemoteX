package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

data class RegisterWriteResult(
    val success: Boolean,
    val activateMotionPlus: Boolean = false,
    val motionPlusMode: Int? = null,
    val deactivateMotionPlus: Boolean = false,
)

class WiimoteRegisterBank(
    private val dataEncoder: WiimoteDataReportEncoder = WiimoteDataReportEncoder(),
) {
    fun read(
        state: WiimoteState,
        address: Int,
        size: Int,
    ): ByteArray? {
        val normalized = address and 0xFFFFFF
        val requested = size.coerceIn(1, 16)

        return when {
            normalized in EXTENSION_DATA_START..EXTENSION_DATA_END -> {
                slice(
                    dataEncoder.encodeExtensionPayload(state),
                    normalized - EXTENSION_DATA_START,
                    requested,
                )
            }

            normalized in NUNCHUK_ID_START..NUNCHUK_ID_END -> {
                val id = when {
                    state.motionPlus.active -> MOTION_PLUS_ACTIVE_ID
                    state.nunchuk.connected -> NUNCHUK_ID
                    else -> return null
                }
                slice(id, normalized - NUNCHUK_ID_START, requested)
            }

            normalized in MOTION_PLUS_CALIBRATION_START..MOTION_PLUS_CALIBRATION_END -> {
                if (!state.motionPlus.present) return null
                slice(
                    MOTION_PLUS_CALIBRATION,
                    normalized - MOTION_PLUS_CALIBRATION_START,
                    requested,
                )
            }

            normalized in MOTION_PLUS_ID_START..MOTION_PLUS_ID_END -> {
                if (!state.motionPlus.present || state.motionPlus.active) return null
                slice(
                    MOTION_PLUS_INACTIVE_ID,
                    normalized - MOTION_PLUS_ID_START,
                    requested,
                )
            }

            normalized in IR_REGISTER_START..IR_REGISTER_END -> {
                ByteArray(requested)
            }

            else -> null
        }
    }

    fun write(
        state: WiimoteState,
        address: Int,
        data: ByteArray,
    ): RegisterWriteResult {
        val normalized = address and 0xFFFFFF

        if (normalized == MOTION_PLUS_ACTIVATION_REGISTER && data.isNotEmpty()) {
            return when (data[0].toInt() and 0xFF) {
                0x04, 0x05, 0x07 -> RegisterWriteResult(
                    success = true,
                    activateMotionPlus = true,
                    motionPlusMode = data[0].toInt() and 0xFF,
                )

                else -> RegisterWriteResult(success = true)
            }
        }

        if (normalized == NUNCHUK_INIT_REGISTER && data.firstOrNull()?.toInt()?.and(0xFF) == 0x55) {
            return RegisterWriteResult(
                success = true,
                deactivateMotionPlus = state.motionPlus.active,
            )
        }

        if (
            normalized in EXTENSION_REGISTER_START..EXTENSION_REGISTER_END ||
            normalized in MOTION_PLUS_REGISTER_START..MOTION_PLUS_REGISTER_END ||
            normalized in IR_REGISTER_START..IR_REGISTER_END
        ) {
            return RegisterWriteResult(success = true)
        }

        return RegisterWriteResult(success = false)
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
        const val EXTENSION_DATA_START = 0xA40008
        const val EXTENSION_DATA_END = 0xA4000D

        const val NUNCHUK_ID_START = 0xA400FA
        const val NUNCHUK_ID_END = 0xA400FF
        const val NUNCHUK_INIT_REGISTER = 0xA400F0

        const val MOTION_PLUS_REGISTER_START = 0xA60000
        const val MOTION_PLUS_REGISTER_END = 0xA600FF
        const val MOTION_PLUS_CALIBRATION_START = 0xA60020
        const val MOTION_PLUS_CALIBRATION_END = 0xA6003F
        const val MOTION_PLUS_ID_START = 0xA600FA
        const val MOTION_PLUS_ID_END = 0xA600FF
        const val MOTION_PLUS_ACTIVATION_REGISTER = 0xA600FE

        const val IR_REGISTER_START = 0xB00000
        const val IR_REGISTER_END = 0xB00033

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

        val MOTION_PLUS_ACTIVE_ID = byteArrayOf(
            0x00, 0x00, 0xA4.toByte(), 0x20, 0x04, 0x05,
        )
    }
}
