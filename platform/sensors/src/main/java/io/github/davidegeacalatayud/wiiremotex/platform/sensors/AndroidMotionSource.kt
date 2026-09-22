package io.github.davidegeacalatayud.wiiremotex.platform.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.davidegeacalatayud.wiiremotex.core.model.MotionState
import io.github.davidegeacalatayud.wiiremotex.core.model.Orientation
import io.github.davidegeacalatayud.wiiremotex.core.model.Vector3
import kotlin.math.PI

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

    private val rotationVector =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var accelerationG = Vector3()
    private var angularVelocity = Vector3()
    private var orientation = Orientation()
    private var lastDispatchNanos = 0L

    val capabilities = Capabilities(
        accelerometer = accelerometer != null,
        gyroscope = gyroscope != null,
        rotationVector = rotationVector != null,
    )

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelerationG = Vector3(
                    x = event.values[0] / SensorManager.GRAVITY_EARTH,
                    y = event.values[1] / SensorManager.GRAVITY_EARTH,
                    z = event.values[2] / SensorManager.GRAVITY_EARTH,
                )
            }

            Sensor.TYPE_GYROSCOPE -> {
                angularVelocity = Vector3(
                    x = event.values[0].radToDeg(),
                    y = event.values[1].radToDeg(),
                    z = event.values[2].radToDeg(),
                )
            }

            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                val angles = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, angles)

                orientation = Orientation(
                    yawDegrees = angles[0].radToDeg(),
                    pitchDegrees = angles[1].radToDeg(),
                    rollDegrees = angles[2].radToDeg(),
                )
            }
        }

        if (event.timestamp - lastDispatchNanos >= MIN_DISPATCH_INTERVAL_NANOS) {
            lastDispatchNanos = event.timestamp
            listener.onMotionChanged(
                MotionState(
                    accelerationG = accelerationG,
                    angularVelocityDegPerSec = angularVelocity,
                    orientation = orientation,
                ),
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun Float.radToDeg(): Float =
        (this * 180.0 / PI).toFloat()

    data class Capabilities(
        val accelerometer: Boolean,
        val gyroscope: Boolean,
        val rotationVector: Boolean,
    )

    private companion object {
        const val MIN_DISPATCH_INTERVAL_NANOS = 16_000_000L
    }
}
