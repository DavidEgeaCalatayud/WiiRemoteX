package io.github.davidegeacalatayud.wiiremotex.platform.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import kotlin.math.PI
import kotlin.math.roundToInt

data class OrientationSample(
    val yawRadians: Float,
    val pitchRadians: Float,
    val rollRadians: Float,
)

class AndroidMotionSource(
    context: Context,
    private val listener: Listener,
) : SensorEventListener {

    interface Listener {
        fun onMotionChanged(motion: MotionState)
        fun onOrientationChanged(orientation: OrientationSample) = Unit
    }

    private val sensorManager =
        context.applicationContext.getSystemService(SensorManager::class.java)

    private val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val rotationVector =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var accelerationX = WIIMOTE_ZERO
    private var accelerationY = WIIMOTE_ZERO
    private var accelerationZ = WIIMOTE_ONE_G

    private var gyroYaw = MOTION_PLUS_ZERO
    private var gyroRoll = MOTION_PLUS_ZERO
    private var gyroPitch = MOTION_PLUS_ZERO

    private var gyroBiasX = 0f
    private var gyroBiasY = 0f
    private var gyroBiasZ = 0f

    private var latestGyroX = 0f
    private var latestGyroY = 0f
    private var latestGyroZ = 0f

    private var lastMotionEmissionNanos = 0L
    private var lastOrientationEmissionNanos = 0L

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

        rotationVector?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
            )
        }

        return accelRegistered || gyroRegistered
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun calibrateGyroscope() {
        gyroBiasX = latestGyroX
        gyroBiasY = latestGyroY
        gyroBiasZ = latestGyroZ
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerationX = accelerationToWiimote(event.values[0])
                accelerationY = accelerationToWiimote(-event.values[1])
                accelerationZ = accelerationToWiimote(event.values[2])
            }

            Sensor.TYPE_GYROSCOPE -> {
                latestGyroX = event.values[0]
                latestGyroY = event.values[1]
                latestGyroZ = event.values[2]

                gyroPitch = angularVelocityToMotionPlus(event.values[0] - gyroBiasX)
                gyroRoll = angularVelocityToMotionPlus(-(event.values[1] - gyroBiasY))
                gyroYaw = angularVelocityToMotionPlus(event.values[2] - gyroBiasZ)
            }

            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR,
            -> emitOrientation(event)
        }

        if (
            event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            event.timestamp - lastMotionEmissionNanos >= MOTION_EMISSION_INTERVAL_NS
        ) {
            lastMotionEmissionNanos = event.timestamp
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

    private fun emitOrientation(event: SensorEvent) {
        if (event.timestamp - lastOrientationEmissionNanos < ORIENTATION_EMISSION_INTERVAL_NS) {
            return
        }

        lastOrientationEmissionNanos = event.timestamp

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        listener.onOrientationChanged(
            OrientationSample(
                yawRadians = orientation[0],
                pitchRadians = orientation[1],
                rollRadians = orientation[2],
            ),
        )
    }

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

        const val MOTION_EMISSION_INTERVAL_NS = 20_000_000L
        const val ORIENTATION_EMISSION_INTERVAL_NS = 16_000_000L
    }
}
