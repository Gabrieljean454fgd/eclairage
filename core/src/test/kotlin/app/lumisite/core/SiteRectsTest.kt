package app.lumisite.core

import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature
import app.lumisite.core.osm.OsmSelection
import app.lumisite.core.osm.SiteRects
import app.lumisite.core.osm.SiteRects.Handle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** v176a-bis: Accueil > Nouveau relevé > « Tracer rectangle » (several rectangles, stretch, move, delete, union). */
class SiteRectsTest {
    private val a = BBox(43.600, 1.440, 43.602, 1.444)
    private val b = BBox(43.605, 1.450, 43.606, 1.452)
    private val t = 0.0001

    @Test fun handlesOnlyOnTheSelectedRectangleThenInsideThenNothing() {
        val rs = listOf(a, b)
        assertEquals(SiteRects.Hit(0, Handle.NE), SiteRects.hit(rs, LatLon(43.60205, 1.44405), t, t, selected = 0))
        assertEquals(SiteRects.Hit(0, Handle.S), SiteRects.hit(rs, LatLon(43.60003, 1.442), t, t, selected = 0))
        assertEquals(SiteRects.Hit(0, Handle.W), SiteRects.hit(rs, LatLon(43.601, 1.43995), t, t, selected = 0))
        // Same corner, rectangle not selected: grabbing its inside moves it, its border outside does nothing.
        assertEquals(SiteRects.Hit(0, Handle.MOVE), SiteRects.hit(rs, LatLon(43.6019, 1.4439), t, t, selected = null))
        assertNull(SiteRects.hit(rs, LatLon(43.60205, 1.44405), t, t, selected = null))
        assertEquals(SiteRects.Hit(1, Handle.MOVE), SiteRects.hit(rs, LatLon(43.6055, 1.451), t, t, selected = 0))
        assertNull(SiteRects.hit(rs, LatLon(43.603, 1.447), t, t, selected = 0))
    }

    @Test fun stretchMoveAndCrossingTheOppositeSideStayValid() {
        val from = LatLon(a.north, a.east)
        assertEquals(BBox(43.600, 1.440, 43.603, 1.446), SiteRects.drag(a, Handle.NE, from, LatLon(43.603, 1.446)).r6())
        assertEquals(BBox(43.601, 1.441, 43.603, 1.445), SiteRects.drag(a, Handle.MOVE, LatLon(43.601, 1.442), LatLon(43.602, 1.443)).r6())
        // Dragging the east side past the west side flips the rectangle instead of producing west > east.
        assertEquals(BBox(43.600, 1.438, 43.602, 1.440), SiteRects.drag(a, Handle.E, LatLon(43.601, a.east), LatLon(43.601, 1.438)).r6())
        assertEquals(BBox(43.600, 1.440, 43.602, 1.444), SiteRects.drag(a, Handle.N, LatLon(a.north, 1.442), LatLon(a.north, 1.442)).r6())
    }

    @Test fun siteIsTheUnionEnvelopeAndBuildingsOfEveryRectangleAreSelected() {
        assertNull(SiteRects.envelope(emptyList()))
        assertEquals(BBox(43.600, 1.440, 43.606, 1.452), SiteRects.envelope(listOf(a, b)))
        val inA = bld(1, 43.601, 1.442); val inB = bld(2, 43.6055, 1.451); val between = bld(3, 43.603, 1.447)
        val fs = listOf(between, inB, inA)
        // The envelope covers « between », the union of the rectangles does not.
        assertEquals(listOf(inB.ref, inA.ref), OsmSelection.buildingsIn(listOf(a, b, a), fs))
        assertEquals(8, SiteRects.handles(a).size)
    }

    private fun BBox.r6() = BBox(south.r(), west.r(), north.r(), east.r())
    private fun Double.r() = Math.round(this * 1e6) / 1e6
    private fun bld(id: Long, lat: Double, lon: Double) = OsmFeature("way", id, mapOf("building" to "yes"),
        listOf(LatLon(lat - 1e-5, lon - 1e-5), LatLon(lat - 1e-5, lon + 1e-5), LatLon(lat + 1e-5, lon + 1e-5), LatLon(lat + 1e-5, lon - 1e-5)), "building")
}
