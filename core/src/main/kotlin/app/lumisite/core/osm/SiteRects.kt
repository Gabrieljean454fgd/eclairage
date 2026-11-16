package app.lumisite.core.osm

import app.lumisite.core.model.BBox
import app.lumisite.core.geo.LatLon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * v176a-bis, Accueil > Nouveau relevé > « Tracer rectangle »: several site rectangles, each one can be moved, stretched by
 * its corners and sides, or deleted. Usage modelled on the area selection of JOSM (behaviour only, no code: JOSM is GPL).
 */
object SiteRects {
    enum class Handle { MOVE, N, S, E, W, NE, NW, SE, SW }

    data class Hit(val index: Int, val handle: Handle)

    /** Touch tolerance in degrees for [toleranceM] metres around [at]. */
    fun tolerance(at: LatLon, toleranceM: Double): Pair<Double, Double> =
        toleranceM / 111_320.0 to toleranceM / (111_320.0 * max(0.01, cos(Math.toRadians(at.lat))))

    /**
     * What a finger put down at [p] grabs: a corner or side of the [selected] rectangle (within the tolerance), else the
     * inside of a rectangle (the last drawn wins), else nothing (a new rectangle is drawn).
     */
    fun hit(rects: List<BBox>, p: LatLon, tolLat: Double, tolLon: Double, selected: Int?): Hit? {
        rects.getOrNull(selected ?: -1)?.let { r ->
            val nearN = abs(p.lat - r.north) <= tolLat; val nearS = abs(p.lat - r.south) <= tolLat
            val nearE = abs(p.lon - r.east) <= tolLon; val nearW = abs(p.lon - r.west) <= tolLon
            val inLat = p.lat in r.south - tolLat..r.north + tolLat; val inLon = p.lon in r.west - tolLon..r.east + tolLon
            val h = when {
                nearN && nearE -> Handle.NE; nearN && nearW -> Handle.NW
                nearS && nearE -> Handle.SE; nearS && nearW -> Handle.SW
                nearN && inLon -> Handle.N; nearS && inLon -> Handle.S
                nearE && inLat -> Handle.E; nearW && inLat -> Handle.W
                else -> null
            }
            if (h != null) return Hit(selected!!, h)
        }
        for (i in rects.indices.reversed()) if (contains(rects[i], p)) return Hit(i, Handle.MOVE)
        return null
    }

    fun contains(r: BBox, p: LatLon) = p.lat in r.south..r.north && p.lon in r.west..r.east

    /** [r] after the finger moved from [from] to [to] holding [h]; the result is always a valid (normalised) rectangle. */
    fun drag(r: BBox, h: Handle, from: LatLon, to: LatLon): BBox {
        val dLat = to.lat - from.lat; val dLon = to.lon - from.lon
        var s = r.south; var n = r.north; var w = r.west; var e = r.east
        when (h) {
            Handle.MOVE -> { s += dLat; n += dLat; w += dLon; e += dLon }
            Handle.N -> n += dLat
            Handle.S -> s += dLat
            Handle.E -> e += dLon
            Handle.W -> w += dLon
            Handle.NE -> { n += dLat; e += dLon }
            Handle.NW -> { n += dLat; w += dLon }
            Handle.SE -> { s += dLat; e += dLon }
            Handle.SW -> { s += dLat; w += dLon }
        }
        return BBox(min(s, n), min(w, e), max(s, n), max(w, e))
    }

    /** Smallest rectangle containing all of them (the site bbox, the OSM request); null when there is none. */
    fun envelope(rects: List<BBox>): BBox? = if (rects.isEmpty()) null else
        BBox(rects.minOf { it.south }, rects.minOf { it.west }, rects.maxOf { it.north }, rects.maxOf { it.east })

    /** Corner and side-middle handles of [r], drawn on the selected rectangle. */
    fun handles(r: BBox): List<LatLon> {
        val mLat = (r.south + r.north) / 2; val mLon = (r.west + r.east) / 2
        return listOf(
            LatLon(r.north, r.west), LatLon(r.north, mLon), LatLon(r.north, r.east), LatLon(mLat, r.east),
            LatLon(r.south, r.east), LatLon(r.south, mLon), LatLon(r.south, r.west), LatLon(mLat, r.west),
        )
    }
}
