package app.lumisite.core

import app.lumisite.core.export.GeoJsonFeatures
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import app.lumisite.core.model.ZoneEvent
import app.lumisite.core.survey.Zones
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoneEventTest {
    private val ltp = LocalTangentPlane(Fixtures.site.origin)
    // Outer ring clockwise and a hole counter-clockwise on purpose: the export must reorient them.
    private val outer = listOf(Enu(0.0, 0.0, 0.0), Enu(0.0, 10.0, 0.0), Enu(10.0, 10.0, 0.0), Enu(10.0, 0.0, 0.0))
    private val hole = listOf(Enu(4.0, 4.0, 0.0), Enu(6.0, 4.0, 0.0), Enu(6.0, 6.0, 0.0), Enu(4.0, 6.0, 0.0))
    private val zone = ZoneEvent("zon-1", Fixtures.stamp(1), outer, listOf(hole))

    @Test fun zoneEventRoundTripsAndOldShapeZonesStillCount() {
        val back = LumiJson.decodeFromString(SessionEvent.serializer(), LumiJson.encodeToString(SessionEvent.serializer(), zone))
        assertEquals(zone, back)
        assertTrue(LumiJson.encodeToString(SessionEvent.serializer(), zone).contains("\"type\":\"zone\""))
        val old = ShapeEvent("shp-1", Fixtures.stamp(2), "zone", "polygon", hole, zoneKind = "TECHNIQUE")
        assertEquals(setOf("zon-1", "shp-1"), Zones.current(listOf(zone, old)).map { it.id }.toSet())
    }

    @Test fun exportedRingsAreClosedOuterCcwHolesCw() {
        val f = GeoJsonFeatures.lines(listOf(zone), ltp, "dev-1", "sess-1").single()
        val rings = ((f["geometry"] as JsonObject)["coordinates"] as JsonArray).map { r ->
            (r as JsonArray).map { p -> (p as JsonArray).let { it[0].toString().toDouble() to it[1].toString().toDouble() } }
        }
        assertEquals(2, rings.size)
        fun area(r: List<Pair<Double, Double>>) = r.zipWithNext { a, b -> a.first * b.second - b.first * a.second }.sum()
        rings.forEach { assertEquals(it.first(), it.last()) }
        assertTrue(area(rings[0]) > 0); assertTrue(area(rings[1]) < 0)
        assertEquals("zone", ((f["properties"] as JsonObject)["kind"] as JsonPrimitive).content)
    }

    @Test fun surveyFeatureCarriesItsComputedZone() {
        fun srv(id: String, e: Double, n: Double) = SurveyEvent(id, Fixtures.stamp(5), "spot", tracking = TrackingState.TRACKING,
            positionSource = "MANUAL_MAP", manualSite = Enu(e, n, 0.0))
        val fs = GeoJsonFeatures.lines(listOf(zone, srv("in", 2.0, 2.0), srv("hole", 5.0, 5.0)), ltp, "dev-1", "sess-1")
        fun zoneOf(id: String) = (fs.first { (it["properties"] as JsonObject)["event_id"].let { e -> (e as JsonPrimitive).content } == id }["properties"] as JsonObject)["zone_id"]
        assertEquals("zon-1", (zoneOf("in") as JsonPrimitive).content)
        assertNull(zoneOf("hole"))
    }

    @Test fun collinearContactsAndBadHolesAreRefused() {
        // Vertex touching a non-adjacent edge (figure "8" pinched on an edge).
        assertTrue(Zones.selfIntersects(listOf(Enu(0.0, 0.0, 0.0), Enu(4.0, 0.0, 0.0), Enu(4.0, 4.0, 0.0), Enu(2.0, 0.0, 0.0), Enu(0.0, 4.0, 0.0))))
        // Edge folding back on the previous one.
        assertTrue(Zones.selfIntersects(listOf(Enu(0.0, 0.0, 0.0), Enu(4.0, 0.0, 0.0), Enu(2.0, 0.0, 0.0), Enu(2.0, 3.0, 0.0))))
        assertFalse(Zones.selfIntersects(outer))
        assertNull(Zones.problem(outer, listOf(hole)))
        assertNotNull(Zones.problem(outer, listOf(hole.map { it.copy(e = it.e + 5.0) })))
        assertNotNull(Zones.problem(outer, listOf(hole, hole.map { it.copy(e = it.e + 1.0) })))
    }
}
