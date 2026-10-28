package app.lumisite.core.geo

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** WGS84 geodetic coordinates (EPSG:4326 order lat, lon; ellipsoidal height in metres if known). */
@Serializable
data class LatLon(val lat: Double, val lon: Double, val h: Double? = null)

/** East-North-Up metres in a local tangent plane. */
@Serializable
data class Enu(val e: Double, val n: Double, val u: Double = 0.0)

@Serializable
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    fun norm() = hypot(x, y)
}

/**
 * Description of a coordinate reference system used in the export, so that another program can
 * re-project data (e.g. to EPSG:2154 Lambert-93 for DXF).
 */
@Serializable
data class CrsInfo(
    val id: String,
    val kind: String,
    val definition: String,
    val origin: LatLon? = null,
    val axes: List<String> = emptyList(),
    val units: String = "m",
)

object Wgs84 {
    const val A = 6378137.0
    const val F = 1 / 298.257223563
    const val E2 = F * (2 - F)

    data class Ecef(val x: Double, val y: Double, val z: Double)

    fun toEcef(p: LatLon): Ecef {
        val lat = Math.toRadians(p.lat)
        val lon = Math.toRadians(p.lon)
        val h = p.h ?: 0.0
        val n = A / sqrt(1 - E2 * sin(lat) * sin(lat))
        return Ecef((n + h) * cos(lat) * cos(lon), (n + h) * cos(lat) * sin(lon), (n * (1 - E2) + h) * sin(lat))
    }

    /** Bowring-style iterative inverse, sub-millimetre for terrestrial heights. */
    fun fromEcef(e: Ecef): LatLon {
        val lon = atan2(e.y, e.x)
        val p = hypot(e.x, e.y)
        var lat = atan2(e.z, p * (1 - E2))
        var h = 0.0
        repeat(6) {
            val n = A / sqrt(1 - E2 * sin(lat) * sin(lat))
            h = p / cos(lat) - n
            lat = atan2(e.z, p * (1 - E2 * n / (n + h)))
        }
        return LatLon(Math.toDegrees(lat), Math.toDegrees(lon), h)
    }
}

/**
 * Local tangent plane (ENU) anchored at [origin]. Exact ECEF-based conversion (no flat-earth approx),
 * suitable for sites of a few km.
 */
class LocalTangentPlane(val origin: LatLon) {
    private val o = Wgs84.toEcef(origin)
    private val lat = Math.toRadians(origin.lat)
    private val lon = Math.toRadians(origin.lon)
    private val sLat = sin(lat); private val cLat = cos(lat)
    private val sLon = sin(lon); private val cLon = cos(lon)

    fun toEnu(p: LatLon): Enu {
        val e = Wgs84.toEcef(p)
        val dx = e.x - o.x; val dy = e.y - o.y; val dz = e.z - o.z
        return Enu(
            e = -sLon * dx + cLon * dy,
            n = -sLat * cLon * dx - sLat * sLon * dy + cLat * dz,
            u = cLat * cLon * dx + cLat * sLon * dy + sLat * dz,
        )
    }

    fun toLatLon(p: Enu): LatLon {
        val dx = -sLon * p.e - sLat * cLon * p.n + cLat * cLon * p.u
        val dy = cLon * p.e - sLat * sLon * p.n + cLat * sLon * p.u
        val dz = cLat * p.n + sLat * p.u
        // h is always returned (ellipsoidal): a point at u=0 lies below the ellipsoid away from the origin.
        return Wgs84.fromEcef(Wgs84.Ecef(o.x + dx, o.y + dy, o.z + dz))
    }

    fun crsInfo(id: String = "SITE_ENU") = CrsInfo(
        id = id,
        kind = "LOCAL_TANGENT_PLANE_ENU",
        definition = "+proj=topocentric +ellps=WGS84 +lat_0=${origin.lat} +lon_0=${origin.lon} +h_0=${origin.h ?: 0.0}",
        origin = origin,
        axes = listOf("E (x, east)", "N (y, north)", "U (z, up)"),
    )
}

/**
 * 2D similarity transform: p' = s * R(theta) * p + t. Used to map the horizontal plane of the AR session
 * frame onto the site ENU plane (x=E, y=N).
 */
@Serializable
data class Similarity2D(val scale: Double, val rotationRad: Double, val tx: Double, val ty: Double) {
    fun apply(p: Vec2): Vec2 {
        val c = cos(rotationRad) * scale; val s = sin(rotationRad) * scale
        return Vec2(c * p.x - s * p.y + tx, s * p.x + c * p.y + ty)
    }

    fun then(next: Similarity2D): Similarity2D {
        // next(this(p))
        val r = rotationRad + next.rotationRad
        val sc = scale * next.scale
        val t = next.apply(Vec2(tx, ty))
        return Similarity2D(sc, r, t.x, t.y)
    }

    companion object {
        val IDENTITY = Similarity2D(1.0, 0.0, 0.0, 0.0)

        /** One known point plus a rotation; scale is assumed (default 1, AR tracking is metric). */
        fun fromPointAndRotation(local: Vec2, target: Vec2, rotationRad: Double, scale: Double = 1.0): Similarity2D {
            val partial = Similarity2D(scale, rotationRad, 0.0, 0.0).apply(local)
            return Similarity2D(scale, rotationRad, target.x - partial.x, target.y - partial.y)
        }

        /** Two point correspondences fully determine scale, rotation and translation. */
        fun fromTwoPoints(localA: Vec2, localB: Vec2, targetA: Vec2, targetB: Vec2, fixedScale: Double? = null): Similarity2D {
            val dl = localB - localA
            val dt = targetB - targetA
            require(dl.norm() > 1e-6) { "Local points are identical; cannot determine orientation" }
            val rot = atan2(dt.y, dt.x) - atan2(dl.y, dl.x)
            val scale = fixedScale ?: (dt.norm() / dl.norm())
            return fromPointAndRotation(localA, targetA, rot, scale)
        }
    }
}

object Angles {
    /** Compass heading (degrees clockwise from north) -> math angle in ENU plane (radians CCW from east). */
    fun headingDegToEnuRad(headingDeg: Double): Double = Math.toRadians(90.0 - headingDeg)
    fun enuRadToHeadingDeg(rad: Double): Double = ((90.0 - Math.toDegrees(rad)) % 360.0 + 360.0) % 360.0
    fun wrapPi(a: Double): Double { var x = a; while (x > Math.PI) x -= 2 * Math.PI; while (x < -Math.PI) x += 2 * Math.PI; return x }
}
