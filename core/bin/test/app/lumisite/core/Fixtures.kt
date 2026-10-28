package app.lumisite.core

import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.model.Device
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.SessionHeader
import app.lumisite.core.model.Site
import app.lumisite.core.model.TrackingState
import app.lumisite.core.time.Stamp
import app.lumisite.core.time.TimeBase
import app.lumisite.core.geo.CrsInfo
import kotlin.math.cos
import kotlin.math.sin

object Fixtures {
    val origin = LatLon(48.8566, 2.3522)
    val timeBase = TimeBase("tb-1", "dev-1", TimeBase.ANDROID_ELAPSED_REALTIME, 1_700_000_000_000, 5_000_000_000)

    fun stamp(monoNs: Long) = Stamp(timeBase.monotonicToUtcMs(monoNs).toLong(), monoNs, timeBase.id)

    /** Pose at plane position (x, planeY) with camera forward rotated by yaw (rad, CCW, 0 = looking -Z). */
    fun pose(monoNs: Long, x: Double, planeY: Double, yaw: Double = 0.0, state: TrackingState = TrackingState.TRACKING) =
        PoseSample(stamp(monoNs), listOf(x, 1.4, -planeY), listOf(0.0, sin(yaw / 2), 0.0, cos(yaw / 2)), state)

    val site = Site(
        id = "site-1", name = "Test", origin = origin, originSource = "COORDINATES", createdUtcMs = 1_700_000_000_000,
        crs = listOf(LocalTangentPlane(origin).crsInfo()),
    )

    val header = SessionHeader(
        id = "sess-1", siteId = site.id, name = "S1", createdUtcMs = 1_700_000_000_000,
        device = Device("dev-1", "ACME", "Tab", "14", "0.1.0", "SUPPORTED"),
        timeBases = listOf(timeBase), localFrame = CrsInfo("AR_SESSION", "ARCORE_WORLD", "y up, metres"),
    )
}
