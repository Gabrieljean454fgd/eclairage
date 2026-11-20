package app.lumisite.android.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.lumisite.android.BuildConfig
import app.lumisite.core.geo.LatLon
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

/** Position actuelle pour les adresses récentes et la recherche en ligne : dernier correctif connu, null sans permission. */
object LastKnownPosition {
    @SuppressLint("MissingPermission")
    fun get(ctx: Context): LatLon? {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { p -> runCatching { lm.allProviders.contains(p) }.getOrDefault(false) }
            .firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let { LatLon(it.latitude, it.longitude) } }
    }
}

/** Stage 1.7: identity of the current phone boot, so that monotonic clocks of two boots are never compared. */
object BootInfo {
    fun bootId(): String? = runCatching { java.io.File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    fun bootCount(ctx: Context): Int? = runCatching {
        android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT)
    }.getOrNull()
}
