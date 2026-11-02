package app.lumisite.android.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import app.lumisite.core.model.ImuSample
import app.lumisite.core.time.StampFactory
import kotlin.math.atan2

/**
 * Records raw inertial/magnetic sensors that exist on the device. Values are stored as delivered by
 * SensorManager; SensorEvent.timestamp is preserved in Stamp.sensorNs.
 */
class SensorRecorder(ctx: Context, private val stamps: () -> StampFactory?, private val sink: (ImuSample) -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var thread: HandlerThread? = null

    /** Latest camera heading (deg from north) from TYPE_ROTATION_VECTOR, or null if unavailable. */
    @Volatile var cameraHeadingDeg: Double? = null; private set
    /** Camera tilt above the horizon (deg, negative = looking down) from TYPE_ROTATION_VECTOR, or null. */
    @Volatile var cameraTiltDeg: Double? = null; private set
    @Volatile var headingAccuracy: Int? = null; private set

    /**
     * D15: accelerometer + gyroscope at [ImuRates.motionHz] (100 Hz by default), magnetometer and rotation vector
     * (heading) at 10 Hz, pressure/light at the normal rate. Gravity, game rotation and the uncalibrated variants are
     * no longer recorded: they are derived by Android from the same sensors (≈ 60 % of the former IMU volume).
     */
    val recordedTypes = listOf(
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_PRESSURE, Sensor.TYPE_LIGHT,
    )
    private val motionPeriodUs = 1_000_000 / ImuRates.motionHz(ctx)

    fun available(): List<Sensor> = recordedTypes.mapNotNull { sm.getDefaultSensor(it) }

    fun start() {
        if (thread != null) return
        val t = HandlerThread("imu").apply { start() }; thread = t
        val h = Handler(t.looper)
        available().forEach { s ->
            val rate = when (s.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE -> motionPeriodUs
                Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR -> 100_000
                else -> SensorManager.SENSOR_DELAY_NORMAL
            }
            sm.registerListener(this, s, rate, h)
        }
    }

    fun stop() {
        sm.unregisterListener(this)
        thread?.quitSafely(); thread = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_ROTATION_VECTOR) updateHeading(e)
        val f = stamps() ?: return
        sink(ImuSample(f.now(sensorNs = e.timestamp), e.sensor.type, e.sensor.name, e.values.toList(), e.accuracy))
    }

    private val rm = FloatArray(9)
    private fun updateHeading(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rm, e.values)
        // Back camera looks along device -Z. World frame of rotation vector: X east, Y north, Z up.
        val east = -rm[2]; val north = -rm[5]
        cameraHeadingDeg = (Math.toDegrees(atan2(east.toDouble(), north.toDouble())) + 360.0) % 360.0
        cameraTiltDeg = app.lumisite.core.position.Tilt.fromRotationMatrix(rm)
        headingAccuracy = e.accuracy
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}

/** D15: rate of the motion sensors (Réglages > Capture), 100 Hz by default. */
object ImuRates {
    private const val FILE = "lumisite_capture"
    val choices = listOf(50, 100, 200)
    fun motionHz(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("imu_hz", 100).takeIf { it in choices } ?: 100
    fun set(ctx: Context, hz: Int) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("imu_hz", hz).apply()
}
