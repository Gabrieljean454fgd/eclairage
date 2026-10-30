package app.lumisite.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lumisite.core.map.Basemap
import app.lumisite.core.map.TileCachePolicy
import app.lumisite.core.model.BBox
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.IFilesystemCache
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.modules.MapTileFileStorageProviderBase
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

/** Tile cache following [TileCachePolicy] (satellite limited to the site, plan layers cached normally). */
class SiteTileCache(private val basemap: Basemap, @Volatile var bbox: BBox?) : IFilesystemCache {
    private val sql = if (basemap.cacheAllowed) SqlTileWriter() else null

    override fun saveFile(src: ITileSource, idx: Long, stream: InputStream, expiration: Long?): Boolean =
        sql != null && TileCachePolicy.shouldStore(basemap, bbox, MapTileIndex.getZoom(idx), MapTileIndex.getX(idx), MapTileIndex.getY(idx)) &&
            sql.saveFile(src, idx, stream, expiration)
    override fun exists(src: ITileSource, idx: Long) = sql?.exists(src, idx) ?: false
    override fun onDetach() { sql?.onDetach() }
    override fun remove(src: ITileSource, idx: Long) = sql?.remove(src, idx) ?: false
    override fun getExpirationTimestamp(src: ITileSource, idx: Long): Long? = sql?.getExpirationTimestamp(src, idx)
    override fun loadTile(src: ITileSource, idx: Long): Drawable? = sql?.loadTile(src, idx)
}

/** Searches at most [TileCachePolicy.MAX_APPROX_LEVELS] lower zooms for a missing tile (osmdroid #1106). */
private class BoundedApproximater : MapTileApproximater() {
    override fun approximateTileFromLowerZoom(idx: Long): Bitmap? {
        for (d in TileCachePolicy.approximationLevels(MapTileIndex.getZoom(idx))) approximateTileFromLowerZoom(idx, d)?.let { return it }
        return null
    }
}

class SiteTileProvider(ctx: Context, src: ITileSource, cache: IFilesystemCache) : MapTileProviderBasic(ctx, src, cache) {
    override fun createApproximater(
        a: MapTileFileStorageProviderBase?, b: MapTileFileStorageProviderBase?, c: MapTileFileStorageProviderBase?,
    ): MapTileApproximater = BoundedApproximater().apply { a?.let(::addProvider); b?.let(::addProvider); c?.let(::addProvider) }
}
