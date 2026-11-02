package app.lumisite.core

import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.contract.ArFrameRef
import app.lumisite.core.contract.ArPoint
import app.lumisite.core.contract.PositionMethod
import app.lumisite.core.contract.SiteHeight
import app.lumisite.core.contract.SiteLink
import app.lumisite.core.contract.label
import app.lumisite.core.geo.Similarity2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContractTest {
    private val seg = TrajectoryCorrector.Segment(0, Similarity2D(1.0, Math.PI / 2, 10.0, 20.0), "cal", "tb1", epoch = 1)

    @Test fun conversionIsExplicitAndBoundToItsArFrame() {
        val p = SiteLink(seg).toSite(ArPoint(1.0, 1.5, 0.0, ArFrameRef("tb1", 1)))
        assertEquals(10.0, p.eM, 1e-9); assertEquals(21.0, p.nM, 1e-9)
        assertEquals(SiteHeight.Unqualified, p.height, "the AR y is never turned into a height")
        assertFailsWith<IllegalArgumentException> { SiteLink(seg).toSite(ArPoint(1.0, 0.0, 0.0, ArFrameRef("tb1", 0))) }
        assertFailsWith<IllegalArgumentException> { SiteLink(seg).toSite(ArPoint(1.0, 0.0, 0.0, ArFrameRef("tb2", 1))) }
    }

    @Test fun heightWithoutFloorIsUnqualified() {
        assertEquals("hauteur non qualifiée", SiteHeight.Unqualified.label())
        assertEquals("2,62 m", SiteHeight.AboveFloor(2.62, "sol-1").label())
        assertEquals(0.0, app.lumisite.core.contract.SitePoint(1.0, 2.0).toEnu().u)
    }

    @Test fun journalSourcesMapToMethods() {
        assertEquals(PositionMethod.MAP_TAP, PositionMethod.of("MANUAL_MAP"))
        assertEquals(PositionMethod.AR_CAMERA, PositionMethod.of("AR_POSE"))
        assertEquals(PositionMethod.UNKNOWN, PositionMethod.of("??"))
    }
}
