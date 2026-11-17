package app.lumisite.core.compute

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v176c, stripped AR view: pure geometry of the lines drawn over the camera (plane outlines, vertical wall edges,
 * calibration flags, luminaire symbol). Behaviour read in the ARCore sample hello_ar_java (Apache-2.0), reimplemented.
 */
object ArGeometry {
    /** Column-major 4×4 matrix (OpenGL / ARCore `Pose.toMatrix`) applied to a point. */
    fun transform(m: DoubleArray, p: V3): V3 = V3(
        m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
        m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
        m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14],
    )

    /** Plane polygon (ARCore `Plane.getPolygon()`: x, z pairs in the plane's local frame) → session-frame points. */
    fun polygonToWorld(centerPose: DoubleArray, polygonXz: List<Double>): List<V3> =
        (0 until polygonXz.size / 2).map { transform(centerPose, V3(polygonXz[2 * it], 0.0, polygonXz[2 * it + 1])) }

    /**
     * World → screen pixels through a column-major view-projection matrix; null behind the camera or outside the near plane.
     * Pixel origin top-left, y down.
     */
    fun project(viewProj: DoubleArray, p: V3, widthPx: Int, heightPx: Int): Pair<Double, Double>? {
        val x = viewProj[0] * p.x + viewProj[4] * p.y + viewProj[8] * p.z + viewProj[12]
        val y = viewProj[1] * p.x + viewProj[5] * p.y + viewProj[9] * p.z + viewProj[13]
        val w = viewProj[3] * p.x + viewProj[7] * p.y + viewProj[11] * p.z + viewProj[15]
        if (w <= 1e-6) return null
        return (x / w + 1) / 2 * widthPx to (1 - y / w) / 2 * heightPx
    }

    /**
     * Vertical edges of a wall: the two vertical segments at the horizontal extremities of the wall polygon (along its
     * main horizontal direction), from the lowest to the highest point of the polygon. Empty for a degenerate polygon.
     */
    fun verticalEdges(world: List<V3>): List<Pair<V3, V3>> {
        if (world.size < 3) return emptyList()
        val cx = world.sumOf { it.x } / world.size; val cz = world.sumOf { it.z } / world.size
        var sxx = 0.0; var szz = 0.0; var sxz = 0.0
        for (p in world) { val dx = p.x - cx; val dz = p.z - cz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz }
        // Main axis of the horizontal footprint (largest eigenvector of the 2×2 covariance).
        val tr = sxx + szz; val det = sxx * szz - sxz * sxz
        val l1 = tr / 2 + sqrt(max(0.0, tr * tr / 4 - det))
        var ux = sxz; var uz = l1 - sxx
        if (ux * ux + uz * uz < 1e-12) { ux = 1.0; uz = 0.0; if (szz > sxx) { ux = 0.0; uz = 1.0 } }
        val n = sqrt(ux * ux + uz * uz); ux /= n; uz /= n
        val t = world.map { (it.x - cx) * ux + (it.z - cz) * uz }
        val tMin = t.min(); val tMax = t.max()
        if (tMax - tMin < 0.05) return emptyList()
        val yMin = world.minOf { it.y }; val yMax = world.maxOf { it.y }
        if (yMax - yMin < 0.05) return emptyList()
        return listOf(tMin, tMax).map { k -> V3(cx + ux * k, yMin, cz + uz * k) to V3(cx + ux * k, yMax, cz + uz * k) }
    }

    /** Calibration flag at [base] (session frame, y up): mast of [mastM] then a triangular pennant, as line vertices pairs. */
    fun flag(base: V3, mastM: Double = 0.30): List<Pair<V3, V3>> {
        val top = base + V3(0.0, mastM, 0.0)
        val mid = base + V3(0.0, mastM * 0.6, 0.0)
        val tip = base + V3(mastM * 0.5, mastM * 0.8, 0.0)
        return listOf(base to top, top to tip, tip to mid)
    }

    /** Luminaire symbol at [c]: circle of radius [r] (horizontal, [segments] sides) with a cross, as line segments. */
    fun luminaire(c: V3, r: Double = 0.12, segments: Int = 16): List<Pair<V3, V3>> {
        val ring = (0 until segments).map { i ->
            val a = 2 * Math.PI * i / segments
            V3(c.x + r * kotlin.math.cos(a), c.y, c.z + r * kotlin.math.sin(a))
        }
        val k = r / sqrt(2.0)
        return ring.indices.map { ring[it] to ring[(it + 1) % ring.size] } + listOf(
            V3(c.x - k, c.y, c.z - k) to V3(c.x + k, c.y, c.z + k),
            V3(c.x - k, c.y, c.z + k) to V3(c.x + k, c.y, c.z - k),
        )
    }

    fun clamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))
}
