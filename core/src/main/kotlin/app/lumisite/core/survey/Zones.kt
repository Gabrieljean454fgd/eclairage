package app.lumisite.core.survey

import app.lumisite.core.geo.Enu
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.ZoneEvent

/**
 * Zones (lot 13): polygons in site metres stored as ShapeEvent(kind = "zone"). The zone of a survey is COMPUTED (never
 * stored): point in polygon (holes excluded), deepest zone wins. Self-intersecting rings are refused at input.
 */
object Zones {
    val KINDS = listOf("ARCHITECTURAL", "TECHNIQUE", "CIRCULATION", "EXTERIEUR")

    /** Event id -> id of the first version of its edit chain (stable zone identity = exported Feature id). */
    fun roots(events: List<SessionEvent>): Map<String, String> {
        val root = HashMap<String, String>()
        events.forEach { e ->
            val (id, sup) = when (e) { is ShapeEvent -> e.id to e.supersedes; is ZoneEvent -> e.id to e.supersedes; else -> return@forEach }
            root[id] = sup?.let { root[it] ?: it } ?: id
        }
        return root
    }

    /**
     * Current zones: ZoneEvent (0.2) and ShapeEvent(kind = "zone") (0.1), as shapes. `id` and `parentZoneId` are the
     * stable identities (first version of each edit chain), so they match the exported Feature ids across edits.
     */
    fun current(events: List<SessionEvent>): List<ShapeEvent> {
        val root = roots(events)
        return Shapes.current(events).filter { it.kind == "zone" }
            .map { z -> z.copy(id = root[z.id] ?: z.id, parentZoneId = z.parentZoneId?.let { root[it] ?: it }) }
    }

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

    private const val EPS = 1e-9
    private fun sgn(v: Double) = if (kotlin.math.abs(v) < EPS) 0 else if (v > 0) 1 else -1
    private fun onSeg(a: Enu, b: Enu, p: Enu) =
        p.e in minOf(a.e, b.e) - EPS..maxOf(a.e, b.e) + EPS && p.n in minOf(a.n, b.n) - EPS..maxOf(a.n, b.n) + EPS

    /** Proper crossing, or any contact (a vertex on the other segment, collinear overlap). */
    fun segmentsTouch(p1: Enu, p2: Enu, q1: Enu, q2: Enu): Boolean {
        val d1 = sgn(cross(q1, q2, p1)); val d2 = sgn(cross(q1, q2, p2)); val d3 = sgn(cross(p1, p2, q1)); val d4 = sgn(cross(p1, p2, q2))
        if (d1 * d2 < 0 && d3 * d4 < 0) return true
        return (d1 == 0 && onSeg(q1, q2, p1)) || (d2 == 0 && onSeg(q1, q2, p2)) || (d3 == 0 && onSeg(p1, p2, q1)) || (d4 == 0 && onSeg(p1, p2, q2))
    }

    /** True when two non-adjacent edges of the closed ring cross or touch, or two adjacent edges fold back on each other. */
    fun selfIntersects(ring: List<Enu>): Boolean {
        val n = ring.size
        if (n < 3) return false
        for (i in 0 until n) {
            val a = ring[i]; val b = ring[(i + 1) % n]; val c = ring[(i + 2) % n]
            if (sgn(cross(a, b, c)) == 0 && (b.e - a.e) * (c.e - b.e) + (b.n - a.n) * (c.n - b.n) < 0) return true
        }
        if (n < 4) return false
        for (i in 0 until n) for (j in i + 2 until n) {
            if (i == 0 && j == n - 1) continue
            if (segmentsTouch(ring[i], ring[(i + 1) % n], ring[j], ring[(j + 1) % n])) return true
        }
        return false
    }

    private fun open(r: List<Enu>) = if (r.size > 1 && r.first().e == r.last().e && r.first().n == r.last().n) r.dropLast(1) else r

    private fun same(a: Enu, b: Enu) = kotlin.math.abs(a.e - b.e) < EPS && kotlin.math.abs(a.n - b.n) < EPS

    /** Fewer than 3 distinct vertices, a repeated consecutive vertex (zero-length edge), or zero area. */
    private fun degenerate(r: List<Enu>) =
        r.size < 3 || r.indices.any { same(r[it], r[(it + 1) % r.size]) } || kotlin.math.abs(signedArea(r)) < EPS

    private fun ringsTouch(a: List<Enu>, b: List<Enu>) =
        a.indices.any { i -> b.indices.any { j -> segmentsTouch(a[i], a[(i + 1) % a.size], b[j], b[(j + 1) % b.size]) } }

    /** Why the zone is refused, or null: self-intersecting outer ring or hole, hole outside the outer ring, rings touching. */
    fun problem(outer0: List<Enu>, holes0: List<List<Enu>> = emptyList()): String? {
        val outer = open(outer0); val holes = holes0.map(::open)
        if (degenerate(outer)) return "au moins 3 sommets distincts, surface non nulle"
        if (selfIntersects(outer)) return "le polygone se recoupe"
        holes.forEachIndexed { k, h ->
            if (degenerate(h)) return "le trou ${k + 1} est plat ou a moins de 3 sommets"
            if (selfIntersects(h)) return "le trou ${k + 1} se recoupe"
            if (!h.all { inRing(outer, it) } || ringsTouch(outer, h)) return "le trou ${k + 1} sort du contour"
            for (m in 0 until k) if (ringsTouch(holes[m], h) || inRing(holes[m], h[0]) || inRing(h, holes[m][0])) return "les trous ${m + 1} et ${k + 1} se chevauchent"
        }
        return null
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
