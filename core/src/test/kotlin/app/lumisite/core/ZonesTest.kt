package app.lumisite.core

import app.lumisite.core.geo.Enu
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.time.Stamp
import app.lumisite.core.survey.Zones
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZonesTest {
    private val t = Stamp(1, 1, "tb")
    private fun e(x: Double, y: Double) = Enu(x, y, 0.0)
    private fun zone(id: String, vararg p: Pair<Double, Double>, parent: String? = null, holes: List<List<Enu>>? = null) =
        ShapeEvent(id, t, "zone", "polygon", p.map { e(it.first, it.second) }, zoneKind = "ARCHITECTURAL", parentZoneId = parent, holes = holes, source = "MANUAL_MAP")

    @Test fun convexConcaveHoleOutside() {
        val square = zone("z", 0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0)
        assertTrue(Zones.contains(square, e(5.0, 5.0)))
        val l = zone("l", 0.0 to 0.0, 10.0 to 0.0, 10.0 to 4.0, 4.0 to 4.0, 4.0 to 10.0, 0.0 to 10.0)
        assertTrue(Zones.contains(l, e(2.0, 8.0))); assertFalse(Zones.contains(l, e(8.0, 8.0)))
        val cloister = zone("c", 0.0 to 0.0, 30.0 to 0.0, 30.0 to 30.0, 0.0 to 30.0, holes = listOf(listOf(e(10.0, 10.0), e(20.0, 10.0), e(20.0, 20.0), e(10.0, 20.0))))
        assertTrue(Zones.contains(cloister, e(5.0, 15.0))); assertFalse(Zones.contains(cloister, e(15.0, 15.0)))
        assertNull(Zones.zoneOf(e(50.0, 50.0), listOf(square, l, cloister)))
    }

    @Test fun nestedZonesDeepestWins() {
        val church = zone("eglise", 0.0 to 0.0, 40.0 to 0.0, 40.0 to 20.0, 0.0 to 20.0)
        val choir = zone("choeur", 30.0 to 5.0, 38.0 to 5.0, 38.0 to 15.0, 30.0 to 15.0)
        val altar = zone("autel", 33.0 to 8.0, 35.0 to 8.0, 35.0 to 12.0, 33.0 to 12.0, parent = "choeur")
        val zs = listOf(church, choir, altar)
        assertEquals("choeur", Zones.zoneOf(e(31.0, 6.0), zs)?.id)
        assertEquals("autel", Zones.zoneOf(e(34.0, 10.0), zs)?.id)
        assertEquals("eglise", Zones.zoneOf(e(5.0, 5.0), zs)?.id)
    }

    @Test fun selfIntersectingRejectedAndOrientation() {
        assertTrue(Zones.selfIntersects(listOf(e(0.0, 0.0), e(10.0, 10.0), e(10.0, 0.0), e(0.0, 10.0))))
        assertFalse(Zones.selfIntersects(listOf(e(0.0, 0.0), e(10.0, 0.0), e(10.0, 10.0), e(0.0, 10.0))))
        assertTrue(Zones.signedArea(listOf(e(0.0, 0.0), e(10.0, 0.0), e(10.0, 10.0), e(0.0, 10.0))) > 0)
    }

    @Test fun oldShapeWithoutZoneFieldsStillReads() {
        val old = """{"type":"shape","id":"shp-1","t":{"utcEpochMs":1,"monotonicNs":1,"timeBaseId":"tb"},"kind":"zone","geometryType":"polygon","points":[{"e":0.0,"n":0.0,"u":0.0}]}"""
        val s = LumiJson.decodeFromString(SessionEvent.serializer(), old) as ShapeEvent
        assertNull(s.zoneKind); assertNull(s.holes)
        val z = zone("z", 0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0)
        assertEquals(z, LumiJson.decodeFromString(SessionEvent.serializer(), LumiJson.encodeToString(SessionEvent.serializer(), z)))
    }
}
