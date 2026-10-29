package app.lumisite.core.map

import app.lumisite.core.model.BBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Base map layers (fiche D). Over-zoom beyond [nativeMaxZoom] is done by scaling tiles, never by downloading. */
enum class Basemap(
    val label: String,
    val nativeMaxZoom: Int,
    /** Tiles may be stored on the device (site bbox only). */
    val cacheAllowed: Boolean,
    /** Explicit "download the zone" allowed by the provider's policy. */
    val bulkAllowed: Boolean,
    val attribution: String,
) {
    OSM("OSM standard", 19, cacheAllowed = true, bulkAllowed = false, attribution = "© les contributeurs d'OpenStreetMap"),
    IGN_PLAN("Plan IGN", 19, cacheAllowed = true, bulkAllowed = true, attribution = "IGN – Plan IGN v2, Etalab 2.0"),
    IGN_ORTHO("Orthophoto IGN", 19, cacheAllowed = true, bulkAllowed = true, attribution = "IGN – Orthophotographies, Etalab 2.0"),
    ESRI("Esri World Imagery", 19, cacheAllowed = false, bulkAllowed = false,
        attribution = "Source : Esri, Maxar, Earthstar Geographics, and the GIS User Community"),
    ;

    companion object {
        const val MAX_ZOOM = 26.0
        const val IGN_WMTS = "https://data.geopf.fr/wmts"
        const val ESRI_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"

        fun ignLayer(b: Basemap) = when (b) {
            IGN_PLAN -> Triple("GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2", "image/png", "png")
            IGN_ORTHO -> Triple("ORTHOIMAGERY.ORTHOPHOTOS", "image/jpeg", "jpg")
            else -> error("not an IGN layer: $b")
        }

        /** WMTS GetTile URL (TILEMATRIXSET=PM_0_19, STYLE=normal). */
        fun ignTileUrl(b: Basemap, z: Int, x: Int, y: Int): String {
            val (layer, format, _) = ignLayer(b)
            return "$IGN_WMTS?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=$layer&STYLE=normal&TILEMATRIXSET=PM_0_19" +
                "&TILEMATRIX=$z&TILEROW=$y&TILECOL=$x&FORMAT=$format"
        }

        fun esriTileUrl(z: Int, x: Int, y: Int) = "$ESRI_URL$z/$y/$x"
    }
}

/** Web Mercator tile arithmetic for the "download the zone" cap. */
object TileMath {
    fun x(lon: Double, z: Int): Int = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    fun y(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt().coerceIn(0, (1 shl z) - 1)
    }

    fun count(b: BBox, zMin: Int, zMax: Int): Long = (zMin..zMax).sumOf { z ->
        (x(b.east, z) - x(b.west, z) + 1).toLong() * (y(b.south, z) - y(b.north, z) + 1)
    }

    /** Highest zMax ≤ [zMax] keeping the count under [maxTiles]; null if even [zMin] exceeds it. */
    fun maxZoomUnder(b: BBox, zMin: Int, zMax: Int, maxTiles: Int): Int? =
        (zMax downTo zMin).firstOrNull { count(b, zMin, it) <= maxTiles }

    /** Width of the visible map in metres at latitude [lat] for a longitude span. */
    fun widthM(lat: Double, lonSpan: Double) = lonSpan * 111_320.0 * cos(Math.toRadians(lat))
}

object ZoneDownload {
    const val Z_MIN = 16
    const val MAX_TILES = 600
}
