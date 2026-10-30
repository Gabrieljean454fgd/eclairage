package app.lumisite.android.ui

import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.lumisite.core.compute.CorrectedPoint
import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature
import org.osmdroid.events.MapEventsReceiver
import android.os.Build
import android.view.MotionEvent
import android.widget.Magnifier
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumisite.core.map.Basemap
import app.lumisite.core.map.TileMath
import app.lumisite.core.map.ZoneDownload
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay

data class MapPoint(val pos: LatLon, val title: String, val color: Int, val id: String? = null)
data class MapLine(val a: LatLon, val b: LatLon, val color: Int, val title: String? = null)

/** Everything the map can draw. Trajectories are estimates and drawn as such (no uncertainty is invented). */
data class MapContent(
    val origin: LatLon? = null,
    val features: List<OsmFeature> = emptyList(),
    val selectedFeatureRef: String? = null,
    val trajectory: List<List<CorrectedPoint>> = emptyList(),
    val uncorrected: List<List<CorrectedPoint>> = emptyList(),
    val lines: List<MapLine> = emptyList(),
    val points: List<MapPoint> = emptyList(),
    val current: LatLon? = null,
    val picked: LatLon? = null,
    /** Heading sector of the current position (triangle, site frame), drawn only when tracked and calibrated. */
    val headingSector: List<LatLon> = emptyList(),
    /** When set, the map keeps this point centred ("Suivre"). */
    val follow: LatLon? = null,
    /** Site rectangle (bbox), drawn as a dashed frame. */
    val rect: BBox? = null,
)

private fun gp(p: LatLon) = GeoPoint(p.lat, p.lon)

