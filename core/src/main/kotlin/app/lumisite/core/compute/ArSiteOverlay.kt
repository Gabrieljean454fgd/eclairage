package app.lumisite.core.compute

import app.lumisite.core.catalog.PlanSymbols
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.model.BBox
import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.OsmFeature
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * v176i: AR overlay built from the selected site's data (plan layer). Only data that exist are drawn: luminaires of the Relevé
 * with their catalog symbol, control points (repères) of every visit, the site outline (user rectangles, selected OSM
 * buildings). Nothing is drawn before a calibration (no map → AR transform) and the plan layer lies on the detected floor
 * (the renderer adds the floor height; no floor detected = no plan layer). No wall is extruded from the outline.
 * Session frame: x right, y up, z backwards; plan (e, n) ↔ session (x, −z) through the inverse of the calibration [Similarity2D].
 */
object ArSiteOverlay {
    enum class Kind { LUMINAIRE, REPERE }
    /** [current] = recorded during this visit (already drawn at its own ARCore anchor); [label] for diagnostics only. */
    data class Item(val site: Enu, val kind: Kind, val symbol: String?, val argb: Int, val current: Boolean, val label: String = "")
    data class Group(val argb: Int, val segs: List<Pair<V3, V3>>)
    data class Scene(val groups: List<Group>, val luminaires: Int, val reperes: Int, val otherVisits: Int, val outlineSegs: Int) {
        val empty get() = groups.isEmpty()
    }

    const val OUTLINE_ARGB = 0xFF00E5FF.toInt()
    const val REPERE_ARGB = 0xFFFF9800.toInt()
    const val OLD_REPERE_ARGB = 0xFFFFCC80.toInt()
    /** Plan items beyond this distance (m) from the calibration origin are left out (other floors / far buildings). */
    const val MAX_RANGE_M = 60.0

    /** Plan point (site ENU) → session frame at height [y]. */
    fun toSession(e: Enu, tr: Similarity2D, y: Double): V3 {
        val dx = (e.e - tr.tx) / tr.scale; val dy = (e.n - tr.ty) / tr.scale
        val c = cos(-tr.rotationRad); val s = sin(-tr.rotationRad)
        return V3(c * dx - s * dy, y, -(s * dx + c * dy))
    }

    /**
     * Plan symbol (unit box [-1, 1], y down = towards the plan's south) laid flat at [c] with half-size [halfM]: circles and
     * arcs as polylines, polygons as edges, lines as segments (fills are not drawn: AR stays lines only).
     */
    fun glyph(prims: List<PlanSymbols.Prim>, c: V3, halfM: Double, steps: Int = 16): List<Pair<V3, V3>> {
        fun p(x: Float, y: Float) = V3(c.x + x * halfM, c.y, c.z + y * halfM)
        fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float): List<Pair<V3, V3>> {
            val n = maxOf(4, (steps * kotlin.math.abs(sweep) / 360f).toInt())
            val pts = (0..n).map { i -> val a = Math.toRadians((start + sweep * i / n).toDouble()); p((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat()) }
            return pts.zipWithNext()
        }
        return prims.flatMap { pr ->
            when (pr) {
                is PlanSymbols.Line -> listOf(p(pr.x1, pr.y1) to p(pr.x2, pr.y2))
                is PlanSymbols.Circle -> arc(pr.cx, pr.cy, pr.r, 0f, 360f)
                is PlanSymbols.Arc -> arc(pr.cx, pr.cy, pr.r, pr.start, pr.sweep)
                is PlanSymbols.Poly -> {
                    val pts = pr.pts.chunked(2).filter { it.size == 2 }.map { p(it[0], it[1]) }
                    (if (pr.closed && pts.size > 2) pts + pts.first() else pts).zipWithNext()
                }
            }
        }
    }

    /** Site outline rings (ENU): user rectangles, then the selected OSM buildings (outer rings). */
    fun outlines(rects: List<BBox>, features: List<OsmFeature>, selectedRefs: Set<String>, ltp: LocalTangentPlane): List<List<Enu>> {
        fun ring(ll: List<LatLon>) = ll.map { ltp.toEnu(it).copy(u = 0.0) }
        val r = rects.map { b -> ring(listOf(LatLon(b.south, b.west), LatLon(b.south, b.east), LatLon(b.north, b.east), LatLon(b.north, b.west), LatLon(b.south, b.west))) }
        val f = features.filter { it.ref in selectedRefs && it.geometry.size > 2 }.map { ring(it.geometry) }
        return r + f
    }

    /** Plan layer at height 0 (the renderer lifts it to the detected floor); current-visit items are skipped (anchored elsewhere). */
    fun build(items: List<Item>, outlines: List<List<Enu>>, tr: Similarity2D, symbolHalfM: Double = 0.15): Scene {
        val origin = Enu(tr.tx, tr.ty)
        fun near(e: Enu) = hypot(e.e - origin.e, e.n - origin.n) <= MAX_RANGE_M
        val groups = ArrayList<Group>()
        var outlineSegs = 0
        val outlineLines = outlines.flatMap { ring -> ring.zipWithNext().filter { (a, b) -> near(a) || near(b) } }
            .map { (a, b) -> toSession(a, tr, 0.0) to toSession(b, tr, 0.0) }
        if (outlineLines.isNotEmpty()) { groups += Group(OUTLINE_ARGB, outlineLines); outlineSegs = outlineLines.size }
        val shown = items.filter { !it.current && near(it.site) }
        shown.groupBy { it.argb }.forEach { (argb, list) ->
            groups += Group(argb, list.flatMap { i ->
                val c = toSession(i.site, tr, 0.0)
                if (i.kind == Kind.REPERE) glyph(PlanSymbols.prims(PlanSymbols.FLAG), c, symbolHalfM) else glyph(PlanSymbols.prims(i.symbol), c, symbolHalfM)
            })
        }
        return Scene(groups, items.count { it.kind == Kind.LUMINAIRE && near(it.site) }, items.count { it.kind == Kind.REPERE && near(it.site) },
            shown.size, outlineSegs)
    }

    /** « Plan : 12 luminaires · 5 repères (3 d'autres visites) · contours 18 traits », or why nothing is drawn. */
    fun status(scene: Scene?, calibrated: Boolean, floorKnown: Boolean, shown: Boolean): String = when {
        !shown -> "Plan : masqué"
        !calibrated -> "Plan : calibrez pour superposer les données du site"
        scene == null -> "Plan : chargement des données du site…"
        !floorKnown -> "Plan : visez le sol pour poser le plan (aucun sol détecté)"
        else -> "Plan : ${scene.luminaires} luminaire(s) · ${scene.reperes} repère(s) (${scene.otherVisits} d'autres visites) · contours ${scene.outlineSegs} trait(s)"
    }
}
