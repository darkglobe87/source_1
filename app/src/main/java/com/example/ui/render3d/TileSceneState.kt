package com.example.ui.render3d

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** One tile's current draw state, rebuilt by Compose every frame and read by the GL thread. */
data class TileDrawInfo(val xWorld: Float, val yWorld: Float, val flipDeg: Float, val char: Char)

/**
 * Thread-safe bridge between Compose (which owns layout/animation state) and the
 * GL render thread (which reads a snapshot once per frame). All fields are
 * plain volatiles rather than a lock: each is only ever read as a whole,
 * independent value by the renderer, so torn reads aren't a correctness risk
 * for a continuously-redrawn visual.
 */
class TileSceneState {
    @Volatile var tiles: List<TileDrawInfo> = emptyList()
    @Volatile var tileSizePx: Float = 1f
    @Volatile var dangerLevel: Float = 0f
    @Volatile var accentColor: FloatArray = floatArrayOf(0f, 0.94f, 1f)
    @Volatile var tiltPitchDeg: Float = 0f
    @Volatile var tiltRollDeg: Float = 0f
}

/**
 * Low-pass-filtered accelerometer tilt, mapped to a small rotation range, for
 * the subtle "holographic marquee" camera parallax. Deliberately modest (a
 * few degrees) so it reads as a reactive surface, not a gimmick.
 */
class TiltSensorListener(
    private val onTilt: (pitchDeg: Float, rollDeg: Float) -> Unit
) : SensorEventListener {
    private var smoothedX = 0f
    private var smoothedY = 0f

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.08f
        smoothedX += alpha * (event.values[0] - smoothedX)
        smoothedY += alpha * (event.values[1] - smoothedY)
        val maxDeg = 6f
        val roll = (-smoothedX / 9.8f * maxDeg).coerceIn(-maxDeg, maxDeg)
        val pitch = (smoothedY / 9.8f * maxDeg).coerceIn(-maxDeg, maxDeg)
        onTilt(pitch, roll)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    companion object {
        fun registerIfAvailable(context: Context, listener: TiltSensorListener): Boolean {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
            val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
            return manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        fun unregister(context: Context, listener: TiltSensorListener) {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            manager.unregisterListener(listener)
        }
    }
}