@Composable
fun OsmMap(
    modifier: Modifier,
    center: LatLon,
    zoom: Double,
    content: MapContent,
    recenterKey: Any? = null,
    /** When set, the first display (and each recenter) frames this rectangle instead of center/zoom. */
    fitBox: app.lumisite.core.model.BBox? = null,
    onTap: (LatLon) -> Unit,
    onFeatureTap: ((OsmFeature) -> Unit)? = null,
    onPointTap: ((String) -> Unit)? = null,
    /** Site rectangle: tiles are cached only inside it, and "Télécharger la zone" downloads it. */
    siteBbox: BBox? = null,
    /** Central cross: the map is moved under it (placement aid). */
    reticle: Boolean = false,
    onViewport: ((center: LatLon, widthM: Double) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val basemap = MapPrefs.basemap
    var widthM by remember { mutableStateOf<Double?>(null) }
    var menu by remember { mutableStateOf(false) }
    val map = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            controller.setZoom(zoom)
            controller.setCenter(gp(center))
            // Over-zoom up to 26: tiles are never requested above the source's native zoom, osmdroid scales lower ones.
            minZoomLevel = 3.0; maxZoomLevel = Basemap.MAX_ZOOM
            isTilesScaledToDpi = false
        }
    }
    val holder = remember { arrayOf<Any?>(null, null) }
    if (holder[1] != basemap) {
        holder[1] = basemap
        val src = tileSourceFor(basemap)
        map.setTileProvider(SiteTileProvider(ctx.applicationContext, src, SiteTileCache(basemap, siteBbox)))
        map.setTileSource(src)
    }
    (map.tileProvider.tileWriter as? SiteTileCache)?.bbox = siteBbox
    DisposableEffect(Unit) {
        val l = object : MapListener {
            fun report(): Boolean {
                val bb = map.boundingBox
                val w = TileMath.widthM(bb.centerLatitude, bb.longitudeSpanWithDateLine)
                widthM = w
                onViewport?.invoke(LatLon(map.mapCenter.latitude, map.mapCenter.longitude), w)
                return false
            }
            override fun onScroll(event: ScrollEvent?) = report()
            override fun onZoom(event: ZoomEvent?) = report()
        }
        map.addMapListener(l)
        val magnifier = if (Build.VERSION.SDK_INT >= 28) Magnifier.Builder(map).setSize(300, 200).setInitialZoom(2f).build() else null
        map.setOnTouchListener { _, e ->
            // Magnifier above the finger while placing at room scale (zoom ≥ 20).
            if (magnifier != null && Build.VERSION.SDK_INT >= 28 && map.zoomLevelDouble >= 20.0 && e.pointerCount == 1) when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> magnifier.show(e.x, e.y)
                else -> magnifier.dismiss()
            } else if (magnifier != null && Build.VERSION.SDK_INT >= 28) magnifier.dismiss()
            false
        }
        onDispose { map.removeMapListener(l) }
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }
    if (holder[0] != recenterKey) {
        holder[0] = recenterKey
        val c = gp(center)
        // Before the first layout osmdroid ignores setCenter/setZoom; apply after layout.
        val apply = {
            if (fitBox != null) map.zoomToBoundingBox(BoundingBox(fitBox.north, fitBox.east, fitBox.south, fitBox.west), false, 48)
            else { map.controller.setZoom(zoom); map.controller.setCenter(c) }
        }
        if (map.isLayoutOccurred) map.post { apply() } else map.addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
    }
    val tone = LocalMapTone.current
    Box(modifier.clipToBounds()) {
    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { mv ->
        mv.overlayManager.tilesOverlay.setColorFilter(MapFilters.of(tone))
        mv.overlays.clear()
        mv.overlays += MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { onTap(LatLon(p.latitude, p.longitude)); return true }
            override fun longPressHelper(p: GeoPoint) = false
        })
        content.features.forEach { f ->
            if (f.geometry.size >= 3 && f.category in setOf("building", "indoor")) {
                val sel = f.ref == content.selectedFeatureRef
                mv.overlays += Polygon(mv).apply {
                    points = f.geometry.map(::gp)
                    holes = f.holes.map { r -> r.map(::gp) }
                    fillPaint.color = if (sel) Color.argb(90, 255, 160, 0) else Color.argb(40, 60, 90, 200)
                    outlinePaint.color = if (sel) Color.rgb(230, 120, 0) else Color.rgb(60, 90, 200)
                    outlinePaint.strokeWidth = if (sel) 5f else 2f
                    title = f.tags["name"] ?: f.ref
                    setOnClickListener { _, _, _ -> onFeatureTap?.invoke(f); onFeatureTap != null }
                }
            } else if (f.geometry.size >= 2 && f.category == "path") {
                mv.overlays += Polyline(mv).apply { setPoints(f.geometry.map(::gp)); outlinePaint.color = Color.argb(120, 120, 120, 120); outlinePaint.strokeWidth = 2f }
            } else if (f.geometry.size == 1) {
                mv.overlays += Marker(mv).apply {
                    position = gp(f.geometry[0]); title = "${f.category} ${f.ref}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = dot(mv, if (f.category == "street_lamp") Color.rgb(250, 200, 0) else Color.rgb(120, 120, 120), 14)
                }
            }
        }
        content.features.forEach { f ->
            if (f.category == "building") f.outers.forEach { r ->
                mv.overlays += Polygon(mv).apply { points = r.map(::gp); fillPaint.color = Color.argb(40, 60, 90, 200); outlinePaint.color = Color.rgb(60, 90, 200); outlinePaint.strokeWidth = 2f }
            }
        }
        content.rect?.let { b ->
            mv.overlays += Polyline(mv).apply {
                setPoints(listOf(GeoPoint(b.south, b.west), GeoPoint(b.south, b.east), GeoPoint(b.north, b.east), GeoPoint(b.north, b.west), GeoPoint(b.south, b.west)))
                outlinePaint.color = Color.rgb(220, 0, 120); outlinePaint.strokeWidth = 4f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 10f), 0f)
                title = "Rectangle du site"
            }
        }
        content.uncorrected.forEach { seg ->
            if (seg.size >= 2) mv.overlays += Polyline(mv).apply {
                setPoints(seg.mapNotNull { it.latLon?.let(::gp) })
                outlinePaint.color = Color.argb(150, 130, 130, 130); outlinePaint.strokeWidth = 4f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                title = "Sans recalage (calibration seule)"
            }
        }
        content.trajectory.forEach { seg ->
            if (seg.size >= 2) mv.overlays += Polyline(mv).apply {
                setPoints(seg.mapNotNull { it.latLon?.let(::gp) })
                outlinePaint.color = Color.rgb(0, 160, 90); outlinePaint.strokeWidth = 6f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                title = "Trajectoire estimée"
            }
        }
        content.lines.forEach { l ->
            mv.overlays += Polyline(mv).apply { setPoints(listOf(gp(l.a), gp(l.b))); outlinePaint.color = l.color; outlinePaint.strokeWidth = 5f; title = l.title }
        }
        content.origin?.let { mv.overlays += Marker(mv).apply { position = gp(it); title = "Origine du site"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, Color.BLACK, 16) } }
        content.points.forEach { p ->
            mv.overlays += Marker(mv).apply {
                position = gp(p.pos); title = p.title; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, p.color, 20)
                val id = p.id
                setOnMarkerClickListener { m, _ -> if (id != null && onPointTap != null) onPointTap(id) else m.showInfoWindow(); true }
            }
        }
        if (content.headingSector.size >= 3) mv.overlays += Polygon(mv).apply {
            points = content.headingSector.map(::gp)
            fillPaint.color = Color.argb(90, 0, 120, 255); outlinePaint.color = Color.rgb(0, 120, 255); outlinePaint.strokeWidth = 2f
            setOnClickListener { _, _, _ -> false }
        }
        content.picked?.let { mv.overlays += Marker(mv).apply { position = gp(it); title = "Point choisi" } }
        content.current?.let { mv.overlays += Marker(mv).apply { position = gp(it); title = "Position estimée"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, Color.rgb(0, 120, 255), 26) } }
        content.follow?.let { mv.controller.setCenter(gp(it)) }
        mv.overlays += ScaleBarOverlay(mv)
        mv.overlays += CopyrightOverlay(ctx).apply { setTextSize(12) }
        mv.invalidate()
    })
    if (reticle) Text("+", Modifier.align(Alignment.Center), fontSize = 40.sp, color = androidx.compose.ui.graphics.Color.Red,
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.White, blurRadius = 6f)))
    widthM?.let {
        Text(if (it < 100) "largeur visible : %.1f m".format(it) else "largeur visible : %.0f m".format(it),
            Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 28.dp).background(androidx.compose.ui.graphics.Color(0xCCFFFFFF)).padding(4.dp),
            style = MaterialTheme.typography.labelSmall)
    }
    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.background(androidx.compose.ui.graphics.Color(0xCCFFFFFF))) { Text("Fond : ${basemap.label}") }
        DropdownMenu(menu, { menu = false }) {
            Basemap.entries.forEach { b ->
                val ok = b != Basemap.ESRI || MapPrefs.esriAccepted
                DropdownMenuItem(
                    text = { Text((if (b == basemap) "✓ " else "") + b.label + (if (!ok) " (conditions Esri non acceptées)" else "")) },
                    enabled = ok, onClick = { MapPrefs.basemap = b; MapPrefs.save(ctx); menu = false },
                )
            }
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(MapPrefs.esriAccepted, null); Text("Le projet accepte les conditions Esri") } },
                onClick = {
                    MapPrefs.esriAccepted = !MapPrefs.esriAccepted
                    if (!MapPrefs.esriAccepted && MapPrefs.basemap == Basemap.ESRI) MapPrefs.basemap = Basemap.OSM
                    MapPrefs.save(ctx)
                },
            )
            if (siteBbox != null) DropdownMenuItem(
                text = { Text(when {
                    basemap.bulkAllowed -> "Télécharger la zone (${basemap.label})"
                    basemap == Basemap.OSM -> "OSM : pas de téléchargement en masse (règles OSM) ; la zone se met en cache en la consultant"
                    else -> "${basemap.label} : en ligne seulement, aucun cache"
                }) },
                enabled = basemap.bulkAllowed,
                onClick = { menu = false; downloadZone(map, basemap, siteBbox) },
            )
        }
    }
    }
}

private fun downloadZone(mv: MapView, basemap: Basemap, b: BBox) {
    val ctx = mv.context
    val zMax = TileMath.maxZoomUnder(b, ZoneDownload.Z_MIN, basemap.nativeMaxZoom, ZoneDownload.MAX_TILES)
    if (zMax == null) { Toast.makeText(ctx, "Zone trop grande pour le téléchargement (plus de ${ZoneDownload.MAX_TILES} tuiles).", Toast.LENGTH_LONG).show(); return }
    val n = TileMath.count(b, ZoneDownload.Z_MIN, zMax)
    try {
        CacheManager(mv).downloadAreaAsync(ctx, BoundingBox(b.north, b.east, b.south, b.west), ZoneDownload.Z_MIN, zMax)
        Toast.makeText(ctx, "Téléchargement de $n tuiles (zooms ${ZoneDownload.Z_MIN} à $zMax)…", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(ctx, "Téléchargement impossible : ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun dot(mv: MapView, color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
    val px = (sizeDp * mv.resources.displayMetrics.density).toInt()
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color); setStroke(3, Color.WHITE); setSize(px, px)
    }
}
