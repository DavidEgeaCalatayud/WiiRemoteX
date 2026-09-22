package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.ExtensionState
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionPlusState

data class RegisterReadResult(
    val data: ByteArray,
    val error: Int = 0,
)

data class RegisterWriteResult(
    val extension: ExtensionState,
    val error: Int = 0,
)

class ExtensionRegisterBank(
    private val extensionCodec: ExtensionCodec = ExtensionCodec(),
) {
    fun read(
        extension: ExtensionState,
        address: Int,
        size: Int,
    ): RegisterReadResult {
        if (size <= 0) {
            return RegisterReadResult(byteArrayOf(), error = ERROR_INVALID_ADDRESS)
        }

        val output = ByteArray(size.coerceAtMost(16))
        for (index in output.indices) {
            val result = readByte(extension, address + index)
            if (result == null) {
                return RegisterReadResult(
                    data = ByteArray(output.size),
                    error = errorForAddress(extension, address + index),
                )
            }
            output[index] = result
        }

        return RegisterReadResult(output)
    }

    fun write(
        extension: ExtensionState,
        address: Int,
        data: ByteArray,
    ): RegisterWriteResult {
        if (extension is ExtensionState.MotionPlus && data.isNotEmpty()) {
            if (address == MOTION_PLUS_CONTROL_F0 && (data[0].toInt() and 0xFF) == 0x55) {
                return RegisterWriteResult(extension)
            }

            if (address == MOTION_PLUS_ACTIVATE_FE) {
                val mode = data[0].toInt() and 0xFF
                if (mode == 0x04 || mode == 0x05 || mode == 0x07) {
                    return RegisterWriteResult(
                        extension.copy(
                            value = extension.value.copy(active = true),
                        ),
                    )
                }
            }

            if (address == EXTENSION_CONTROL_F0 && (data[0].toInt() and 0xFF) == 0x55) {
                return RegisterWriteResult(
                    extension.copy(
                        value = extension.value.copy(active = false),
                    ),
                )
            }
        }

        if (extension is ExtensionState.Nunchuk) {
            if (
                address == EXTENSION_CONTROL_F0 ||
                address == EXTENSION_CONTROL_FB ||
                address == EXTENSION_OLD_INIT
            ) {
                return RegisterWriteResult(extension)
            }
        }

        if (address in IR_REGISTER_START..IR_REGISTER_END) {
            return RegisterWriteResult(extension)
        }

        return if (address in EXTENSION_REGISTER_START..EXTENSION_REGISTER_END ||
            address in MOTION_PLUS_REGISTER_START..MOTION_PLUS_REGISTER_END
        ) {
            RegisterWriteResult(extension)
        } else {
            RegisterWriteResult(extension, error = ERROR_INVALID_ADDRESS)
        }
    }

    private fun readByte(
        extension: ExtensionState,
        address: Int,
    ): Byte? =
        when (extension) {
            ExtensionState.None -> {
                if (address in IR_REGISTER_START..IR_REGISTER_END) 0x00 else null
            }

            is ExtensionState.Nunchuk -> readNunchukByte(extension, address)

            is ExtensionState.MotionPlus -> readMotionPlusByte(extension.value, address)
        }

    private fun readNunchukByte(
        extension: ExtensionState.Nunchuk,
        address: Int,
    ): Byte? {
        if (address in IR_REGISTER_START..IR_REGISTER_END) return 0x00

        val offset = address - EXTENSION_REGISTER_START
        return when {
            offset in 0x08..0x0D ->
                extensionCodec.encodeNunchuk(extension.value)[offset - 0x08]

            offset in 0x20..0x2F ->
                NUNCHUK_CALIBRATION[offset - 0x20]

            offset in 0xFA..0xFF ->
                NUNCHUK_ID[offset - 0xFA]

            offset in 0x00..0xFF -> 0x00
            else -> null
        }
    }

    private fun readMotionPlusByte(
        state: MotionPlusState,
        address: Int,
    ): Byte? {
        if (address in IR_REGISTER_START..IR_REGISTER_END) return 0x00

        if (state.active) {
            val a4Offset = address - EXTENSION_REGISTER_START
            if (a4Offset in 0x08..0x0D) {
                return extensionCodec.encodeMotionPlus(state)[a4Offset - 0x08]
            }
            if (a4Offset in 0xFA..0xFF) {
                return MOTION_PLUS_ACTIVE_ID[a4Offset - 0xFA]
            }
        }

        val a6Offset = address - MOTION_PLUS_REGISTER_START
        return when {
            a6Offset in 0x20..0x3F ->
                MOTION_PLUS_CALIBRATION[a6Offset - 0x20]

            a6Offset in 0xFA..0xFF ->
                if (state.active) {
                    MOTION_PLUS_INACTIVE_AFTER_USE_ID[a6Offset - 0xFA]
                } else {
                    MOTION_PLUS_INACTIVE_ID[a6Offset - 0xFA]
                }

            a6Offset in 0x00..0xFF -> 0x00
            else -> {
                val a4Offset = address - EXTENSION_REGISTER_START
                if (state.active && a4Offset in 0x00..0xFF) 0x00 else null
            }
        }
    }

    private fun errorForAddress(
        extension: ExtensionState,
        address: Int,
    ): Int =
        when {
            extension is ExtensionState.None &&
                (
                    address in EXTENSION_REGISTER_START..EXTENSION_REGISTER_END ||
                        address in MOTION_PLUS_REGISTER_START..MOTION_PLUS_REGISTER_END
                    ) -> ERROR_NO_EXTENSION

            extension is ExtensionState.Nunchuk &&
                address in MOTION_PLUS_REGISTER_START..MOTION_PLUS_REGISTER_END ->
                ERROR_NO_EXTENSION

            else -> ERROR_INVALID_ADDRESS
        }

    companion object {
        const val ERROR_NO_EXTENSION = 0x07
        const val ERROR_INVALID_ADDRESS = 0x08

        const val EXTENSION_REGISTER_START = 0xA40000
        const val EXTENSION_REGISTER_END = 0xA400FF
        const val MOTION_PLUS_REGISTER_START = 0xA60000
        const val MOTION_PLUS_REGISTER_END = 0xA600FF
        const val IR_REGISTER_START = 0xB00000
        const val IR_REGISTER_END = 0xB00033

        const val EXTENSION_OLD_INIT = 0xA40040
        const val EXTENSION_CONTROL_F0 = 0xA400F0
        const val EXTENSION_CONTROL_FB = 0xA400FB
        const val MOTION_PLUS_CONTROL_F0 = 0xA600F0
        const val MOTION_PLUS_ACTIVATE_FE = 0xA600FE

        val NUNCHUK_ID = byteArrayOf(
            0x00,
            0x00,
            0xA4.toByte(),
            0x20,
            0x00,
            0x00,
        )

        val MOTION_PLUS_INACTIVE_ID = byteArrayOf(
            0x00,
            0x00,
            0xA6.toByte(),
            0x20,
            0x00,
            0x05,
        )

        val MOTION_PLUS_ACTIVE_ID = byteArrayOf(
            0x00,
            0x00,
            0xA4.toByte(),
            0x20,
            0x04,
            0x05,
        )

        val MOTION_PLUS_INACTIVE_AFTER_USE_ID = byteArrayOf(
            0x00,
            0x00,
            0xA6.toByte(),
            0x20,
            0x04,
            0x05,
        )

        private val NUNCHUK_CALIBRATION = byteArrayOf(
            0xFF.toByte(), 0x00, 0x80.toByte(),
            0xFF.toByte(), 0x00, 0x80.toByte(),
            0x00, 0x00,
            0xFF.toByte(), 0x00, 0x80.toByte(),
            0xFF.toByte(), 0x00, 0x80.toByte(),
            0x00, 0x00,
        )

        private val MOTION_PLUS_CALIBRATION = ByteArray(32).apply {
            // Plausible neutral/default block. Games should still calibrate zero at runtime.
            this[0] = 0x1F
            this[1] = 0x7F
            this[2] = 0x1F
            this[3] = 0x7F
            this[4] = 0x1F
            this[5] = 0x7F
            this[16] = 0x1F
            this[17] = 0x7F
            this[18] = 0x1F
            this[19] = 0x7F
            this[20] = 0x1F
            this[21] = 0x7F
        }
    }
}
