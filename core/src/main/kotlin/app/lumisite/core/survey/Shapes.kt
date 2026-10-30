package app.lumisite.core.survey

import app.lumisite.core.geo.Enu
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.ZoneEvent
import app.lumisite.core.model.SurveyEvent
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Geometry helpers for shapes drawn in the site frame (metres). */
object Shapes {
    private fun sup(e: SessionEvent) = when (e) { is ShapeEvent -> e.supersedes; is ZoneEvent -> e.supersedes; is SurveyEvent -> e.supersedes; else -> null }

    /** Latest version id of the edit chain containing [id]. */
    fun head(events: List<SessionEvent>, id: String): String {
        val next = HashMap<String, String>()
        events.forEach { e -> sup(e)?.let { next[it] = e.id } }
        var h = id
        while (true) h = next[h] ?: return h
    }

    /** Current shapes: chain heads that are not deleted. */
    fun current(all: List<SessionEvent>): List<ShapeEvent> {
        val events = all.map { (it as? ZoneEvent)?.asShape() ?: it }
        val superseded = events.mapNotNull { (it as? ShapeEvent)?.supersedes }.toSet()
        return events.filterIsInstance<ShapeEvent>().filter { it.id !in superseded && it.deleted != true }
    }

    fun outline(s: ShapeEvent): List<Enu> = when (s.geometryType) {
        "rectangle" -> if (s.points.size >= 2) { val a = s.points[0]; val b = s.points[1]; listOf(a, Enu(b.e, a.n, 0.0), b, Enu(a.e, b.n, 0.0), a) } else s.points
        "circle" -> s.points.firstOrNull()?.let { c -> val r = s.radiusM ?: 1.0; (0..32).map { i -> val t = 2 * Math.PI * i / 32; Enu(c.e + r * cos(t), c.n + r * sin(t), 0.0) } } ?: emptyList()
        "polygon" -> if (s.points.isNotEmpty()) s.points + s.points.first() else s.points
        else -> s.points
    }

    fun centroid(s: ShapeEvent): Enu = Enu(s.points.map { it.e }.average(), s.points.map { it.n }.average(), 0.0)

    fun translate(s: ShapeEvent, de: Double, dn: Double) = s.copy(points = s.points.map { Enu(it.e + de, it.n + dn, it.u) })

    fun scale(s: ShapeEvent, k: Double): ShapeEvent {
        val c = centroid(s)
        return s.copy(points = s.points.map { Enu(c.e + (it.e - c.e) * k, c.n + (it.n - c.n) * k, it.u) }, radiusM = s.radiusM?.times(k))
    }

    private fun segDist(p: Enu, a: Enu, b: Enu): Double {
        val dx = b.e - a.e; val dy = b.n - a.n; val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) 0.0 else (((p.e - a.e) * dx + (p.n - a.n) * dy) / l2).coerceIn(0.0, 1.0)
        return hypot(p.e - (a.e + t * dx), p.n - (a.n + t * dy))
    }

    fun distance(s: ShapeEvent, p: Enu): Double {
        val o = outline(s)
        if (o.size == 1) return hypot(p.e - o[0].e, p.n - o[0].n)
        return o.zipWithNext().minOfOrNull { (a, b) -> segDist(p, a, b) } ?: Double.MAX_VALUE
    }

    fun nearest(shapes: List<ShapeEvent>, p: Enu, maxM: Double = 3.0): ShapeEvent? =
        shapes.map { it to distance(it, p) }.filter { it.second <= maxM }.minByOrNull { it.second }?.first
}
