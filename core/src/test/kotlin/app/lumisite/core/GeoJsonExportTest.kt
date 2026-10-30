package app.lumisite.core

import app.lumisite.core.export.ExportPackage
import app.lumisite.core.export.GeoJsonFeatures
import app.lumisite.core.export.GeoJsonSeq
import app.lumisite.core.export.GeoJsonSeqWriter
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.model.DxfHint
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.SessionStopped
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoJsonExportTest {
    private val ltp = LocalTangentPlane(Fixtures.site.origin)
    private fun tmp(): File = Files.createTempDirectory("lumigeo").toFile()

    private fun srv(id: String, t: Long, e: Double, n: Double, supersedes: String? = null, deleted: Boolean? = null) = SurveyEvent(
        id = id, t = Fixtures.stamp(t), kind = "spot", tracking = TrackingState.TRACKING, positionSource = "MANUAL_MAP",
        manualSite = Enu(e, n, 0.0), payloadSchema = "lumisite-equipment/1",
        payload = buildJsonObject { put("catalog_version", "lumisite-catalog-1.0"); put("block_name", "spot") },
        dxf = DxfHint("RES - Elec", "spot"), supersedes = supersedes, deleted = deleted,
    )

    @Test fun geometryIsLonLatAndLocalIsSiteFrame() {
        val f = GeoJsonFeatures.feature(srv("a", 1_000, 10.0, 20.0), Enu(10.0, 20.0, 1.5), ltp, "a", 1, "dev-1", "sess-1")
        val c = f["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val ll = ltp.toLatLon(Enu(10.0, 20.0, 0.0))
        assertEquals(ll.lon, c[0].jsonPrimitive.double, 1e-7); assertEquals(ll.lat, c[1].jsonPrimitive.double, 1e-7)
        val p = f["properties"]!!.jsonObject
        assertEquals(20.0, p["local"]!!.jsonObject["y"]!!.jsonPrimitive.double)
        assertEquals("RES - Elec", p["layer"]!!.jsonPrimitive.content)
        assertTrue("crs" !in f)
        assertEquals(JsonNull, GeoJsonFeatures.feature(srv("b", 1, 0.0, 0.0), null, ltp, "b", 1, "d", "s")["geometry"])
    }

    @Test fun liveWriterAppendsRevisionsAndSurvivesTruncation() {
        val d = tmp(); val file = File(d, GeoJsonSeqWriter.FILE)
        GeoJsonSeqWriter(file, ltp, "dev-1", "sess-1").use { w ->
            w.append(srv("a", 1_000, 1.0, 1.0), Enu(1.0, 1.0, 0.0))
            w.append(srv("b", 2_000, 2.0, 2.0), Enu(2.0, 2.0, 0.0))
        }
        val line1 = file.readLines()[0]
        file.appendText("""{"type":"Feature","id":"c","geom""") // app killed mid-write
        val r = GeoJsonSeq.read(file)
        assertEquals(2, r.items.size); assertEquals(listOf(3), r.skippedLines)
        GeoJsonSeqWriter(file, ltp, "dev-1", "sess-1").use { w -> w.append(srv("a2", 3_000, 1.1, 1.0, supersedes = "a"), Enu(1.1, 1.0, 0.0)) }
        assertEquals(line1, file.readLines()[0]) // correction does not rewrite line 1
        val latest = GeoJsonSeq.latest(GeoJsonSeq.read(file).items)
        assertEquals(2, latest.getValue("a")["properties"]!!.jsonObject["rev"]!!.jsonPrimitive.int)
        GeoJsonSeqWriter(file, ltp, "dev-1", "sess-1").use { w -> w.append(srv("b2", 4_000, 0.0, 0.0, supersedes = "b", deleted = true), null) }
        assertEquals(setOf("a"), GeoJsonSeq.latest(GeoJsonSeq.read(file).items).keys)
    }

    @Test fun regeneratedLinesMatchEditChain() {
        val ev = listOf(srv("a", 1, 1.0, 1.0), srv("a2", 2, 2.0, 1.0, supersedes = "a"), srv("a3", 3, 3.0, 1.0, supersedes = "a2"))
        val lines = GeoJsonFeatures.lines(ev, ltp, "dev-1", "sess-1")
        assertEquals(listOf("a", "a", "a"), lines.map { it["id"]!!.jsonPrimitive.content })
        assertEquals(listOf(1, 2, 3), lines.map { it["properties"]!!.jsonObject["rev"]!!.jsonPrimitive.int })
    }

    @Test fun sessionFromBeforeLot6ResumedKeepsAllObjectsInExport() {
        val sessDir = tmp()
        SessionJournal(sessDir, Fixtures.header).use { j ->
            j.event(SessionStarted("st", Fixtures.stamp(500)))
            j.event(srv("a", 1_000, 1.0, 2.0)); j.event(srv("b", 1_500, 3.0, 2.0))
        }
        // Resumed with the live writer: only the object recorded after the resume is in features.geojsonl.
        val c = srv("c", 5_000, 5.0, 2.0)
        SessionJournal(sessDir).use { j -> j.event(c); j.event(SessionStopped("sp", Fixtures.stamp(6_000), "user")) }
        GeoJsonSeqWriter(File(sessDir, GeoJsonSeqWriter.FILE), ltp, "dev-1", "sess-1").use { it.append(c, c.manualSite) }
        val root = ExportPackage.build(Fixtures.site, listOf(ExportPackage.SessionInput(sessDir)), null, null, tmp(), 1_700_000_000_000, "test")
        assertEquals(3, ExportPackage.readManifest(root).sessions.single().featureCount)
        val ids = GeoJsonSeq.latest(GeoJsonSeq.read(File(root, "sessions/dev-1/sess-1.geojsonl")).items).keys
        assertEquals(setOf("a", "b", "c"), ids)
    }

    @Test fun packageManifestHashesAndRawReimport() {
        val sessDir = tmp()
        SessionJournal(sessDir, Fixtures.header).use { j ->
            j.event(SessionStarted("st", Fixtures.stamp(500)))
            j.event(srv("a", 1_000, 1.0, 2.0)); j.event(srv("a2", 2_000, 1.5, 2.0, supersedes = "a"))
            j.event(SessionStopped("sp", Fixtures.stamp(3_000), "user"))
        }
        val out = tmp()
        val root = ExportPackage.build(Fixtures.site, listOf(ExportPackage.SessionInput(sessDir)), """{"catalog_version":"x","entries":[]}""", null, out, 1_700_000_000_000, "test")
        val m = ExportPackage.readManifest(root)
        assertEquals("lumisite-geojson-export", m.schema); assertEquals("1.0.0", m.schemaVersion)
        assertEquals(1, m.sessions.single().featureCount); assertTrue(m.sessions.single().closed)
        assertTrue(m.files.any { it.path == "sessions/dev-1/sess-1.geojsonl" })
        assertTrue(m.files.none { it.path.endsWith(".dxf") })
        assertEquals(emptyList(), ExportPackage.verify(root))
        val raw = File(root, "raw/dev-1/sess-1.zip")
        assertTrue(SessionImporter.load(raw).problems.isEmpty())
        File(root, "sessions/dev-1/sess-1.geojsonl").appendText("\n")
        assertEquals(listOf("modifié : sessions/dev-1/sess-1.geojsonl"), ExportPackage.verify(root))
        val zip = File(out, "p.zip"); ExportPackage.zipStored(root, zip)
        ZipFile(zip).use { z -> assertTrue(z.getEntry("LumiSite_site-1/manifest.json") != null); assertTrue(z.entries().toList().all { it.method == java.util.zip.ZipEntry.STORED }) }
    }

    @Test fun linesValidateAgainstPublishedSchema() {
        val schemaFile = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "docs/lumisite-feature.schema.json") }.first { it.exists() }
        val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaFile.readText())
        val ev = listOf(srv("a", 1, 1.0, 1.0), srv("a2", 2, 2.0, 1.0, supersedes = "a"), srv("a3", 3, 0.0, 0.0, supersedes = "a2", deleted = true))
        val mapper = ObjectMapper()
        GeoJsonFeatures.lines(ev, ltp, "dev-1", "sess-1").forEach { l ->
            val errors = schema.validate(mapper.readTree(l.toString()))
            assertTrue(errors.isEmpty(), "$errors in $l")
        }
        val bad = mapper.readTree("""{"type":"Feature","id":"x","geometry":null,"properties":{"rev":0}}""")
        assertTrue(schema.validate(bad).isNotEmpty())
        for ((kind, g) in listOf("shape" to "rectangle", "annotation" to "point", "zone" to "polygon", "shape" to "polyline")) {
            val pts = if (g == "point") listOf(Enu(1.0, 1.0, 0.0)) else listOf(Enu(0.0, 0.0, 0.0), Enu(4.0, 0.0, 0.0), Enu(4.0, 3.0, 0.0))
            val f = GeoJsonFeatures.shape(app.lumisite.core.model.ShapeEvent("s-$g", Fixtures.stamp(1), kind, g, pts), ltp, "s-$g", 1, "dev-1", "sess-1")
            assertEquals(emptySet(), schema.validate(mapper.readTree(f.toString())), "$kind/$g")
        }
    }
}
