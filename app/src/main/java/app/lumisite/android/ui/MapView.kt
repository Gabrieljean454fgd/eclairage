package app.lumisite.android.ui

import android.graphics.Color
import androidx.compose.ui.platform.testTag
import android.graphics.Paint
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

data class MapPoint(val pos: LatLon, val title: String, val color: Int, val id: String? = null, val dashed: Boolean = false)
/** Arrow (position + direction) drawn at a fixed screen size; headingDeg null = dot. Grey when stale. */
data class MapArrow(val pos: LatLon, val headingDeg: Double?, val color: Int, val title: String)

/** Single-finger gesture phases captured by the map when [OsmMap] gets an onFinger callback (two fingers move the map). */
enum class FingerPhase { DOWN, MOVE, UP, CANCEL }

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
    val selectedFeatureRefs: Set<String> = emptySet(),
    /** Elastic preview of the drawing in progress (follows the finger). */
    val preview: List<LatLon> = emptyList(),
    val arrows: List<MapArrow> = emptyList(),
    /** v176a-bis: several site rectangles (Nouveau site); the selected one is drawn solid with its stretch handles. */
    val rects: List<BBox> = emptyList(),
    val selectedRect: Int? = null,
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
    /** Metres per dp of the visible map (touch tolerances). */
    onScale: ((metersPerDp: Double) -> Unit)? = null,
    /** When set, ONE finger draws (DOWN/MOVE/UP reported here) and TWO fingers pan/zoom the map. */
    onFinger: ((FingerPhase, LatLon) -> Unit)? = null,
) {
    val fingerCb = rememberUpdatedState(onFinger)
    val scaleCb = rememberUpdatedState(onScale)
    val tapCb = rememberUpdatedState(onTap)
    val featureCb = rememberUpdatedState(onFeatureTap)
    val pointCb = rememberUpdatedState(onPointTap)
    val layers = remember { MapLayers() }
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
    val gesture = remember { Gesture() }
    if (holder[1] != basemap) {
        holder[1] = basemap
        val src = tileSourceFor(basemap)
        map.setTileProvider(SiteTileProvider(ctx.applicationContext, src, SiteTileCache(basemap, siteBbox)))
        map.setTileSource(src)
    }
    (map.tileProvider.tileWriter as? SiteTileCache)?.bbox = siteBbox
    var cadastreDrawn by remember { mutableStateOf(false) }
    val cadastreOverlay = remember {
        val provider = org.osmdroid.tileprovider.MapTileProviderBasic(ctx.applicationContext, cadastreTileSource())
        // Without the map's handler a downloaded cadastre tile never triggers a redraw: over an already cached base map
        // (OSM) nothing else repaints, so the parcels stayed invisible although the tiles answered HTTP 200.
        provider.tileRequestCompleteHandlers.add(map.tileRequestCompleteHandler)
        object : org.osmdroid.views.overlay.TilesOverlay(provider, ctx.applicationContext) {
            // Called only for real tiles (the loading tile is transparent → null): proof that parcels were painted.
            override fun onTileReadyToDraw(c: android.graphics.Canvas, tile: android.graphics.drawable.Drawable, r: android.graphics.Rect) {
                super.onTileReadyToDraw(c, tile, r)
                if (!cadastreDrawn) map.post { cadastreDrawn = MapPrefs.cadastre }
            }
        }.apply { loadingBackgroundColor = android.graphics.Color.TRANSPARENT; setLoadingLineColor(android.graphics.Color.TRANSPARENT) }
    }
    DisposableEffect(Unit) {
        val l = object : MapListener {
            fun report(): Boolean {
                val bb = map.boundingBox
                val w = TileMath.widthM(bb.centerLatitude, bb.longitudeSpanWithDateLine)
                widthM = w
                onViewport?.invoke(LatLon(map.mapCenter.latitude, map.mapCenter.longitude), w)
                val dpW = map.width / map.resources.displayMetrics.density
                if (dpW > 0) scaleCb.value?.invoke(w / dpW)
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
            val cb = fingerCb.value
            if (cb == null || gesture.reentry) return@setOnTouchListener false
            fun ll(x: Float, y: Float) = map.projection.fromPixels(x.toInt(), y.toInt()).let { LatLon(it.latitude, it.longitude) }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { gesture.multi = false; gesture.x = e.x; gesture.y = e.y; cb(FingerPhase.DOWN, ll(e.x, e.y)); true }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (!gesture.multi) {
                        // Second finger: the drawing gesture is cancelled and handed to the map (synthetic DOWN, then this event).
                        gesture.multi = true; cb(FingerPhase.CANCEL, ll(gesture.x, gesture.y))
                        val down = MotionEvent.obtain(e.downTime, e.eventTime, MotionEvent.ACTION_DOWN, e.getX(0), e.getY(0), 0)
                        gesture.reentry = true
                        try { map.dispatchTouchEvent(down) } finally { gesture.reentry = false; down.recycle() }
                    }
                    false
                }
                else -> if (gesture.multi) false else {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_MOVE -> cb(FingerPhase.MOVE, ll(e.x, e.y))
                        MotionEvent.ACTION_UP -> cb(FingerPhase.UP, ll(e.x, e.y))
                        MotionEvent.ACTION_CANCEL -> cb(FingerPhase.CANCEL, ll(e.x, e.y))
                    }
                    true
                }
            }
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
        // v176b: persistent layers. Each group of overlays is rebuilt only when its own content changed (value equality);
        // the overlay list is reassembled only then. The phone position moves the existing marker (≤ 10 Hz, see SessionController).
        val L = layers
        var changed = false
        fun group(name: String, key: Any?, build: () -> List<org.osmdroid.views.overlay.Overlay>): List<org.osmdroid.views.overlay.Overlay> {
            val g = L.groups.getOrPut(name) { LayerGroup() }
            if (!g.built || g.key != key) { g.key = key; g.overlays = build(); g.built = true; changed = true }
            return g.overlays
        }
        val all = ArrayList<org.osmdroid.views.overlay.Overlay>()
        // Cadastre (D12): first in the list = drawn right above the base tiles, whatever the base map.
        all += group("cadastre", MapPrefs.cadastre) { if (MapPrefs.cadastre) listOf(cadastreOverlay) else emptyList() }
        all += group("events", Unit) { listOf(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { tapCb.value(LatLon(p.latitude, p.longitude)); return true }
            override fun longPressHelper(p: GeoPoint) = false
        })) }
        all += group("features", Triple(content.features, content.selectedFeatureRef, content.selectedFeatureRefs)) { buildList {
            content.features.forEach { f ->
                if (f.geometry.size >= 3 && f.category in setOf("building", "indoor")) {
                    val sel = f.ref == content.selectedFeatureRef || f.ref in content.selectedFeatureRefs
                    add(Polygon(mv).apply {
                        points = f.geometry.map(::gp)
                        holes = f.holes.map { r -> r.map(::gp) }
                        fillPaint.color = if (sel) Color.argb(90, 255, 160, 0) else Color.argb(40, 60, 90, 200)
                        outlinePaint.color = if (sel) Color.rgb(230, 120, 0) else Color.rgb(60, 90, 200)
                        outlinePaint.strokeWidth = if (sel) 5f else 2f
                        title = f.tags["name"] ?: f.ref
                        setOnClickListener { _, _, _ -> val cb = featureCb.value; cb?.invoke(f); cb != null }
                    })
                } else if (f.geometry.size >= 2 && f.category == "path") {
                    add(Polyline(mv).apply { setPoints(f.geometry.map(::gp)); outlinePaint.color = Color.argb(120, 120, 120, 120); outlinePaint.strokeWidth = 2f })
                } else if (f.geometry.size == 1) {
                    add(Marker(mv).apply {
                        position = gp(f.geometry[0]); title = "${f.category} ${f.ref}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = dot(mv, if (f.category == "street_lamp") Color.rgb(250, 200, 0) else Color.rgb(120, 120, 120), 14)
                    })
                }
            }
            content.features.forEach { f ->
                if (f.category == "building") f.outers.forEach { r ->
                    add(Polygon(mv).apply { points = r.map(::gp); fillPaint.color = Color.argb(40, 60, 90, 200); outlinePaint.color = Color.rgb(60, 90, 200); outlinePaint.strokeWidth = 2f })
                }
            }
        } }
        all += group("rect", content.rect) { listOfNotNull(content.rect?.let { b ->
            Polyline(mv).apply {
                setPoints(listOf(GeoPoint(b.south, b.west), GeoPoint(b.south, b.east), GeoPoint(b.north, b.east), GeoPoint(b.north, b.west), GeoPoint(b.south, b.west)))
                outlinePaint.color = Color.rgb(220, 0, 120); outlinePaint.strokeWidth = 4f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 10f), 0f)
                title = "Rectangle du site"
            }
        }) }
        all += group("rects", content.rects to content.selectedRect) { buildList {
            content.rects.forEachIndexed { i, b ->
                val sel = i == content.selectedRect
                add(Polyline(mv).apply {
                    setPoints(listOf(GeoPoint(b.south, b.west), GeoPoint(b.south, b.east), GeoPoint(b.north, b.east), GeoPoint(b.north, b.west), GeoPoint(b.south, b.west)))
                    outlinePaint.color = if (sel) Color.rgb(0, 110, 230) else Color.rgb(220, 0, 120)
                    outlinePaint.strokeWidth = if (sel) 6f else 4f
                    if (!sel) outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 10f), 0f)
                    title = "Rectangle ${i + 1}"
                })
            }
            content.rects.getOrNull(content.selectedRect ?: -1)?.let { b ->
                app.lumisite.core.osm.SiteRects.handles(b).forEach { h ->
                    add(Marker(mv).apply { position = gp(h); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, Color.rgb(0, 110, 230), 22); setOnMarkerClickListener { _, _ -> false } })
                }
            }
        } }
        all += group("uncorrected", content.uncorrected) { content.uncorrected.filter { it.size >= 2 }.map { seg ->
            Polyline(mv).apply {
                setPoints(seg.mapNotNull { it.latLon?.let(::gp) })
                outlinePaint.color = Color.argb(150, 130, 130, 130); outlinePaint.strokeWidth = 4f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                title = "Sans recalage (calibration seule)"
            }
        } }
        // Trajectory: one Polyline per segment, kept and updated in place; long segments are simplified for display only.
        if (L.trajectory != content.trajectory) {
            L.trajectory = content.trajectory
            val segs = content.trajectory.filter { it.size >= 2 }
            while (L.trajLines.size > segs.size) { L.trajLines.removeAt(L.trajLines.size - 1); L.trajSimplifiers.removeAt(L.trajSimplifiers.size - 1); changed = true }
            segs.forEachIndexed { i, seg ->
                if (i >= L.trajLines.size) {
                    L.trajLines += Polyline(mv).apply {
                        outlinePaint.color = Color.rgb(0, 160, 90); outlinePaint.strokeWidth = 6f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        title = "Trajectoire estimée"
                    }
                    L.trajSimplifiers += app.lumisite.core.map.GrowingSegmentSimplifier()
                    L.trajDrawn += null
                    changed = true
                }
                if (L.trajDrawn.getOrNull(i) !== seg) {
                    L.trajDrawn[i] = seg
                    L.trajLines[i].setPoints(L.trajSimplifiers[i].render(seg).mapNotNull { it.latLon?.let(::gp) })
                    L.dirty = true
                }
            }
            while (L.trajDrawn.size > L.trajLines.size) L.trajDrawn.removeAt(L.trajDrawn.size - 1)
        }
        all += L.trajLines
        all += group("lines", content.lines) { content.lines.map { l ->
            Polyline(mv).apply { setPoints(listOf(gp(l.a), gp(l.b))); outlinePaint.color = l.color; outlinePaint.strokeWidth = 5f; title = l.title }
        } }
        all += group("origin", content.origin) { listOfNotNull(content.origin?.let { Marker(mv).apply { position = gp(it); title = "Origine du site"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, Color.BLACK, 16) } }) }
        all += group("points", content.points) { content.points.map { p ->
            Marker(mv).apply {
                position = gp(p.pos); title = p.title; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = if (p.dashed) dashedDot(mv, p.color, 20) else dot(mv, p.color, 20)
                val id = p.id
                setOnMarkerClickListener { m, _ -> val cb = pointCb.value; if (id != null && cb != null) cb(id) else m.showInfoWindow(); true }
            }
        } }
        all += group("sector", content.headingSector) { if (content.headingSector.size >= 3) listOf(Polygon(mv).apply {
            points = content.headingSector.map(::gp)
            fillPaint.color = Color.argb(90, 0, 120, 255); outlinePaint.color = Color.rgb(0, 120, 255); outlinePaint.strokeWidth = 2f
            setOnClickListener { _, _, _ -> false }
        }) else emptyList() }
        all += group("preview", content.preview) { if (content.preview.size >= 2) listOf(Polyline(mv).apply {
            setPoints(content.preview.map(::gp))
            outlinePaint.color = Color.rgb(255, 87, 34); outlinePaint.strokeWidth = 5f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 8f), 0f)
            title = "Tracé en cours"
        }) else emptyList() }
        // Arrows (phone position + direction): markers are kept and moved; icons are cached per 2° of heading and colour.
        val arrows = content.arrows
        if (L.arrowsKey != arrows) {
            L.arrowsKey = arrows
            if (L.arrowMarkers.size != arrows.size) {
                L.arrowMarkers.clear()
                arrows.forEach { _ -> L.arrowMarkers += Marker(mv).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) } }
                changed = true
            }
            arrows.forEachIndexed { i, a ->
                val m = L.arrowMarkers[i]
                m.position = gp(a.pos); m.title = a.title
                val bucket = a.headingDeg?.let { ((it / 2.0).roundToInt() * 2) % 360 }
                m.icon = L.arrowIcons.getOrPut(bucket to a.color) { arrowIcon(mv, bucket?.toDouble(), a.color) }
            }
            L.dirty = true
        }
        all += L.arrowMarkers
        // The picked pin never intercepts taps: an object lying under it stays directly selectable.
        all += group("picked", content.picked) { listOfNotNull(content.picked?.let { Marker(mv).apply { position = gp(it); title = "Point choisi"; setOnMarkerClickListener { _, _ -> false } } }) }
        val cur = content.current
        if (cur == null) L.currentMarker = null
        else {
            val m = L.currentMarker ?: Marker(mv).apply { title = "Position estimée"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, Color.rgb(0, 120, 255), 26) }.also { L.currentMarker = it; changed = true }
            if (L.currentPos != cur) { m.position = gp(cur); L.dirty = true }
        }
        if (L.currentPos != cur) { if ((L.currentPos == null) != (cur == null)) changed = true; L.currentPos = cur }
        L.currentMarker?.let { all += it }
        if (content.follow != null && content.follow != L.followed) mv.controller.setCenter(gp(content.follow))
        L.followed = content.follow
        all += group("scale", Unit) { listOf(ScaleBarOverlay(mv), CopyrightOverlay(ctx).apply { setTextSize(12) }) }
        if (changed || L.dirty || L.tone != tone) {
            if (changed) { mv.overlays.clear(); mv.overlays.addAll(all) }
            L.dirty = false; L.tone = tone
            mv.invalidate()
        }
    })
    if (reticle) Text("+", Modifier.align(Alignment.Center), fontSize = 40.sp, color = androidx.compose.ui.graphics.Color.Red,
        style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color.White, blurRadius = 6f)))
    // Bottom-left stack (cadastre attribution above « largeur visible »), above the OSM copyright line, left half only:
    // the labels never overlap each other nor the bottom-right buttons, whatever the base map.
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.5f).padding(start = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // carte_cadastre_tuiles_dessinees: present only once at least one cadastre tile has been painted on the map.
        if (MapPrefs.cadastre) Box(if (cadastreDrawn) Modifier.testTag("carte_cadastre_tuiles_dessinees") else Modifier) { Text(Basemap.CADASTRE_ATTRIBUTION,
            Modifier.background(androidx.compose.ui.graphics.Color(0xCCFFFFFF)).padding(2.dp).testTag("carte_attribution_cadastre"),
            style = MaterialTheme.typography.labelSmall) }
        widthM?.let {
            Text(if (it < 100) "largeur visible : %.1f m".format(it) else "largeur visible : %.0f m".format(it),
                Modifier.background(androidx.compose.ui.graphics.Color(0xCCFFFFFF)).padding(4.dp).testTag("carte_largeur_visible"),
                style = MaterialTheme.typography.labelSmall)
        }
    }
    Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.testTag("carte_fond").background(androidx.compose.ui.graphics.Color(0xCCFFFFFF))) { Text("Fond : ${basemap.label}") }
        DropdownMenu(menu, { menu = false }) {
            Basemap.entries.forEach { b ->
                val ok = b != Basemap.ESRI || MapPrefs.esriAccepted
                DropdownMenuItem(
                    text = { Text((if (b == basemap) "✓ " else "") + b.label + (if (!ok) " (conditions Esri non acceptées)" else "")) },
                    enabled = ok, onClick = { MapPrefs.basemap = b; MapPrefs.save(ctx); menu = false },
                )
            }
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(MapPrefs.cadastre, null, modifier = Modifier.testTag("carte_cadastre")); Text("Cadastre IGN (Parcellaire Express) par-dessus") } },
                onClick = { MapPrefs.cadastre = !MapPrefs.cadastre; MapPrefs.save(ctx); cadastreDrawn = false; menu = false },
            )
            DropdownMenuItem(
                text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(MapPrefs.esriAccepted, null, modifier = Modifier.testTag("carte_checkbox")); Text("Le projet accepte les conditions Esri") } },
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

