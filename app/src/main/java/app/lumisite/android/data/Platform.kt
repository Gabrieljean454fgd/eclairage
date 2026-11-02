package app.lumisite.android.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import app.lumisite.android.BuildConfig
import app.lumisite.core.model.Device
import app.lumisite.core.model.SensorDescriptor
import app.lumisite.core.time.Clock
import java.util.UUID

object AndroidClock : Clock {
    override fun utcEpochMs(): Long = System.currentTimeMillis()
    /** Monotonic, includes deep sleep, same base as SensorEvent/Location elapsedRealtimeNanos on most devices. */
    override fun monotonicNs(): Long = SystemClock.elapsedRealtimeNanos()
}

object Ids {
    fun new(prefix: String) = "$prefix-" + UUID.randomUUID().toString().substring(0, 13)
}

object DeviceInfo {
    /** Stable per-install identifier (not a hardware identifier: no personal data). */
    fun deviceId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("lumisite", Context.MODE_PRIVATE)
        return prefs.getString("deviceId", null) ?: ("dev-" + UUID.randomUUID().toString().substring(0, 8)).also {
            prefs.edit().putString("deviceId", it).apply()
        }
    }

    fun describe(ctx: Context, arSupport: String): Device {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL).map {
            SensorDescriptor(it.type, it.stringType, it.name, it.vendor, it.resolution, it.maximumRange, it.minDelay)
        }
        return Device(
            id = deviceId(ctx), manufacturer = Build.MANUFACTURER, model = Build.MODEL,
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = BuildConfig.VERSION_NAME, arSupport = arSupport, sensors = sensors,
        )
    }
}

/** Stage 1.7: identity of the current phone boot, so that monotonic clocks of two boots are never compared. */
object BootInfo {
    fun bootId(): String? = runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    fun bootCount(ctx: Context): Int? = runCatching {
        android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
    }.getOrNull()
}
