package app.lumisite.core.compute

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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

    // ── build 199, point 2d : les traits sont les intersections de plans (arêtes de la pièce), pas les polygones ──

    /** Infinite plane in the session frame: a point + a normal (unit or not). */
    data class PlaneEq(val point: V3, val normal: V3)

    /** Planes whose normals make less than this angle are treated as parallel: no intersection line is drawn. */
    const val PARALLEL_MIN_DEG = 10.0
    /** An edge shorter than this is clipping noise, not a room edge. */
    const val MIN_EDGE_M = 0.02

    /**
     * Intersection of two infinite planes: a point of the shared line and its unit direction.
     * null when the planes are parallel or quasi-parallel (angle between normals < [minDeg], 10° by default —
     * below that the line position is too noisy to draw).
     */
    fun intersectPlanes(a: PlaneEq, b: PlaneEq, minDeg: Double = PARALLEL_MIN_DEG): Pair<V3, V3>? {
        val n1 = a.normal; val n2 = b.normal
        val l1 = n1.norm(); val l2 = n2.norm()
        if (l1 < 1e-9 || l2 < 1e-9) return null
        val d = n1 cross n2
        val d2 = d dot d
        if (sqrt(d2) / (l1 * l2) < sin(Math.toRadians(minDeg)) || d2 < 1e-12) return null
        // Point on both planes: p0 = (c1·(n2×d) + c2·(d×n1)) / |d|² with ci = ni·pi (Lagrange, see test).
        val c1 = n1 dot a.point; val c2 = n2 dot b.point
        val p0 = ((n2 cross d) * c1 + (d cross n1) * c2) * (1.0 / d2)
        return p0 to d.unit()
    }

    /**
     * Part of the line `o + t·d` inside a convex polygon (world vertices, coplanar, face normal [n]): the
     * [t0, t1] interval, null if the line misses the polygon. ARCore polygons are convex; for a non-convex
     * polygon the result is the intersection of the edge half-planes (an upper bound of the true part).
     */
    fun clipToPolygon(o: V3, d: V3, poly: List<V3>, n: V3): Pair<Double, Double>? {
        if (poly.size < 3) return null
        val c = V3(poly.sumOf { it.x } / poly.size, poly.sumOf { it.y } / poly.size, poly.sumOf { it.z } / poly.size)
        val nn = n.unit()
        var lo = Double.NEGATIVE_INFINITY; var hi = Double.POSITIVE_INFINITY
        for (i in poly.indices) {
            val v = poly[i]; val e = poly[(i + 1) % poly.size] - v
            var m = nn cross e
            // Inward edge normal: the centroid is inside the polygon whatever the vertex winding.
            if (m dot (c - v) < 0) m = m * -1.0
            val a = m dot (o - v); val b = m dot d
            if (abs(b) < 1e-9) { if (a < 0) return null } else if (b > 0) lo = max(lo, -a / b) else hi = min(hi, -a / b)
            if (lo > hi) return null
        }
        if (lo.isInfinite() || hi.isInfinite()) return null
        return lo to hi
    }

    /**
     * Edge where two detected planes meet (floor ∩ wall = skirting, wall ∩ wall = vertical corner, wall ∩
     * ceiling), bounded by both polygons: the part of the intersection line visible in both. null when the
     * planes are quasi-parallel (< [PARALLEL_MIN_DEG]) or the meeting is outside the polygons (< [MIN_EDGE_M]).
     */
    fun edgeSegment(a: PlaneEq, polyA: List<V3>, b: PlaneEq, polyB: List<V3>): Pair<V3, V3>? {
        val (o, d) = intersectPlanes(a, b) ?: return null
        val (a0, a1) = clipToPolygon(o, d, polyA, a.normal) ?: return null
        val (b0, b1) = clipToPolygon(o, d, polyB, b.normal) ?: return null
        val lo = max(a0, b0); val hi = min(a1, b1)
        if (hi - lo < MIN_EDGE_M) return null
        return (o + d * lo) to (o + d * hi)
    }

    /** Area of an ARCore polygon (x, z pairs in the plane local frame), m² — shoelace. */
    fun polygonAreaM2(polygonXz: List<Double>): Double {
        var s = 0.0
        val n = polygonXz.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            s += polygonXz[2 * i] * polygonXz[2 * j + 1] - polygonXz[2 * j] * polygonXz[2 * i + 1]
        }
        return abs(s) / 2
    }
}
