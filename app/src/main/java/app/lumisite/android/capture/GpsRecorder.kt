package app.lumisite.android.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import app.lumisite.core.model.GpsFix
import app.lumisite.core.model.GpsStatusSample
import app.lumisite.core.time.StampFactory

/** Records GNSS (and network) fixes with accuracy. Absence of fixes indoors is logged as status, not hidden. */
class GpsRecorder(
    ctx: Context,
    private val stamps: () -> StampFactory?,
    private val onFix: (GpsFix) -> Unit,
    private val onStatus: (GpsStatusSample) -> Unit,
) : LocationListener {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    @Volatile private var satUsed: Int? = null
    @Volatile private var satVisible: Int? = null
    private var gnssCallback: GnssStatus.Callback? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val f = stamps() ?: return
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!lm.allProviders.contains(p)) { onStatus(GpsStatusSample(f.now(), p, "NOT_PRESENT")); continue }
            val enabled = lm.isProviderEnabled(p)
            onStatus(GpsStatusSample(f.now(), p, if (enabled) "ENABLED" else "DISABLED"))
            try { lm.requestLocationUpdates(p, 1000L, 0f, this, Looper.getMainLooper()) } catch (e: Exception) {
                onStatus(GpsStatusSample(f.now(), p, "ERROR", e.message))
            }
        }
        if (Build.VERSION.SDK_INT >= 24) {
            val cb = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    satVisible = status.satelliteCount
                    satUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
                }
            }
            try { lm.registerGnssStatusCallback(cb, android.os.Handler(Looper.getMainLooper())); gnssCallback = cb } catch (_: Exception) {}
        }
    }

    fun stop() {
        lm.removeUpdates(this)
        gnssCallback?.let { lm.unregisterGnssStatusCallback(it) }; gnssCallback = null
    }

    override fun onLocationChanged(l: Location) {
        val f = stamps() ?: return
        onFix(
            GpsFix(
                t = f.now(sensorNs = l.elapsedRealtimeNanos), provider = l.provider ?: "?",
                lat = l.latitude, lon = l.longitude,
                horizontalAccuracyM = if (l.hasAccuracy()) l.accuracy else null,
                altitudeM = if (l.hasAltitude()) l.altitude else null,
                verticalAccuracyM = if (Build.VERSION.SDK_INT >= 26 && l.hasVerticalAccuracy()) l.verticalAccuracyMeters else null,
                speedMps = if (l.hasSpeed()) l.speed else null,
                bearingDeg = if (l.hasBearing()) l.bearing else null,
                fixUtcMs = l.time,
                satellitesUsed = if (l.provider == LocationManager.GPS_PROVIDER) satUsed else null,
                satellitesVisible = if (l.provider == LocationManager.GPS_PROVIDER) satVisible else null,
            )
        )
    }

    override fun onProviderEnabled(provider: String) { stamps()?.let { onStatus(GpsStatusSample(it.now(), provider, "ENABLED")) } }
    override fun onProviderDisabled(provider: String) { stamps()?.let { onStatus(GpsStatusSample(it.now(), provider, "DISABLED")) } }
    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
