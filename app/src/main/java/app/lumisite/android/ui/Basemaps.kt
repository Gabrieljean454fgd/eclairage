package app.lumisite.android.ui

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lumisite.core.map.Basemap
import app.lumisite.core.map.TileMath
import app.lumisite.core.map.ZoneDownload
import app.lumisite.core.model.BBox
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Base map choice, kept between launches. */
object MapPrefs {
    var basemap by mutableStateOf(Basemap.OSM)
    /** Esri World Imagery is offered only when the project accepts Esri's terms. */
    var esriAccepted by mutableStateOf(false)

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("map", Context.MODE_PRIVATE)
        esriAccepted = p.getBoolean("esri", false)
        basemap = runCatching { Basemap.valueOf(p.getString("basemap", "OSM")!!) }.getOrDefault(Basemap.OSM)
        if (basemap == Basemap.ESRI && !esriAccepted) basemap = Basemap.OSM
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("map", Context.MODE_PRIVATE).edit().putBoolean("esri", esriAccepted).putString("basemap", basemap.name).apply()
    }
}

private val today get() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

fun tileSourceFor(b: Basemap): ITileSource = when (b) {
    Basemap.OSM -> TileSourceFactory.MAPNIK
    Basemap.IGN_PLAN, Basemap.IGN_ORTHO -> {
        val (_, _, ext) = Basemap.ignLayer(b)
        object : OnlineTileSourceBase("IGN-${b.name}", 0, b.nativeMaxZoom, 256, ".$ext", arrayOf(Basemap.IGN_WMTS), "${b.attribution}, $today") {
            override fun getTileURLString(idx: Long) = Basemap.ignTileUrl(b, MapTileIndex.getZoom(idx), MapTileIndex.getX(idx), MapTileIndex.getY(idx))
        }
    }
    Basemap.ESRI -> object : OnlineTileSourceBase("Esri-WorldImagery", 0, b.nativeMaxZoom, 256, ".jpg", arrayOf(Basemap.ESRI_URL), b.attribution,
        TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE)) {
        override fun getTileURLString(idx: Long) = Basemap.esriTileUrl(MapTileIndex.getZoom(idx), MapTileIndex.getX(idx), MapTileIndex.getY(idx))
    }
}

/**
 * Stores downloaded tiles only inside the site rectangle and zooms [ZoneDownload.Z_MIN]..native (fiche D); nothing at all
 * when the base map forbids caching (Esri) or when no site rectangle is known.
 */
class SiteTileCache(private val basemap: Basemap, @Volatile var bbox: BBox?) : IFilesystemCache {
    private val sql = if (basemap.cacheAllowed) SqlTileWriter() else null

    private fun inSite(idx: Long): Boolean {
        val b = bbox ?: return false
        val z = MapTileIndex.getZoom(idx)
        if (z < ZoneDownload.Z_MIN || z > basemap.nativeMaxZoom) return false
        val x = MapTileIndex.getX(idx); val y = MapTileIndex.getY(idx)
        return x in TileMath.x(b.west, z)..TileMath.x(b.east, z) && y in TileMath.y(b.north, z)..TileMath.y(b.south, z)
    }

    override fun saveFile(src: ITileSource, idx: Long, stream: InputStream, expiration: Long?): Boolean =
        sql != null && inSite(idx) && sql.saveFile(src, idx, stream, expiration)
    override fun exists(src: ITileSource, idx: Long) = sql?.exists(src, idx) ?: false
    override fun onDetach() { sql?.onDetach() }
    override fun remove(src: ITileSource, idx: Long) = sql?.remove(src, idx) ?: false
    override fun getExpirationTimestamp(src: ITileSource, idx: Long): Long? = sql?.getExpirationTimestamp(src, idx)
    override fun loadTile(src: ITileSource, idx: Long): Drawable? = sql?.loadTile(src, idx)
}
