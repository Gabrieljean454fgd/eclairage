package app.lumisite.core

import app.lumisite.core.catalog.PlanSymbols
import app.lumisite.core.compute.ArSiteOverlay
import app.lumisite.core.compute.V3
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.model.BBox
import app.lumisite.core.geo.LatLon
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArSiteOverlayTest {
    private val tr = Similarity2D(1.0, Math.toRadians(30.0), 5.0, -2.0)
    private fun close(a: Double, b: Double) = assertTrue(abs(a - b) < 1e-9, "$a != $b")

    @Test fun toSessionInvertsCalibration() {
        val local = V3(1.5, 0.0, -3.0)
        val site = tr.apply(Vec2(local.x, -local.z))
        val back = ArSiteOverlay.toSession(Enu(site.x, site.y), tr, 0.7)
        close(back.x, 1.5); close(back.z, -3.0); close(back.y, 0.7)
    }

    @Test fun glyphIsFlatAndSized() {
        val segs = ArSiteOverlay.glyph(PlanSymbols.prims("ECL_POINT"), V3(1.0, 2.0, 3.0), 0.15)
        assertTrue(segs.size > 16)
        assertTrue(segs.all { it.first.y == 2.0 && it.second.y == 2.0 })
        assertTrue(segs.all { abs(it.first.x - 1.0) <= 0.15 + 1e-9 && abs(it.first.z - 3.0) <= 0.15 + 1e-9 })
    }

    @Test fun buildSkipsCurrentVisitAndFarItemsAndCounts() {
        val items = listOf(
            ArSiteOverlay.Item(Enu(6.0, -1.0), ArSiteOverlay.Kind.LUMINAIRE, "ECL_ENCASTRE", 0xFFFFC107.toInt(), current = false),
            ArSiteOverlay.Item(Enu(7.0, -1.0), ArSiteOverlay.Kind.LUMINAIRE, "ECL_SPOT", 0xFFFFC107.toInt(), current = true),
            ArSiteOverlay.Item(Enu(4.0, 0.0), ArSiteOverlay.Kind.REPERE, null, ArSiteOverlay.OLD_REPERE_ARGB, current = false),
            ArSiteOverlay.Item(Enu(500.0, 0.0), ArSiteOverlay.Kind.REPERE, null, ArSiteOverlay.OLD_REPERE_ARGB, current = false),
        )
        val s = ArSiteOverlay.build(items, listOf(listOf(Enu(0.0, 0.0), Enu(10.0, 0.0), Enu(10.0, 10.0))), tr)
        assertEquals(2, s.luminaires); assertEquals(1, s.reperes); assertEquals(2, s.otherVisits); assertEquals(2, s.outlineSegs)
        assertTrue(s.groups.flatMap { it.segs }.all { it.first.y == 0.0 }, "plan layer at height 0, lifted to the floor by the renderer")
    }

    @Test fun outlinesFromRectangles() {
        val ltp = LocalTangentPlane(LatLon(43.6, 1.44))
        val r = ArSiteOverlay.outlines(listOf(BBox(43.6, 1.44, 43.6001, 1.4401)), emptyList(), emptySet(), ltp)
        assertEquals(1, r.size); assertEquals(5, r[0].size)
        assertTrue(abs(r[0][2].n - 11.1) < 0.2, r[0][2].toString())
    }

    @Test fun statusNeverClaimsAPlanWithoutCalibrationOrFloor() {
        val s = ArSiteOverlay.build(emptyList(), emptyList(), tr)
        assertTrue(ArSiteOverlay.status(s, false, true, true).startsWith("Plan : calibrez"))
        assertTrue(ArSiteOverlay.status(s, true, false, true).contains("aucun sol détecté"))
        assertTrue(ArSiteOverlay.status(s, true, true, true).startsWith("Plan : 0 luminaire"))
    }
}
