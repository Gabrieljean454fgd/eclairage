package app.lumisite.core.survey

import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Session > Annoter > « Déplacer » : the symbol follows the finger keeping the offset where it was grabbed,
 * so it never jumps under the fingertip. Plane coordinates (ENU, metres); the height of the object is kept.
 */
object DragMove {
    fun target(start: Enu, grab: Enu, finger: Enu): Enu = Enu(start.e + finger.e - grab.e, start.n + finger.n - grab.n, start.u)
}

/** Web Mercator screen projection (same formula as the osmdroid / OSM map): pixel ↔ WGS84 for a view centred on [center]. */
class ScreenProjection(val center: LatLon, val zoom: Double, val widthPx: Int, val heightPx: Int, val tilePx: Double = 256.0) {
    private val world = tilePx * 2.0.pow(zoom)
    private fun wx(lon: Double) = (lon + 180.0) / 360.0 * world
    private fun wy(lat: Double) = (1 - ln(tan(PI / 4 + Math.toRadians(lat) / 2)) / PI) / 2 * world
    private val cx = wx(center.lon); private val cy = wy(center.lat)

    fun toPixel(p: LatLon): Pair<Double, Double> = (wx(p.lon) - cx + widthPx / 2.0) to (wy(p.lat) - cy + heightPx / 2.0)

    fun fromPixel(x: Double, y: Double): LatLon {
        val mx = x - widthPx / 2.0 + cx; val my = y - heightPx / 2.0 + cy
        val lon = mx / world * 360.0 - 180.0
        val lat = Math.toDegrees(2 * atan(exp(PI * (1 - 2 * my / world))) - PI / 2)
        return LatLon(lat, lon)
    }

    /** Ground metres per screen pixel at the centre. */
    fun metresPerPixel(): Double = 2 * PI * 6378137.0 * cos(Math.toRadians(center.lat)) / world
}
