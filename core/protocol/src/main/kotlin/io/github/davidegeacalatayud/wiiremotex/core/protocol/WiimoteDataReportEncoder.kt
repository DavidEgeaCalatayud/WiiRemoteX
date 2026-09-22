package io.github.davidegeacalatayud.wiiremotex.core.protocol

import io.github.davidegeacalatayud.wiiremotex.core.model.WiimoteState

class WiimoteDataReportEncoder(
    private val buttonsEncoder: CoreButtonsReportEncoder = CoreButtonsReportEncoder(),
    private val accelerometerCodec: WiiAccelerometerCodec = WiiAccelerometerCodec(),
    private val infraredCodec: InfraredCodec = InfraredCodec(),
    private val extensionCodec: ExtensionCodec = ExtensionCodec(),
) {
    fun encode(state: WiimoteState): HidInputReport =
        when (state.reportMode) {
            0x31 -> withAccelerometer(state, 0x31)
            0x32 -> withExtension(state, reportId = 0x32, extensionBytes = 8)
            0x33 -> withAccelerometerAndExtendedIr(state)
            0x34 -> withExtension(state, reportId = 0x34, extensionBytes = 19)
            0x35 -> withAccelerometerAndExtension(state)
            0x36 -> withBasicIrAndExtension(state)
            0x37 -> withAccelerometerBasicIrAndExtension(state)
            0x3D -> extensionOnly(state)
            else -> buttonsEncoder.encode(state)
        }

    private fun withAccelerometer(
        state: WiimoteState,
        reportId: Int,
    ): HidInputReport {
        val sample = accelerometerCodec.quantize(state.motion)
        val buttons = accelerometerCodec.applyLsbToButtons(
            buttonsEncoder.encode(state).payload,
            sample,
        )

        return HidInputReport(
            reportId = reportId,
            payload = buttons + accelerometerCodec.upperBytes(sample),
        )
    }

    private fun withAccelerometerAndExtendedIr(state: WiimoteState): HidInputReport {
        val base = withAccelerometer(state, 0x33)
        return HidInputReport(
            reportId = 0x33,
            payload = base.payload + infraredCodec.encodeExtended(state.infrared),
        )
    }

    private fun withExtension(
        state: WiimoteState,
        reportId: Int,
        extensionBytes: Int,
    ): HidInputReport {
        val buttons = buttonsEncoder.encode(state).payload
        return HidInputReport(
            reportId = reportId,
            payload = buttons + extensionCodec.encode(state.extension).padded(extensionBytes),
        )
    }

    private fun withAccelerometerAndExtension(state: WiimoteState): HidInputReport {
        val base = withAccelerometer(state, 0x35)
        return HidInputReport(
            reportId = 0x35,
            payload = base.payload + extensionCodec.encode(state.extension).padded(16),
        )
    }

    private fun withBasicIrAndExtension(state: WiimoteState): HidInputReport =
        HidInputReport(
            reportId = 0x36,
            payload =
                buttonsEncoder.encode(state).payload +
                    infraredCodec.encodeBasic(state.infrared) +
                    extensionCodec.encode(state.extension).padded(9),
        )

    private fun withAccelerometerBasicIrAndExtension(state: WiimoteState): HidInputReport {
        val base = withAccelerometer(state, 0x37)
        return HidInputReport(
            reportId = 0x37,
            payload =
                base.payload +
                    infraredCodec.encodeBasic(state.infrared) +
                    extensionCodec.encode(state.extension),
        )
    }

    private fun extensionOnly(state: WiimoteState): HidInputReport =
        HidInputReport(
            reportId = 0x3D,
            payload = extensionCodec.encode(state.extension).padded(21),
        )

    private fun ByteArray.padded(size: Int): ByteArray =
        copyOf(size)
}
