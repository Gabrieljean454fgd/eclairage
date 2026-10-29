package app.lumisite.core.survey

import app.lumisite.core.geo.Enu
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent

/**
 * Zones (lot 13): polygons in site metres stored as ShapeEvent(kind = "zone"). The zone of a survey is COMPUTED (never
 * stored): point in polygon (holes excluded), deepest zone wins. Self-intersecting rings are refused at input.
 */
object Zones {
    val KINDS = listOf("ARCHITECTURAL", "TECHNIQUE", "CIRCULATION", "EXTERIEUR")

    fun current(events: List<SessionEvent>): List<ShapeEvent> = Shapes.current(events).filter { it.kind == "zone" }

    /** Even-odd ray casting; points exactly on an edge may fall either side. */
    fun inRing(ring: List<Enu>, p: Enu): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]; val b = ring[j]
            if ((a.n > p.n) != (b.n > p.n) && p.e < (b.e - a.e) * (p.n - a.n) / (b.n - a.n) + a.e) inside = !inside
            j = i
        }
        return inside
    }

    fun contains(z: ShapeEvent, p: Enu): Boolean =
        z.points.size >= 3 && inRing(z.points, p) && z.holes.orEmpty().none { it.size >= 3 && inRing(it, p) }

    /** Signed area (m²): > 0 counter-clockwise (X east, Y north). */
    fun signedArea(ring: List<Enu>): Double =
        ring.indices.sumOf { i -> val a = ring[i]; val b = ring[(i + 1) % ring.size]; a.e * b.n - b.e * a.n } / 2

    private fun cross(o: Enu, a: Enu, b: Enu) = (a.e - o.e) * (b.n - o.n) - (a.n - o.n) * (b.e - o.e)

    private fun segmentsCross(p1: Enu, p2: Enu, q1: Enu, q2: Enu): Boolean {
        val d1 = cross(q1, q2, p1); val d2 = cross(q1, q2, p2); val d3 = cross(p1, p2, q1); val d4 = cross(p1, p2, q2)
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0)) && d1 != 0.0 && d2 != 0.0 && d3 != 0.0 && d4 != 0.0
    }

    /** True when two non-adjacent edges of the closed ring cross. */
    fun selfIntersects(ring: List<Enu>): Boolean {
        val n = ring.size
        if (n < 4) return false
        for (i in 0 until n) for (j in i + 2 until n) {
            if (i == 0 && j == n - 1) continue
            if (segmentsCross(ring[i], ring[(i + 1) % n], ring[j], ring[(j + 1) % n])) return true
        }
        return false
    }

    /** Depth: depth(parent) + 1 when parentZoneId is set, else number of other zones containing all its vertices. */
    fun depth(z: ShapeEvent, zones: List<ShapeEvent>, seen: Set<String> = emptySet()): Int {
        val parent = z.parentZoneId?.let { pid -> zones.firstOrNull { it.id == pid } }
        if (parent != null && parent.id !in seen) return depth(parent, zones, seen + z.id) + 1
        return zones.count { o -> o.id != z.id && z.points.all { inRing(o.points, it) } }
    }

    fun zoneOf(p: Enu, zones: List<ShapeEvent>): ShapeEvent? =
        zones.filter { contains(it, p) }.maxByOrNull { depth(it, zones) }
}
