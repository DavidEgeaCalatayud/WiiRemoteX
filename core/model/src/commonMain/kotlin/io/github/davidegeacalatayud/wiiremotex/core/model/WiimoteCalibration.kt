package io.github.davidegeacalatayud.wiiremotex.core.model

data class AxisCalibration(
    val zeroG: Int,
    val positiveOneG: Int,
) {
    val unitsPerG: Int
        get() = positiveOneG - zeroG
}

data class WiimoteAccelerometerCalibration(
    val x: AxisCalibration,
    val y: AxisCalibration,
    val z: AxisCalibration,
) {
    companion object {
        /**
         * Matches the calibration bytes exposed by WiimoteEeprom at 0x0016.
         */
        val DEFAULT = WiimoteAccelerometerCalibration(
            x = AxisCalibration(zeroG = 521, positiveOneG = 627),
            y = AxisCalibration(zeroG = 521, positiveOneG = 626),
            z = AxisCalibration(zeroG = 521, positiveOneG = 632),
        )
    }
}
