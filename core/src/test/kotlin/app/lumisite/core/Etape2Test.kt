package app.lumisite.core

import app.lumisite.core.compute.PositionStatus
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature
import app.lumisite.core.model.TrackingState
import app.lumisite.core.osm.OsmSelection
import app.lumisite.core.survey.Sketch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Etape2Test {
    private fun e(x: Double, y: Double) = Enu(x, y, 0.0)

    @Test fun polygonClosesOnLastPointOnly() {
        val tol = 0.5
        assertEquals(Sketch.PolygonTap.ADD, Sketch.polygonTap(emptyList(), e(0.0, 0.0), tol))
        val three = listOf(e(0.0, 0.0), e(5.0, 0.0), e(5.0, 5.0))
        assertEquals(Sketch.PolygonTap.CLOSE, Sketch.polygonTap(three, e(5.2, 5.1), tol))
        // Touching the FIRST point does not close (rule: the last point touched again).
        assertEquals(Sketch.PolygonTap.ADD, Sketch.polygonTap(three, e(0.0, 0.0), tol))
        // Two points: a second touch on the last one is ignored (no duplicate vertex, no closing).
        assertEquals(Sketch.PolygonTap.IGNORE, Sketch.polygonTap(three.take(2), e(5.0, 0.1), tol))
    }

    @Test fun elasticPreviewIsClosedAndFollowsTheFinger() {
        val p = Sketch.polygonPreview(listOf(e(0.0, 0.0), e(4.0, 0.0)), e(4.0, 3.0))
        assertEquals(4, p.size); assertEquals(p.first(), p.last()); assertEquals(e(4.0, 3.0), p[2])
        assertEquals(2, Sketch.polygonPreview(listOf(e(0.0, 0.0)), e(1.0, 1.0)).size)
        assertEquals(5, Sketch.rectanglePreview(e(0.0, 0.0), e(2.0, 3.0)).size)
    }

    @Test fun toleranceScalesWithZoom() {
        assertEquals(28.0 / 400 * 40, Sketch.toleranceM(40.0, 400.0), 1e-9)
        assertTrue(Sketch.toleranceM(400.0, 400.0) > Sketch.toleranceM(40.0, 400.0))
    }

    private fun building(ref: Long, lat: Double, lon: Double) = OsmFeature("way", ref, emptyMap(),
        listOf(LatLon(lat, lon), LatLon(lat + 0.0001, lon), LatLon(lat + 0.0001, lon + 0.0001), LatLon(lat, lon + 0.0001)), "building")

    @Test fun rectanglePreselectsBuildingsAndTapsToggle() {
        val fs = listOf(building(1, 43.6, 1.44), building(2, 43.61, 1.45), OsmFeature("node", 3, emptyMap(), listOf(LatLon(43.6, 1.44)), "street_lamp"))
        val sel = OsmSelection.buildingsIn(BBox(43.599, 1.439, 43.602, 1.442), fs)
        assertEquals(listOf(fs[0].ref), sel)
        assertEquals(listOf(fs[0].ref, fs[1].ref), OsmSelection.toggle(sel, fs[1].ref))
        assertEquals(emptyList(), OsmSelection.toggle(sel, fs[0].ref))
    }

    @Test fun positionStatusAlwaysSaysSourceCalibrationLossAndAge() {
        val ok = PositionStatus.of(10_000, true, TrackingState.TRACKING, 9_500, null, false)
        assertEquals(PositionStatus.Source.AR, ok.source); assertTrue(!ok.lost); assertEquals(500, ok.ageMs)
        val lost = PositionStatus.of(40_000, true, TrackingState.LOST, 9_500, null, false)
        assertTrue(lost.lost); assertEquals("Source : AR · calibré · suivi perdu · dernière position valide il y a 30 s", lost.text())
        val gps = PositionStatus.of(10_000, false, TrackingState.UNAVAILABLE, null, 7_000, false)
        assertEquals(PositionStatus.Source.GPS, gps.source); assertEquals("Source : GPS · non calibré · dernière position valide il y a 3 s", gps.text())
        assertEquals(PositionStatus.Source.MANUAL, PositionStatus.of(1, false, TrackingState.UNAVAILABLE, null, null, true).source)
        assertEquals("Source : aucune · non calibré", PositionStatus.of(1, false, TrackingState.UNAVAILABLE, null, null, false).text())
    }
}
