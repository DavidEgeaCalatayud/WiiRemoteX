package io.github.davidegeacalatayud.wiiremotex.platform.sensors

import android.content.Context

object PointerTuning {
    private const val PREFS_NAME = "wiiremotex.pointer_tuning"
    private const val KEY_SENSITIVITY = "sensitivity"

    const val MIN_SENSITIVITY = 0.5f
    const val MAX_SENSITIVITY = 2.0f
    const val DEFAULT_SENSITIVITY = 1.0f

    @Volatile
    private var cachedSensitivity: Float? = null

    fun get(context: Context): Float {
        cachedSensitivity?.let { return it }
        val loaded = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
            .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        cachedSensitivity = loaded
        return loaded
    }

    fun set(context: Context, value: Float): Float {
        val clamped = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        cachedSensitivity = clamped
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SENSITIVITY, clamped)
            .apply()
        return clamped
    }

    fun applyToRange(context: Context, degrees: Float): Float =
        degrees / get(context)
}
