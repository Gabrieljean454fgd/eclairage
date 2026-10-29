package app.lumisite.core.compute

import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

/** 3D vector in the ARCore session frame (metres, y up). */
data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun norm() = sqrt(this dot this)
    fun unit() = norm().let { if (it < 1e-12) this else this * (1 / it) }
    fun distanceTo(o: V3) = (this - o).norm()
}

sealed interface AimResult {
    /** [source]: "AR_PLANE" | "AR_LAST_PLANE" | "AR_FLOOR_ESTIMATE" | "ESTIMATED" (50 cm fallback). */
    data class Hit(val point: V3, val source: String) : AimResult
    /** Every candidate plane was seen at a grazing angle: refuse rather than return a jumping point. */
    data object Grazing : AimResult
    data object NoTarget : AimResult
}

/**
 * Aiming from the camera centre (lot 10). Fallback chain: ARCore planes (with polygon test) → plane of the last object
 * → central floor plane → "no target"; analytic ray/plane intersection, grazing rejection |n·d| < 0.03.
 * Every level returns null instead of throwing.
 */
object Aim {
    const val GRAZING = 0.03
    const val FALLBACK_M = 0.5
    const val MAX_RANGE_M = 20.0
    /** Floor assumed this far below the camera when no plane is known (phone held at chest height). */
    const val DEFAULT_CAMERA_HEIGHT_M = 1.4
    val UP = V3(0.0, 1.0, 0.0)

    data class Plane(val point: V3, val normal: V3, val source: String, val contains: ((V3) -> Boolean)? = null)

    sealed interface Ray { data class At(val p: V3) : Ray; data object Grazing : Ray; data object Miss : Ray }

    fun intersect(origin: V3, dir: V3, plane: Plane): Ray = runCatching {
        val d = dir.unit(); val n = plane.normal.unit()
        val denom = n dot d
        if (abs(denom) < GRAZING) return Ray.Grazing
        val t = (n dot (plane.point - origin)) / denom
        if (t <= 0 || t > MAX_RANGE_M) return Ray.Miss
        val p = origin + d * t
        if (plane.contains?.invoke(p) == false) Ray.Miss else Ray.At(p)
    }.getOrDefault(Ray.Miss)

    fun resolve(origin: V3, dir: V3, arPlanes: List<Plane>, lastObjectY: Double?, tracking: Boolean): AimResult {
        if (!tracking) return AimResult.NoTarget
        val floorY = arPlanes.minOfOrNull { it.point.y } ?: (origin.y - DEFAULT_CAMERA_HEIGHT_M)
        val chain = arPlanes + listOfNotNull(
            lastObjectY?.let { Plane(V3(origin.x, it, origin.z), UP, "AR_LAST_PLANE") },
            Plane(V3(origin.x, floorY, origin.z), UP, "AR_FLOOR_ESTIMATE"),
        )
        var grazing = false
        for (pl in chain) when (val r = intersect(origin, dir, pl)) {
            is Ray.At -> return AimResult.Hit(r.p, pl.source)
            Ray.Grazing -> grazing = true
            Ray.Miss -> {}
        }
        return if (grazing) AimResult.Grazing else AimResult.Hit(origin + dir.unit() * FALLBACK_M, "ESTIMATED")
    }

    /** Camera forward (-Z) rotated by quaternion (x, y, z, w). */
    fun forward(q: List<Double>): V3 {
        val (x, y, z, w) = q
        return V3(-(2 * (x * z + w * y)), -(2 * (y * z - w * x)), -(1 - 2 * (x * x + y * y)))
    }

    /** Session-frame point → site ENU through the active calibration segment (same convention as PoseGeometry.plane). */
    fun toSite(p: V3, transform: Similarity2D): Enu = transform.apply(Vec2(p.x, -p.z)).let { Enu(it.x, it.y, p.y) }
}
