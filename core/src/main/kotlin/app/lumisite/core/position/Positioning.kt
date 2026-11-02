package app.lumisite.core.position

import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One calibration or relocalization point (decision D3): [local] = ARCore plane position (x, -z, metres) when the user
 * confirmed the point, [target] = the point of the plan (site ENU, metres). [rotationRad] only for a first point with a heading.
 */
data class ControlPoint(
    val id: String,
    val tNs: Long,
    val local: Vec2,
    val target: Vec2,
    val relocalization: Boolean = false,
    val rotationRad: Double? = null,
)

@Serializable
data class SolverSettings(
    /** D4: a point is rejected when its residual > max(k·σ, minRejectM). */
    val sigmaFactor: Double = 2.0,
    val minRejectM: Double = 1.0,
    val maxIterations: Int = 3,
    /** D5: weight of a point = exp(−age / tau). */
    val tauNs: Long = 5L * 60 * 1_000_000_000,
    /** Below this spread the rotation is not estimated from the points (kept from the heading / previous transform). */
    val minSpreadForRotationM: Double = 2.0,
)

data class Solution(
    val transform: Similarity2D,
    /** Residual (m) of every point, rejected ones included. */
    val residualsM: Map<String, Double>,
    val rejected: Set<String>,
    val weights: Map<String, Double>,
    val rmsM: Double,
    /** "ONE_POINT_HEADING", "LEAST_SQUARES", "ROTATION_KEPT" (points too close to estimate a rotation). */
    val method: String,
    /** D2/D5: the newest relocalization, mapped exactly onto its plan point. */
    val anchorId: String?,
)

/**
 * D3–D5: direct 2D rigid transform (rotation + translation, scale fixed to 1) by weighted least squares
 * (Helmert / Umeyama without scale), no library. Each relocalization starts a new segment: the transform of that segment is
 * solved at its time with older points weighted by exp(−age / 5 min), and the newest relocalization is honoured exactly
 * (« Je suis ici ») so that a relocalization always moves the position by the expected offset.
 */
object CalibrationSolver {
    fun weight(ageNs: Long, s: SolverSettings = SolverSettings()): Double = exp(-max(0L, ageNs).toDouble() / s.tauNs)

    /**
     * @param nowNs solving time (start of the segment): ages are measured from it
     * @param fallbackRotationRad rotation used when the points cannot give one (single point without heading, too close points)
     */
    fun solve(points: List<ControlPoint>, nowNs: Long, fallbackRotationRad: Double? = null, s: SolverSettings = SolverSettings()): Solution? {
        if (points.isEmpty()) return null
        val w = points.associate { it.id to weight(nowNs - it.tNs, s) }
        val anchor = points.filter { it.relocalization }.maxByOrNull { it.tNs }
        val firstRotation = points.firstNotNullOfOrNull { it.rotationRad } ?: fallbackRotationRad
        val rejected = LinkedHashSet<String>()
        var kept = points
        var fit = fit(kept, w, anchor, firstRotation, s) ?: return null
        var iter = 0
        while (iter < s.maxIterations && kept.size > 2) {
            iter++
            val res = kept.associate { it.id to residual(fit.first, it) }
            val cand = kept.filter { it.id != anchor?.id }.maxByOrNull { res.getValue(it.id) } ?: break
            val others = kept.filter { it.id != cand.id }.map { res.getValue(it.id) }
            val sigma = sqrt(others.sumOf { it * it } / others.size)
            if (res.getValue(cand.id) <= max(s.sigmaFactor * sigma, s.minRejectM)) break
            rejected += cand.id
            kept = kept - cand
            fit = fit(kept, w, anchor, firstRotation, s) ?: break
        }
        val residuals = points.associate { it.id to residual(fit.first, it) }
        val keptRes = kept.map { residuals.getValue(it.id) }
        return Solution(fit.first, residuals, rejected, w, sqrt(keptRes.sumOf { it * it } / keptRes.size), fit.second, anchor?.id)
    }

    fun residual(t: Similarity2D, p: ControlPoint): Double = (t.apply(p.local) - p.target).norm()

    private fun fit(pts: List<ControlPoint>, w: Map<String, Double>, anchor: ControlPoint?, fallbackRot: Double?, s: SolverSettings): Pair<Similarity2D, String>? {
        var sw = 0.0; var lx = 0.0; var ly = 0.0; var tx = 0.0; var ty = 0.0
        for (p in pts) { val k = w.getValue(p.id); sw += k; lx += k * p.local.x; ly += k * p.local.y; tx += k * p.target.x; ty += k * p.target.y }
        val cl = Vec2(lx / sw, ly / sw); val ct = Vec2(tx / sw, ty / sw)
        val spread = pts.maxOf { a -> pts.maxOf { b -> (a.target - b.target).norm() } }
        val (rot, method) = if (pts.size >= 2 && spread >= s.minSpreadForRotationM) {
            var a = 0.0; var b = 0.0
            for (p in pts) {
                val k = w.getValue(p.id); val l = p.local - cl; val t = p.target - ct
                a += k * (l.x * t.x + l.y * t.y); b += k * (l.x * t.y - l.y * t.x)
            }
            atan2(b, a) to "LEAST_SQUARES"
        } else (fallbackRot ?: return null) to (if (pts.size == 1) "ONE_POINT_HEADING" else "ROTATION_KEPT")
        val pin = anchor?.let { it.local to it.target } ?: (cl to ct)
        return Similarity2D.fromPointAndRotation(pin.first, pin.second, rot) to method
    }
}

enum class GnssQuality(val label: String) { FIABLE("fiable"), DEGRADE("dégradé"), INUTILISABLE("inutilisable") }

