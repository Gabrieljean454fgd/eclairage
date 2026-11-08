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

    /** Paquet v6 (export du 08/11): open segment exported, photo path = zip entry, sync WAV exported, photo sidecar JSON. */
    @Test fun openSegmentsPhotoPathWavAndSidecarAreExported() {
        val dir = session()
        File(dir, "photos").mkdirs(); File(dir, "photos/p1.jpg").writeBytes(ByteArray(2000) { it.toByte() })
        File(dir, "sync_A_1.wav").writeBytes(ByteArray(1000) { 1 })
        SessionJournal(dir).use { j ->
            val pose = Fixtures.pose(9 * s, 1.0, 1.0)
            j.event(SurveyEvent("s9", pose.t, "LUMINAIRE", "note terrain", pose, TrackingState.TRACKING, null, "AR_POSE",
                attachments = listOf(app.lumisite.core.model.Attachment("photos/p1.jpg", "image/jpeg", "abc", "vue"))))
        }
        val open = app.lumisite.core.journal.Segments.file(dir, app.lumisite.core.journal.Streams.POSES, 999, part = true)
        val l1 = LumiJson.encodeToString(app.lumisite.core.model.PoseSample.serializer(), Fixtures.pose(20 * s, 3.0, 3.0))
        open.writeText(l1 + "\n" + l1.replace("\"monotonicNs\":20", "\"monotonicNs\":21") + "\n" + l1.take(20))
        val r = ResumableExport.run(Fixtures.site, dir, Files.createTempDirectory("exp").toFile(), "test", 1_700_000_000_000)
        assertTrue(r.manifest.notes.any { it.contains(open.name) && it.contains("exportés") }, "${r.manifest.notes}")
        ZipFile(r.zip).use { zf ->
            val poses = r.manifest.entries.first { it.recordType == "PoseSample" }
            assertEquals(21 + 2, poses.records, "closed poses + the 2 complete lines of the open segment (partial line left out)")
            assertEquals(emptyList(), poses.unreadableLines)
            val fc = LumiJson.decodeFromString(JsonObject.serializer(), zf.getInputStream(zf.getEntry("releves.geojson")).readBytes().decodeToString())
            val photoPaths = (fc["features"] as JsonArray).flatMap { f -> (((f as JsonObject)["properties"] as JsonObject)["photos"] as? JsonArray).orEmpty().map { (it as JsonObject)["path"]!!.jsonPrimitive.content } }
            assertEquals(listOf("media/p1.jpg"), photoPaths)
            photoPaths.forEach { assertTrue(zf.getEntry(it) != null, "$it absent du zip") }
            assertTrue(zf.getEntry("media/sync_A_1.wav") != null)
            val side = LumiJson.decodeFromString(JsonObject.serializer(), zf.getInputStream(zf.getEntry("media/p1.jpg.json")).readBytes().decodeToString())
            assertEquals("media/p1.jpg", side["photo"]!!.jsonPrimitive.content)
            assertTrue(side["releve"].toString().contains("note terrain"))
        }
        assertEquals(emptyList(), SessionImporter.verify(r.zip))
        assertTrue(open.isFile, "the open segment of the session is never modified")
    }

    /** Revue PR #20: an open segment closed (renamed, then gzipped) during the export is read from its closed file. */
    @Test fun openSegmentRotatedDuringExportIsStillExported() {
        val dir = session()
        val open = app.lumisite.core.journal.Segments.file(dir, app.lumisite.core.journal.Streams.POSES, 999, part = true)
        val l1 = LumiJson.encodeToString(app.lumisite.core.model.PoseSample.serializer(), Fixtures.pose(20 * s, 3.0, 3.0))
        val l2 = l1.replace("\"monotonicNs\":20", "\"monotonicNs\":21")
        open.writeText(l1 + "\n" + l2 + "\n" + l1.take(20))
        var rotated = false
        val r = ResumableExport.run(Fixtures.site, dir, Files.createTempDirectory("exp").toFile(), "test", 1_700_000_000_000, progress = { p ->
            if (!rotated && p.step == ResumableExport.Step.INSTANTANE) {
                rotated = true
                open.writeText(l1 + "\n" + l2 + "\n" + l1.replace("\"monotonicNs\":20", "\"monotonicNs\":22") + "\n")
                val done = app.lumisite.core.journal.Segments.file(dir, app.lumisite.core.journal.Streams.POSES, 999, part = false)
                assertTrue(open.renameTo(done)); app.lumisite.core.journal.Segments.compress(done)
            }
        })
        assertTrue(rotated)
        assertEquals(21 + 2, r.manifest.entries.first { it.recordType == "PoseSample" }.records, "the snapshot (2 complete lines), not the lines written after it")
        assertEquals(emptyList(), SessionImporter.verify(r.zip))
    }

    /** Revue PR #20: a photo whose survey has no final feature keeps the tags of its journal event in the sidecar. */
    @Test fun sidecarOfAPhotoWithoutFinalFeatureKeepsItsJournalTags() {
        val pose = Fixtures.pose(9 * s, 1.0, 1.0)
        val a = app.lumisite.core.model.Attachment("photos/p0.jpg", "image/jpeg", "abc", "vue")
        val ev = SurveyEvent("s0", pose.t, "LUMINAIRE", "photo remplacée", pose, TrackingState.TRACKING, null, "AR_POSE", attachments = listOf(a))
        val empty = LumiJson.decodeFromString(JsonObject.serializer(), """{"type":"FeatureCollection","features":[]}""")
        val header = LumiJson.decodeFromString(app.lumisite.core.model.SessionHeader.serializer(), File(session(), app.lumisite.core.journal.Streams.HEADER).readText())
        val side = app.lumisite.core.export.Releves.photoTags(empty, "p0.jpg", a, header, ev)
        assertEquals("null", side["releve"].toString())
        assertTrue(side["releve_journal"].toString().contains("photo remplacée"), side.toString())
    }
}
