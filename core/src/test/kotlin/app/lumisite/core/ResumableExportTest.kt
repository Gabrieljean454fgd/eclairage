package app.lumisite.core

import app.lumisite.core.compute.Calibrator
import app.lumisite.core.export.ResumableExport
import app.lumisite.core.export.ResumableManifest
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.geo.Enu
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** D14 / D16: resumable streaming export, GeoJSON FeatureCollection, LISEZMOI. */
class ResumableExportTest {
    private val s = 1_000_000_000L

    private fun session(imuSeconds: Int = 2): File {
        val dir = Files.createTempDirectory("lumi").toFile()
        SessionJournal(dir, Fixtures.header).use { j ->
            j.event(SessionStarted("e0", Fixtures.stamp(5 * s)))
            val poses = (0..20).map { Fixtures.pose(5 * s + it * 100_000_000L, it * 0.2, it * 0.5) }
            poses.forEach(j::pose)
            for (k in 0 until imuSeconds * 100) j.imu(ImuSample(Fixtures.stamp(5 * s + k * 10_000_000L), 1, "accel", listOf(0.1f, 9.8f, 0.0f), 3))
            j.event(Calibrator.onePointHeading("cal", poses[0].t, LocalObservation(poses[0], ReferencePoint("A", Enu(10.0, 20.0), source = "MAP_TAP")), 0.0, "MANUAL", 5.0))
            listOf(5, 10, 15).forEachIndexed { i, k -> j.event(SurveyEvent("s$i", poses[k].t, "LUMINAIRE", null, poses[k], TrackingState.TRACKING, null, "AR_POSE")) }
        }
        return dir
    }

    @Test fun interruptedExportResumesWithoutRewritingVerifiedEntries() {
        val dir = session()
        val out = Files.createTempDirectory("exp").toFile()
        // Stop in the middle of the assembly (after the raw streams, when the trajectory starts).
        assertFailsWith<InterruptedException> {
            ResumableExport.run(Fixtures.site, dir, out, "test", 1_700_000_000_000, progress = { p ->
                if (p.step == ResumableExport.Step.ASSEMBLAGE && p.detail == "derived/trajectory_site.ndjson") throw InterruptedException("stop")
            })
        }
        val expDir = out.listFiles()!!.single()
        val m1 = LumiJson.decodeFromString(ResumableManifest.serializer(), File(expDir, ResumableExport.MANIFEST).readText())
        assertEquals("ASSEMBLAGE", m1.state)
        assertEquals(6, m1.entries.size, "the 6 raw streams are recorded")
        val r = ResumableExport.run(Fixtures.site, dir, out, "test", 1_700_000_000_000, copy = { "Documents/LumiSite/test.zip" })
        assertEquals(m1.entries.map { it.name }, r.reused)
        assertTrue(r.writtenNow.none { it.startsWith("raw/") }, "raw entries already verified are not written again: ${r.writtenNow}")
        assertEquals("TERMINE", r.manifest.state)
        assertEquals("Documents/LumiSite/test.zip", r.manifest.copiedTo)
        assertEquals(emptyList(), SessionImporter.verify(r.zip), "the resumed zip is a valid export")
        assertTrue(expDir.listFiles()!!.none { it.name.endsWith(".part") || it.name.endsWith(".tmp") })
        assertTrue(File(dir, "seg").listFiles()!!.isNotEmpty(), "session data is never deleted")
        // A third run on the same snapshot does nothing again.
        val r3 = ResumableExport.run(Fixtures.site, dir, out, "test", 1_700_000_000_000)
        assertEquals(emptyList(), r3.writtenNow)
    }

    @Test fun relevesGeojsonIsOneFeatureCollectionInWgs84WithLocalCoordinatesInProperties() {
        val r = ResumableExport.run(Fixtures.site, session(), Files.createTempDirectory("exp").toFile(), "test", 1_700_000_000_000)
        ZipFile(r.zip).use { zf ->
            val fc = LumiJson.decodeFromString(JsonObject.serializer(), zf.getInputStream(zf.getEntry("releves.geojson")).readBytes().decodeToString())
            assertEquals("FeatureCollection", fc["type"]!!.jsonPrimitive.content)
            val features = fc["features"] as JsonArray
            assertEquals(3, features.size)
            for (f in features) {
                val c = ((f as JsonObject)["geometry"] as JsonObject)["coordinates"] as JsonArray
                assertEquals(2, c.size)
                val lon = c[0].jsonPrimitive.content.toDouble(); val lat = c[1].jsonPrimitive.content.toDouble()
                assertTrue(lon in 2.0..3.0 && lat in 48.0..49.0, "WGS84 lon/lat near the site origin, not site metres: $lon $lat")
                val p = f["properties"] as JsonObject
                assertTrue("local" in p && "provenance" in p)
            }
            assertTrue(zf.getEntry("LISEZMOI.txt") != null)
            assertTrue(zf.getInputStream(zf.getEntry("LISEZMOI.txt")).readBytes().decodeToString().contains("releves.geojson"))
        }
    }
}