/** D7 thresholds (adjustable, journaled; to be tuned with the Pixel 7 logs). */
@Serializable
data class GnssThresholds(
    val minSatellitesUsed: Int = 4,
    val minMeanCn0DbHz: Double = 25.0,
    val maxAccuracyM: Double = 20.0,
    val maxFixAgeMs: Long = 5_000,
    val toUnusableMs: Long = 3_000,
    val toReliableMs: Long = 10_000,
)

/** What is known at one instant: satellites used in the fix (GnssStatus.usedInFix), their C/N0, Location accuracy, fix age. */
data class GnssObservation(val satellitesUsed: Int?, val cn0UsedDbHz: List<Double>, val accuracyM: Double?, val fixAgeMs: Long?)

/**
 * D7: GNSS unusable if < 4 satellites used, OR mean C/N0 of the 4 best < 25 dB-Hz, OR accuracy > 20 m, OR fix older than 5 s.
 * Hysteresis: a bad sample turns « fiable » into « dégradé » at once, « inutilisable » after 3 s of bad samples;
 * good samples turn « inutilisable » into « dégradé » at once, « fiable » after 10 s of good samples. Unknown values
 * (no GnssStatus) do not count as failures; no fix at all does.
 */
class GnssClassifier(val thresholds: GnssThresholds = GnssThresholds()) {
    var quality: GnssQuality = GnssQuality.INUTILISABLE; private set
    var reasons: List<String> = listOf("aucun fix"); private set
    private var badSinceMs: Long? = null
    private var goodSinceMs: Long? = null

    fun failures(o: GnssObservation): List<String> {
        val th = thresholds; val out = ArrayList<String>()
        if (o.fixAgeMs == null) out += "aucun fix" else if (o.fixAgeMs > th.maxFixAgeMs) out += "fix âgé de ${o.fixAgeMs / 1000} s"
        if (o.satellitesUsed != null && o.satellitesUsed < th.minSatellitesUsed) out += "${o.satellitesUsed} satellite(s) utilisé(s)"
        if (o.cn0UsedDbHz.size >= th.minSatellitesUsed) {
            val mean = o.cn0UsedDbHz.sortedDescending().take(th.minSatellitesUsed).average()
            if (mean < th.minMeanCn0DbHz) out += "C/N0 moyen %.0f dB-Hz".format(java.util.Locale.ROOT, mean)
        }
        if (o.accuracyM != null && o.accuracyM > th.maxAccuracyM) out += "précision %.0f m".format(java.util.Locale.ROOT, o.accuracyM)
        return out
    }

    /** @return true when [quality] changed. */
    fun update(nowMs: Long, o: GnssObservation): Boolean {
        val before = quality
        reasons = failures(o)
        if (reasons.isNotEmpty()) {
            goodSinceMs = null
            val since = badSinceMs ?: nowMs.also { badSinceMs = it }
            quality = if (nowMs - since >= thresholds.toUnusableMs) GnssQuality.INUTILISABLE
                else if (quality == GnssQuality.FIABLE) GnssQuality.DEGRADE else quality
        } else {
            badSinceMs = null
            val since = goodSinceMs ?: nowMs.also { goodSinceMs = it }
            quality = if (nowMs - since >= thresholds.toReliableMs) GnssQuality.FIABLE
                else if (quality == GnssQuality.INUTILISABLE) GnssQuality.DEGRADE else quality
        }
        return quality != before
    }
}

/** Camera tilt above the horizon in degrees (negative = looking down), computed from the sensors (no input field). */
object Tilt {
    /** ARCore pose quaternion (x, y, z, w), session frame y up, camera looking along its local −Z. */
    fun fromArQuaternion(q: DoubleArray): Double {
        val (x, y, z, w) = q
        val fy = -2.0 * (y * z - w * x)
        return Math.toDegrees(asin(fy.coerceIn(-1.0, 1.0)))
    }

    /** Android rotation matrix (SensorManager.getRotationMatrix…, row-major, device → world with z up); the camera looks along device −Z. */
    fun fromRotationMatrix(r: FloatArray): Double = Math.toDegrees(asin((-r[8].toDouble()).coerceIn(-1.0, 1.0)))
}

/** D8: display-only glide towards a new position in ~0.5 s (recorded data are never smoothed). */
class DisplayGlide(private val durationMs: Long = 500) {
    private var from: Vec2? = null; private var to: Vec2? = null; private var startMs = 0L

    fun target(p: Vec2, nowMs: Long) {
        if (to == null) { from = p; to = p; startMs = nowMs; return }
        if (p == to) return
        from = at(nowMs); to = p; startMs = nowMs
    }

    fun at(nowMs: Long): Vec2? {
        val a = from ?: return null; val b = to ?: return null
        val k = ((nowMs - startMs).toDouble() / durationMs).coerceIn(0.0, 1.0)
        return a + (b - a) * k
    }
}

internal fun rot(v: Vec2, r: Double) = Vec2(cos(r) * v.x - sin(r) * v.y, sin(r) * v.x + cos(r) * v.y)

/** D6: one ARCore anchor shared by the surveyed objects lying within [maxM] (8 m) of its creation point. */
object AnchorGroups {
    data class Group(val id: String, val center: Vec2)

    /** Nearest group within [maxM] of [p], or null when a new anchor (group) must be created. */
    fun assign(groups: List<Group>, p: Vec2, maxM: Double = 8.0): Group? =
        groups.map { it to (it.center - p).norm() }.filter { it.second <= maxM }.minByOrNull { it.second }?.first
}
