package io.github.davidegeacalatayud.wiiremotex.platform.sensors

import android.content.Context
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionCalibrationProfile

class MotionCalibrationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): MotionCalibrationProfile =
        MotionCalibrationProfile(
            gyroBiasXRadPerSec = preferences.getFloat(KEY_GYRO_X, 0f),
            gyroBiasYRadPerSec = preferences.getFloat(KEY_GYRO_Y, 0f),
            gyroBiasZRadPerSec = preferences.getFloat(KEY_GYRO_Z, 0f),
            pointerHorizontalRangeDegrees =
                preferences.getFloat(KEY_POINTER_HORIZONTAL_RANGE, 60f),
            pointerVerticalRangeDegrees =
                preferences.getFloat(KEY_POINTER_VERTICAL_RANGE, 45f),
            pointerSmoothingAlpha =
                preferences.getFloat(KEY_POINTER_SMOOTHING, 0.22f),
            pointerDeadZone =
                preferences.getFloat(KEY_POINTER_DEAD_ZONE, 0.0025f),
        )

    fun save(profile: MotionCalibrationProfile) {
        preferences.edit()
            .putFloat(KEY_GYRO_X, profile.gyroBiasXRadPerSec)
            .putFloat(KEY_GYRO_Y, profile.gyroBiasYRadPerSec)
            .putFloat(KEY_GYRO_Z, profile.gyroBiasZRadPerSec)
            .putFloat(KEY_POINTER_HORIZONTAL_RANGE, profile.pointerHorizontalRangeDegrees)
            .putFloat(KEY_POINTER_VERTICAL_RANGE, profile.pointerVerticalRangeDegrees)
            .putFloat(KEY_POINTER_SMOOTHING, profile.pointerSmoothingAlpha)
            .putFloat(KEY_POINTER_DEAD_ZONE, profile.pointerDeadZone)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "wiiremotex.hardware_calibration"
        const val KEY_GYRO_X = "gyro_bias_x_rad_s"
        const val KEY_GYRO_Y = "gyro_bias_y_rad_s"
        const val KEY_GYRO_Z = "gyro_bias_z_rad_s"
        const val KEY_POINTER_HORIZONTAL_RANGE = "pointer_horizontal_range_degrees"
        const val KEY_POINTER_VERTICAL_RANGE = "pointer_vertical_range_degrees"
        const val KEY_POINTER_SMOOTHING = "pointer_smoothing_alpha"
        const val KEY_POINTER_DEAD_ZONE = "pointer_dead_zone"
    }
}
