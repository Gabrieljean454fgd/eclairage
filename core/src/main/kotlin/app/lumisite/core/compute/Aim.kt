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
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm() = sqrt(this dot this)
    fun unit() = norm().let { if (it < 1e-12) this else this * (1 / it) }
    fun distanceTo(o: V3) = (this - o).norm()
}

sealed interface AimResult {
    /** [source]: "AR_PLANE" (floor) | "AR_CEILING" | "AR_WALL" | "AR_LAST_PLANE" | "AR_FLOOR_ESTIMATE" |
     * "AR_CEILING_ESTIMATED" (virtual ceiling at floor + hauteur sous plafond); "ESTIMATED" only in older sessions. */
    data class Hit(val point: V3, val source: String) : AimResult
    /** Every candidate plane was seen at a grazing angle: refuse rather than return a jumping point. */
    data object Grazing : AimResult
    data object NoTarget : AimResult
    /** v176c: tracking but no plane hit (e.g. aiming at a ceiling not detected yet): « position à compléter », no silent 50 cm point. */
    data object ToComplete : AimResult
}

/**
 * Aiming from the camera centre (lot 10, v176c). Fallback chain: ARCore planes, floor, ceiling and walls (with polygon
 * test, nearest hit first) → virtual ceiling (2f, [virtualCeilingY], source [SRC_CEILING_ESTIMATED]) → plane of the last
 * object → central floor plane → « position à compléter » ([AimResult.ToComplete]);
 * analytic ray/plane intersection, grazing rejection |n·d| < 0.03.
 * Every level returns null instead of throwing.
 */
object Aim {
    const val GRAZING = 0.03
    const val MAX_RANGE_M = 20.0
    /** Floor assumed this far below the camera when no plane is known (phone held at chest height). */
    const val DEFAULT_CAMERA_HEIGHT_M = 1.4
    /** 2f: hit source when the aimed point is the ray ∩ virtual ceiling (floor + hauteur sous plafond), never silent. */
    const val SRC_CEILING_ESTIMATED = "AR_CEILING_ESTIMATED"
    /** Inclination fallback when ARCore has no wall plane (explicit estimate, never silent). */
    const val SRC_WALL_ESTIMATE = "AR_WALL_ESTIMATE"
    /** Point measured by the ARCore Depth API at the reticle without a detected plane. */
    const val SRC_DEPTH = "AR_DEPTH"

    /** True for an inferred hit, including one based on a previously detected plane's height. */
    fun isEstimate(source: String?): Boolean =
        source == "ESTIMATED" || source == "AR_FLOOR_ESTIMATE" || source == "AR_LAST_PLANE" ||
            source == SRC_CEILING_ESTIMATED || source == SRC_WALL_ESTIMATE

    /** Default ceiling height used only when the user has not entered « hauteur sous plafond ». */
    const val DEFAULT_CEILING_HEIGHT_M = 2.5
    /** Default distance of a vertical wall estimate in front of the camera. */
    const val DEFAULT_WALL_DISTANCE_M = 2.0
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

    /**
     * [fallbacks] = false (v176c, Mur or Plafond chosen): no last-object / floor-estimate fallback.
     * [virtualCeilingY] (2f): session-frame height of the virtual ceiling (detected floor + hauteur sous plafond saisie);
     * a hit on it is reported with source [SRC_CEILING_ESTIMATED] — never a silent fallback.
     */
    fun resolve(origin: V3, dir: V3, arPlanes: List<Plane>, lastObjectY: Double?, tracking: Boolean, fallbacks: Boolean = true,
                virtualCeilingY: Double? = null, extraPlanes: List<Plane> = emptyList()): AimResult {
        if (!tracking) return AimResult.NoTarget
        // Nearest detected plane along the ray (a wall in front of a farther floor wins), then the fallbacks.
        nearest(origin, dir, arPlanes)?.let { return it }
        val floorY = arPlanes.filter { it.source == "AR_PLANE" }.minOfOrNull { it.point.y } ?: (origin.y - DEFAULT_CAMERA_HEIGHT_M)
        val chain = buildList {
            virtualCeilingY?.let { add(Plane(V3(origin.x, it, origin.z), UP, SRC_CEILING_ESTIMATED)) }
            addAll(extraPlanes)
            if (fallbacks) {
                lastObjectY?.let { add(Plane(V3(origin.x, it, origin.z), UP, "AR_LAST_PLANE")) }
                add(Plane(V3(origin.x, floorY, origin.z), UP, "AR_FLOOR_ESTIMATE"))
            }
        }
        var grazing = arPlanes.any { intersect(origin, dir, it) == Ray.Grazing }
        for (pl in chain) when (val r = intersect(origin, dir, pl)) {
            is Ray.At -> return AimResult.Hit(r.p, pl.source)
            Ray.Grazing -> grazing = true
            Ray.Miss -> {}
        }
        return if (grazing) AimResult.Grazing else AimResult.ToComplete
    }

    private fun nearest(origin: V3, dir: V3, planes: List<Plane>): AimResult.Hit? =
        planes.mapNotNull { pl -> (intersect(origin, dir, pl) as? Ray.At)?.let { AimResult.Hit(it.p, pl.source) } }
            .minByOrNull { it.point.distanceTo(origin) }

    /** v176c, surface chosen by the user at the survey (Sol / Mur / Plafond): keeps only the detected planes of that kind. */
    fun sourceOf(surface: String?): String? = when (surface) { "SOL" -> "AR_PLANE"; "MUR" -> "AR_WALL"; "PLAFOND" -> "AR_CEILING"; else -> null }

    /**
     * Vertical plane in front of the camera, used when the tilt says MUR but ARCore has no wall.
     * [forward] is the camera look direction; the plane is placed [DEFAULT_WALL_DISTANCE_M] ahead,
     * facing the camera, source [SRC_WALL_ESTIMATE].
     */
    fun wallEstimate(origin: V3, forward: V3, distanceM: Double = DEFAULT_WALL_DISTANCE_M): Plane {
        val h = V3(forward.x, 0.0, forward.z)
        val n = if (h.norm() < 1e-6) V3(0.0, 0.0, -1.0) else h.unit()
        val d = distanceM.coerceIn(0.3, MAX_RANGE_M)
        return Plane(origin + n * d, n, SRC_WALL_ESTIMATE)
    }

    /** Horizontal ceiling estimate at floor + height (default 2,5 m) when no height was entered. */
    fun ceilingEstimate(origin: V3, floorY: Double, heightM: Double = DEFAULT_CEILING_HEIGHT_M): Plane =
        Plane(V3(origin.x, floorY + heightM, origin.z), UP, SRC_CEILING_ESTIMATED)

    /** Camera forward (-Z) rotated by quaternion (x, y, z, w). */
    fun forward(q: List<Double>): V3 {
        val (x, y, z, w) = q
        return V3(-(2 * (x * z + w * y)), -(2 * (y * z - w * x)), -(1 - 2 * (x * x + y * y)))
    }

    /** Session-frame point → site ENU through the active calibration segment (same convention as PoseGeometry.plane). */
    fun toSite(p: V3, transform: Similarity2D): Enu = transform.apply(Vec2(p.x, -p.z)).let { Enu(it.x, it.y, p.y) }

    /**
     * Site ENU → session-frame point, exact inverse of [toSite] (2c: anchors of a resumed session are recreated at the
     * map-projected position once the transform is restored). u maps to the session y (no vertical calibration).
     */
    fun toSession(site: Enu, transform: Similarity2D): V3 = transform.invert(Vec2(site.e, site.n)).let { V3(it.x, site.u, -it.y) }
}
