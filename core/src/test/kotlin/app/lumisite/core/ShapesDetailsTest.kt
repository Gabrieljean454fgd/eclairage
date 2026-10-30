package app.lumisite.core

import app.lumisite.core.export.GeoJsonFeatures
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.survey.Details
import app.lumisite.core.survey.Shapes
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShapesDetailsTest {
    private val ltp = LocalTangentPlane(Fixtures.site.origin)

    @Test fun rectangleIsStoredInMetresAndExportedInBothFrames() {
        val s = ShapeEvent("sh1", Fixtures.stamp(1), "shape", "rectangle", listOf(Enu(0.0, 0.0, 0.0), Enu(4.0, 3.0, 0.0)), label = "local TGBT", comment = "à vérifier")
        val back = LumiJson.decodeFromString(SessionEvent.serializer(), LumiJson.encodeToString(SessionEvent.serializer(), s)) as ShapeEvent
        assertEquals(4.0, back.points[1].e)
        val f = GeoJsonFeatures.lines(listOf(s), ltp, "dev-1", "sess-1").single()
        assertEquals("Polygon", f["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val ring = f["geometry"]!!.jsonObject["coordinates"]!!.jsonArray[0].jsonArray
        assertEquals(5, ring.size)
        val ne = ltp.toLatLon(Enu(4.0, 3.0, 0.0))
        assertEquals(ne.lon, ring[2].jsonArray[0].jsonPrimitive.double, 1e-7)
        val p = f["properties"]!!.jsonObject
        assertEquals("local TGBT", p["label"]!!.jsonPrimitive.content); assertEquals("à vérifier", p["note"]!!.jsonPrimitive.content)
        assertEquals(4.0, p["local"]!!.jsonObject["points"]!!.jsonArray[1].jsonArray[0].jsonPrimitive.double)
    }

    @Test fun format01EventWithoutNewFieldsStillReads() {
        val old = """{"type":"survey","id":"s","t":{"utcEpochMs":1,"monotonicNs":1,"timeBaseId":"tb"},"kind":"POINT","tracking":"TRACKING","positionSource":"NONE"}"""
        val e = LumiJson.decodeFromString(SessionEvent.serializer(), old) as SurveyEvent
        assertNull(e.supersedes); assertEquals(emptyList(), e.attachments)
    }

    @Test fun detailsValidation() {
        assertNotNull(Details.validate("puissance_W", "-5"))
        assertNotNull(Details.validate("puissance_W", "0"))
        assertNotNull(Details.validate("temp_couleur_K", "12000"))
        assertNull(Details.validate("temp_couleur_K", "3000"))
        assertNull(Details.validate("puissance_W", ""))
        assertFailsWith<IllegalArgumentException> { Details.build(mapOf("temp_couleur_K" to "12000")) }
        val d = Details.build(mapOf("sous_type" to "GU10 LED", "puissance_W" to "7,5", "culot" to ""))!!
        assertEquals(7.5, d["puissance_W"]!!.jsonPrimitive.double)
        assertNull(d["culot"]) // empty stays absent, never 0
        assertNull(Details.build(mapOf("puissance_W" to "")))
    }

    @Test fun editChainUndoAndGeometryHelpers() {
        val a = ShapeEvent("a", Fixtures.stamp(1), "shape", "rectangle", listOf(Enu(0.0, 0.0, 0.0), Enu(4.0, 2.0, 0.0)), label = "local TGBT")
        val moved = Shapes.translate(a, 1.0, 0.0).copy(id = "a2", supersedes = "a")
        val big = Shapes.scale(moved, 2.0).copy(id = "a3", supersedes = "a2")
        assertEquals(8.0, big.points[1].e - big.points[0].e, 1e-9)
        val undo = moved.copy(id = "a4", supersedes = "a3") // undo = previous state appended
        val ev = listOf(a, moved, big, undo)
        assertEquals("a4", Shapes.head(ev, "a"))
        assertEquals(listOf("a4"), Shapes.current(ev).map { it.id })
        assertEquals(emptyList(), Shapes.current(ev + undo.copy(id = "a5", supersedes = "a4", deleted = true)))
        assertEquals("a4", Shapes.nearest(Shapes.current(ev), Enu(1.1, 1.0, 0.0))?.id)
        assertNull(Shapes.nearest(Shapes.current(ev), Enu(50.0, 50.0, 0.0)))
    }
}
