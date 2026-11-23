package app.lumisite.core.compute

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v176g § 2d–2f: room geometry from the ARCore planes (pure functions, session frame y up, metres).
 * - room edges = intersections of planes (floor ∩ wall = skirting, wall ∩ wall = vertical corner, wall ∩ ceiling), bounded by the polygons;
 * - furniture filter before a floor aim;
 * - virtual ceiling at floor + height when no ceiling plane is detected (source AR_CEILING_ESTIMATED, never a silent fallback).
 */
object ArPlanes {
    enum class Kind { FLOOR, WALL, CEILING }
    enum class EdgeKind { PLINTHE, ANGLE, CORNICHE }

    /** [polygon]: outline in the session frame. */
    data class Info(val id: String, val kind: Kind, val center: V3, val normal: V3, val polygon: List<V3>) {
        val area: Double get() = polygonArea(polygon, normal)
    }

    data class Edge(val a: V3, val b: V3, val kind: EdgeKind)

    const val MIN_AREA_M2 = 0.25
    const val FLOOR_TOLERANCE_M = 0.10
    const val FURNITURE_MIN_M = 0.30
    const val FURNITURE_MAX_M = 2.00
    /** A polygon must come this close to the intersection line for the edge to exist. */
    const val EDGE_REACH_M = 0.30
    const val WALL_ALIGN_DEG = 10.0
    const val CEILING_ESTIMATED = "AR_CEILING_ESTIMATED"

    fun cross(a: V3, b: V3) = V3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)

    /** Area of a planar polygon (Newell / cross-product sum projected on the normal). */
    fun polygonArea(poly: List<V3>, normal: V3): Double {
        if (poly.size < 3) return 0.0
        var s = V3(0.0, 0.0, 0.0)
        for (i in poly.indices) s += cross(poly[i], poly[(i + 1) % poly.size])
        return abs(s dot normal.unit()) / 2
    }

    /** Floor height: lowest horizontal upward plane of at least 0.25 m² (null if none). */
    fun floorY(planes: List<Info>): Double? = planes.filter { it.kind == Kind.FLOOR && it.area >= MIN_AREA_M2 }.minOfOrNull { it.center.y }

    /** Horizontal planes 0.3–2.0 m above the floor: tables, desks, shelves. */
    fun isFurniture(p: Info, floorY: Double?): Boolean =
        p.kind == Kind.FLOOR && floorY != null && (p.center.y - floorY) in FURNITURE_MIN_M..FURNITURE_MAX_M

    /** Planes usable for a floor aim: the floor (± 10 cm), never furniture, never a plane under 0.25 m². */
    fun floorCandidates(planes: List<Info>): List<Info> {
        val fy = floorY(planes) ?: return emptyList()
        return planes.filter { it.kind == Kind.FLOOR && it.area >= MIN_AREA_M2 && abs(it.center.y - fy) <= FLOOR_TOLERANCE_M }
    }

    /**
     * After calibration: walls whose horizontal direction is within 10° of a wall of the site (OSM, [siteWallDirsRad] already in the
     * session frame, angle of the wall line in the x/z plane). Unaligned walls are dropped only when at least one wall is aligned.
     */
    fun alignedWalls(walls: List<Info>, siteWallDirsRad: List<Double>): List<Info> {
        if (siteWallDirsRad.isEmpty()) return walls
        val ok = walls.filter { w ->
            val dir = kotlin.math.atan2(-w.normal.x, w.normal.z) // wall line direction = normal rotated by 90° in x/z
            siteWallDirsRad.any { angleBetweenLinesDeg(dir, it) <= WALL_ALIGN_DEG }
        }
        return ok.ifEmpty { walls }
    }

    /** Angle between two undirected lines, 0–90°. */
    fun angleBetweenLinesDeg(a: Double, b: Double): Double {
        var d = Math.toDegrees(abs(a - b)) % 180.0
        if (d > 90) d = 180 - d
        return d
    }

    /**
     * Line where two planes meet, bounded by both polygons: projection interval of each polygon on the line, intersected; null
     * for (nearly) parallel planes, polygons farther than 0.3 m from the line, or no overlap.
     */
    fun intersection(p: Info, q: Info): Edge? {
        val kind = edgeKind(p.kind, q.kind) ?: return null
        val n1 = p.normal.unit(); val n2 = q.normal.unit()
        val d = cross(n1, n2)
        if (d.norm() < 0.34) return null // < 20° between the planes
        val u = d.unit()
        // Point on both planes: solve with the third equation u·x = 0.
        val h1 = n1 dot p.center; val h2 = n2 dot q.center
        val o = (cross(n2, u) * h1 + cross(u, n1) * h2) * (1.0 / (n1 dot cross(n2, u)))
        val r1 = reach(p.polygon, o, u) ?: return null
        val r2 = reach(q.polygon, o, u) ?: return null
        val t0 = max(r1.first, r2.first); val t1 = min(r1.second, r2.second)
        if (t1 - t0 < 0.05) return null
        return Edge(o + u * t0, o + u * t1, kind)
    }

    /** Edges of the room between every pair of planes (floor ∩ wall, wall ∩ wall, wall ∩ ceiling). */
    fun roomEdges(planes: List<Info>): List<Edge> {
        val usable = planes.filter { it.area >= MIN_AREA_M2 }
        val out = ArrayList<Edge>()
        for (i in usable.indices) for (j in i + 1 until usable.size) intersection(usable[i], usable[j])?.let { out += it }
        return out
    }

    private fun edgeKind(a: Kind, b: Kind): EdgeKind? = when (setOf(a, b)) {
        setOf(Kind.FLOOR, Kind.WALL) -> EdgeKind.PLINTHE
        setOf(Kind.WALL) -> EdgeKind.ANGLE
        setOf(Kind.WALL, Kind.CEILING) -> EdgeKind.CORNICHE
        else -> null
    }

    /** Interval (t) of the polygon vertices projected on the line o + t·u, if the polygon comes within [EDGE_REACH_M] of it. */
    private fun reach(poly: List<V3>, o: V3, u: V3): Pair<Double, Double>? {
        if (poly.size < 3) return null
        val near = poly.filter { v -> val w = v - o; val t = w dot u; sqrt(max(0.0, (w dot w) - t * t)) <= EDGE_REACH_M }
        if (near.isEmpty()) return null
        val ts = near.map { (it - o) dot u }
        return ts.min() to ts.max()
    }

    /** Ceiling plane at floor + [heightM], facing down, source [CEILING_ESTIMATED]. */
    fun virtualCeiling(origin: V3, floorY: Double, heightM: Double): Aim.Plane =
        Aim.Plane(V3(origin.x, floorY + heightM, origin.z), V3(0.0, -1.0, 0.0), CEILING_ESTIMATED)

    /** Measured ceiling height from a point aimed at the wall/ceiling corner (or on a detected ceiling); null if implausible. */
    fun ceilingHeight(aimedY: Double, floorY: Double): Double? = (aimedY - floorY).takeIf { it in 1.8..8.0 }

    /** Angle between two normals in degrees. */
    fun angleDeg(a: V3, b: V3): Double = Math.toDegrees(acos(((a.unit() dot b.unit())).coerceIn(-1.0, 1.0)))
}
