package io.github.davidegeacalatayud.wiiremotex.platform.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import kotlin.math.PI
import kotlin.math.roundToInt

class AndroidMotionSource(
    context: Context,
    private val listener: Listener,
) : SensorEventListener {

    fun interface Listener {
        fun onMotionChanged(motion: MotionState)
    }

    private val sensorManager =
        context.applicationContext.getSystemService(SensorManager::class.java)

    private val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var accelerationX = WIIMOTE_ZERO
    private var accelerationY = WIIMOTE_ZERO
    private var accelerationZ = WIIMOTE_ONE_G

    private var gyroYaw = MOTION_PLUS_ZERO
    private var gyroRoll = MOTION_PLUS_ZERO
    private var gyroPitch = MOTION_PLUS_ZERO

    private var lastEmissionNanos = 0L

    fun start(): Boolean {
        val accelRegistered = accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
            )
        } ?: false

        val gyroRegistered = gyroscope?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
            )
        } ?: false

        return accelRegistered || gyroRegistered
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Android reports m/s². A real Wiimote is roughly 512 at 0G
                // and ~640 at +1G, giving ~128 raw units/G.
                accelerationX = accelerationToWiimote(event.values[0])
                accelerationY = accelerationToWiimote(-event.values[1])
                accelerationZ = accelerationToWiimote(event.values[2])
            }

            Sensor.TYPE_GYROSCOPE -> {
                // Android reports rad/s. MotionPlus slow mode is about
                // 13.768 raw units per degree/s around its ~0x1F7F center.
                gyroPitch = angularVelocityToMotionPlus(event.values[0])
                gyroRoll = angularVelocityToMotionPlus(-event.values[1])
                gyroYaw = angularVelocityToMotionPlus(event.values[2])
            }
        }

        if (event.timestamp - lastEmissionNanos >= EMISSION_INTERVAL_NS) {
            lastEmissionNanos = event.timestamp
            listener.onMotionChanged(
                MotionState(
                    accelerationX = accelerationX,
                    accelerationY = accelerationY,
                    accelerationZ = accelerationZ,
                    gyroYaw = gyroYaw,
                    gyroRoll = gyroRoll,
                    gyroPitch = gyroPitch,
                ),
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun accelerationToWiimote(valueMs2: Float): Int {
        val g = valueMs2 / SensorManager.GRAVITY_EARTH
        return (WIIMOTE_ZERO + g * WIIMOTE_UNITS_PER_G)
            .roundToInt()
            .coerceIn(0, 1023)
    }

    private fun angularVelocityToMotionPlus(valueRadS: Float): Int {
        val degreesPerSecond = valueRadS * 180.0 / PI
        return (MOTION_PLUS_ZERO + degreesPerSecond * MOTION_PLUS_UNITS_PER_DEGREE)
            .roundToInt()
            .coerceIn(0, 0x3FFF)
    }

    private companion object {
        const val WIIMOTE_ZERO = 512
        const val WIIMOTE_ONE_G = 640
        const val WIIMOTE_UNITS_PER_G = 128.0

        const val MOTION_PLUS_ZERO = 0x1F7F
        const val MOTION_PLUS_UNITS_PER_DEGREE = 13.768

        const val EMISSION_INTERVAL_NS = 20_000_000L // 50 Hz
    }
}
