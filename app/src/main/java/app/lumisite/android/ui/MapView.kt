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
import app.lumisite.core.model.OsmFeature
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay

data class MapPoint(val pos: LatLon, val title: String, val color: Int)
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
)

private fun gp(p: LatLon) = GeoPoint(p.lat, p.lon)

@Composable
fun OsmMap(
    modifier: Modifier,
    center: LatLon,
    zoom: Double,
    content: MapContent,
    recenterKey: Any? = null,
    onTap: (LatLon) -> Unit,
    onFeatureTap: ((OsmFeature) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(zoom)
            controller.setCenter(gp(center))
            minZoomLevel = 3.0; maxZoomLevel = 21.0
        }
    }
    val holder = remember { arrayOf<Any?>(null) }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }
    if (holder[0] != recenterKey) {
        holder[0] = recenterKey
        val c = gp(center)
        // Before the first layout osmdroid ignores setCenter/setZoom; apply after layout.
        val apply = { map.controller.setZoom(zoom); map.controller.setCenter(c) }
        if (map.isLayoutOccurred) map.post { apply() } else map.addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
    }
    AndroidView(factory = { map }, modifier = modifier.clipToBounds(), update = { mv ->
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
        content.points.forEach { p -> mv.overlays += Marker(mv).apply { position = gp(p.pos); title = p.title; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, p.color, 20) } }
        content.picked?.let { mv.overlays += Marker(mv).apply { position = gp(it); title = "Point choisi" } }
        content.current?.let { mv.overlays += Marker(mv).apply { position = gp(it); title = "Position estimée"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); icon = dot(mv, Color.rgb(0, 120, 255), 26) } }
        mv.overlays += ScaleBarOverlay(mv)
        mv.overlays += CopyrightOverlay(ctx).apply { setTextSize(12) }
        mv.invalidate()
    })
}

private fun dot(mv: MapView, color: Int, sizeDp: Int): android.graphics.drawable.Drawable {
    val px = (sizeDp * mv.resources.displayMetrics.density).toInt()
    return android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(color); setStroke(3, Color.WHITE); setSize(px, px)
    }
}
