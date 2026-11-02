package app.lumisite.core

import app.lumisite.core.export.FORMAT_VERSION
import app.lumisite.core.export.SessionImporter
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 1.3: a session exported by format 0.1 (no bootId, no integrity, no media, no stream_state) is read by this version. */
class Format01CompatTest {
    @Test fun session01IsReadByTheNewVersion() {
        val d = Files.createTempDirectory("v01").toFile()
        val raw = File(d, "raw").apply { mkdirs() }; File(d, "derived").mkdirs()
        val pose = app.lumisite.core.journal.LumiJson.encodeToString(app.lumisite.core.model.PoseSample.serializer(), Fixtures.pose(5_100_000_000, 0.0, 1.0))
        File(raw, "poses.ndjson").writeText(pose + "\n")
        listOf("imu", "gps", "gps_status", "events").forEach { File(raw, "$it.ndjson").writeText("") }
        File(d, "derived/trajectory_site.ndjson").writeText(""); File(d, "derived/survey_points.ndjson").writeText("")
        val sha = { f: String -> app.lumisite.core.export.sha256(File(d, f)) }
        fun fe(path: String, cat: String, type: String, n: Int) = """{"path":"$path","category":"$cat","recordType":"$type","records":$n,"sha256":"${sha(path)}","bytes":${File(d, path).length()}}"""
        // Site and header as written by 0.1 (same fields; 0.2.1 adds only optional ones, absent here).
        val site = app.lumisite.core.journal.LumiJson.encodeToString(app.lumisite.core.model.Site.serializer(), Fixtures.site)
        val session = app.lumisite.core.journal.LumiJson.encodeToString(app.lumisite.core.model.SessionHeader.serializer(), Fixtures.header)
        check(!session.contains("bootId"))
        File(d, "session.json").writeText("""
            {"format":"lumisite.session","formatVersion":"0.1.0","exportedUtcMs":1700000001000,"generator":"LumiSite Android 0.1.0",
             "site":$site,
             "session":$session,
             "events":[],
             "files":[${fe("raw/poses.ndjson", "raw", "PoseSample", 1)},${fe("raw/imu.ndjson", "raw", "ImuSample", 0)},${fe("raw/gps.ndjson", "raw", "GpsFix", 0)},
               ${fe("raw/gps_status.ndjson", "raw", "GpsStatusSample", 0)},${fe("raw/events.ndjson", "raw", "SessionEvent", 0)},
               ${fe("derived/trajectory_site.ndjson", "derived", "CorrectedPoint", 0)},${fe("derived/survey_points.ndjson", "derived", "DerivedSurveyPoint", 0)}],
             "derivedSummary":{"trajectoryPoints":0,"trajectoryLengthM":0.0,"posesWithoutCalibration":1,"trackingStateCounts":{"TRACKING":1},"notes":[]}}
        """.trimIndent())
        val loaded = SessionImporter.load(d)
        assertEquals(emptyList(), loaded.problems)
        assertEquals("0.1.0", loaded.manifest.formatVersion)
        assertEquals(1, loaded.poses.size)
        assertEquals(null, loaded.manifest.session.timeBases.single().bootId)
        assertEquals(emptyList(), SessionImporter.verify(d))
        assertTrue(FORMAT_VERSION.startsWith("0.2"))
    }
}