private class LayerGroup { var built = false; var key: Any? = null; var overlays: List<org.osmdroid.views.overlay.Overlay> = emptyList() }

/** v176b: overlays kept between recompositions (see the AndroidView update of [OsmMap]). */
private class MapLayers {
    val groups = HashMap<String, LayerGroup>()
    var trajectory: List<List<CorrectedPoint>>? = null
    val trajLines = ArrayList<Polyline>()
    val trajSimplifiers = ArrayList<app.lumisite.core.map.GrowingSegmentSimplifier>()
    val trajDrawn = ArrayList<List<CorrectedPoint>?>()
    var arrowsKey: List<MapArrow>? = null
    val arrowMarkers = ArrayList<Marker>()
    val arrowIcons = HashMap<Pair<Int?, Int>, android.graphics.drawable.Drawable>()
    var currentMarker: Marker? = null
    var currentPos: LatLon? = null
    var followed: LatLon? = null
    var tone: Any? = null
    var dirty = false
}

private class Gesture { var multi = false; var reentry = false; var x = 0f; var y = 0f }

/** North-up map: rotating the canvas clockwise by the heading (degrees from north) points the arrow the right way. */
private fun arrowIcon(mv: MapView, headingDeg: Double?, color: Int, sizeDp: Int = 36): android.graphics.drawable.Drawable {
    val d = mv.resources.displayMetrics.density
    val px = (sizeDp * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    val c = px / 2f
    if (headingDeg == null) { cv.drawCircle(c, c, px * 0.28f, fill); cv.drawCircle(c, c, px * 0.28f, edge) }
    else {
        cv.rotate(headingDeg.toFloat(), c, c)
        val path = android.graphics.Path().apply { moveTo(c, px * 0.06f); lineTo(px * 0.84f, px * 0.92f); lineTo(c, px * 0.68f); lineTo(px * 0.16f, px * 0.92f); close() }
        cv.drawPath(path, fill); cv.drawPath(path, edge)
    }
    return android.graphics.drawable.BitmapDrawable(mv.resources, bmp)
}

/** Repère « à confirmer » (reprise de session) : cercle en pointillé, centre transparent (la carte reste lisible dessous). */
private fun dashedDot(mv: MapView, color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
    val d = mv.resources.displayMetrics.density
    val px = (sizeDp * d).toInt()
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke((2.5f * d).toInt().coerceAtLeast(1), color, 6 * d, 4 * d)
        setSize(px, px)
    }
}

private fun dot(mv: MapView, color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
    val px = (sizeDp * mv.resources.displayMetrics.density).toInt()
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color); setStroke(3, Color.WHITE); setSize(px, px)
    }
}
