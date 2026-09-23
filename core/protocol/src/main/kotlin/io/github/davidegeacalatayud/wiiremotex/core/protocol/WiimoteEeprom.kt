package io.github.davidegeacalatayud.wiiremotex.core.protocol

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class WiimoteEeprom {
    private val lock = SynchronizedObject()
    private val memory = ByteArray(SIZE)

    init {
        DEFAULT_CALIBRATION.copyInto(memory, destinationOffset = 0x0000)
        DEFAULT_TRAILER.copyInto(memory, destinationOffset = 0x16D0)
    }

    fun read(
        address: Int,
        size: Int,
    ): ByteArray? = synchronized(lock) {
        val offset = address and 0xFFFF
        if (offset !in 0 until SIZE) return null
        if (size <= 0) return byteArrayOf()

        val end = (offset + size).coerceAtMost(SIZE)
        return memory.copyOfRange(offset, end)
    }

    fun write(
        address: Int,
        data: ByteArray,
    ): Boolean = synchronized(lock) {
        val offset = address and 0xFFFF
        if (offset !in 0 until SIZE) return false
        if (offset + data.size > SIZE) return false

        data.copyInto(
            destination = memory,
            destinationOffset = offset,
        )
        return true
    }

    private companion object {
        const val SIZE = 0x1700

        val DEFAULT_CALIBRATION = byteArrayOf(
            0xA1.toByte(), 0xAA.toByte(), 0x8B.toByte(), 0x99.toByte(),
            0xAE.toByte(), 0x9E.toByte(), 0x78, 0x30, 0xA7.toByte(), 0x74, 0xD3.toByte(),
            0xA1.toByte(), 0xAA.toByte(), 0x8B.toByte(), 0x99.toByte(),
            0xAE.toByte(), 0x9E.toByte(), 0x78, 0x30, 0xA7.toByte(), 0x74, 0xD3.toByte(),
            0x82.toByte(), 0x82.toByte(), 0x82.toByte(), 0x15,
            0x9C.toByte(), 0x9C.toByte(), 0x9E.toByte(), 0x38, 0x40, 0x3E,
            0x82.toByte(), 0x82.toByte(), 0x82.toByte(), 0x15,
            0x9C.toByte(), 0x9C.toByte(), 0x9E.toByte(), 0x38, 0x40, 0x3E,
        )

        val DEFAULT_TRAILER = byteArrayOf(
            0x00, 0x00, 0x00, 0xFF.toByte(), 0x11, 0xEE.toByte(), 0x00, 0x00,
            0x33, 0xCC.toByte(), 0x44, 0xBB.toByte(), 0x00, 0x00, 0x66, 0x99.toByte(),
            0x77, 0x88.toByte(), 0x00, 0x00, 0x2B, 0x01, 0xE8.toByte(), 0x13,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
    }
}
