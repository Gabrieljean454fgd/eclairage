package app.lumisite.core.survey

import app.lumisite.core.geo.Enu
import kotlin.math.hypot

/** Rules of the elastic drawing in Session > Annoter (stage 2.1/2.2), independent of the screen. */
object Sketch {
    /** Touch radius on the map, in dp, used to recognise "the last point touched again". */
    const val TOUCH_DP = 28.0

    fun dist(a: Enu, b: Enu) = hypot(a.e - b.e, a.n - b.n)

    /** Tolerance in metres for a touch, given the visible width of the map (m) and its width on screen (dp). */
    fun toleranceM(visibleWidthM: Double, mapWidthDp: Double): Double =
        if (visibleWidthM <= 0 || mapWidthDp <= 0) 0.5 else visibleWidthM / mapWidthDp * TOUCH_DP

    enum class PolygonTap { ADD, CLOSE, IGNORE }

    /** Polygon: each tap adds a vertex; touching the last vertex again closes it (≥ 3 vertices). No button, no double tap. */
    fun polygonTap(pts: List<Enu>, p: Enu, tolM: Double): PolygonTap {
        val last = pts.lastOrNull() ?: return PolygonTap.ADD
        if (dist(last, p) > tolM) return PolygonTap.ADD
        return if (pts.size >= 3) PolygonTap.CLOSE else PolygonTap.IGNORE
    }

    /** Live preview: placed vertices + the finger position; closed ring for a polygon (first point repeated). */
    fun polygonPreview(pts: List<Enu>, hover: Enu?): List<Enu> {
        val all = if (hover != null) pts + hover else pts
        return if (all.size >= 3) all + all.first() else all
    }

    fun linePreview(pts: List<Enu>, hover: Enu?): List<Enu> = if (hover != null) pts + hover else pts

    /** Rectangle from the first corner and the finger, as a closed ring. */
    fun rectanglePreview(a: Enu?, hover: Enu?): List<Enu> {
        if (a == null || hover == null) return emptyList()
        return listOf(a, Enu(hover.e, a.n, 0.0), hover, Enu(a.e, hover.n, 0.0), a)
    }
}
