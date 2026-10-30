package app.lumisite.core

import app.lumisite.core.compute.Calibrator
import app.lumisite.core.compute.Relocalizer
import app.lumisite.core.export.FORMAT_VERSION
import app.lumisite.core.export.SessionExporter
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.geo.Enu
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.CalibrationEvent
import app.lumisite.core.model.DxfHint
import app.lumisite.core.model.GpsFix
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.RelocalizationEvent
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.SyncEvent
import app.lumisite.core.model.SyncMethod
import app.lumisite.core.model.SyncRole
import app.lumisite.core.model.TrackingState
import app.lumisite.core.model.TrackingStateChanged
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JournalExportTest {
    private fun tmp(): File = Files.createTempDirectory("lumi").toFile()

    private fun recordSession(dir: File) {
        SessionJournal(dir, Fixtures.header).use { j ->
            j.event(SessionStarted("e0", Fixtures.stamp(5_000_000_000)))
            j.event(TrackingStateChanged("e1", Fixtures.stamp(5_100_000_000), TrackingState.TRACKING))
            val poses = (0..20).map { Fixtures.pose(5_100_000_000 + it * 100_000_000L, 0.0, it * 0.5) }
            poses.forEach(j::pose)
            j.imu(ImuSample(Fixtures.stamp(5_200_000_000).copy(sensorNs = 5_199_990_000), 1, "accel", listOf(0.1f, 9.8f, 0.0f), 3))
            j.gps(GpsFix(Fixtures.stamp(5_300_000_000), "gps", 48.8566, 2.3522, 12f, fixUtcMs = 1_700_000_000_300))
            val cal = Calibrator.onePointHeading("cal", poses[0].t, LocalObservation(poses[0], ReferencePoint("A", Enu(0.0, 0.0), source = "MAP_TAP")), 0.0, "MANUAL", 15.0)
            j.event(cal)
            j.event(SurveyEvent("s1", poses[10].t, "LUMINAIRE", "Applique", poses[10], TrackingState.TRACKING,
                Enu(0.0, 5.0), "AR_POSE", payloadSchema = "x-test/1", payload = buildJsonObject { put("lux", JsonPrimitive(120)) },
                dxf = DxfHint("ECL_LUMINAIRES", "LUM_GENERIC")))
            j.event(Relocalizer.relocalize("rel", poses[15].t, poses[15], ReferencePoint("B", Enu(0.4, 7.6), source = "MAP_TAP"), cal.transform))
            j.event(SyncEvent("sync1", poses[16].t, "ABC123", SyncMethod.MANUAL_TAP, SyncRole.PARTICIPANT, poses[16].t.monotonicNs))
        }
    }

    @Test fun headerIsNeverOverwritten() {
        val d = tmp(); SessionJournal(d, Fixtures.header).close()
        assertFailsWith<IllegalStateException> { SessionJournal(d, Fixtures.header) }
    }

    @Test fun truncatedTailLineIsSkippedNotFatal() {
        val d = tmp(); recordSession(d)
        File(d, Streams.POSES).appendText("{\"t\":{\"utcEpochMs\":1,") // simulated crash mid-write
        val data = SessionReader.read(d)
        assertEquals(21, data.poses.size)
        assertEquals(listOf(22), data.skippedLines[Streams.POSES])
    }

    @Test fun reopenAppendsWithoutLosingData() {
        val d = tmp(); recordSession(d)
        SessionJournal(d).use { it.event(TrackingStateChanged("late", Fixtures.stamp(9_000_000_000), TrackingState.LOST, "INSUFFICIENT_FEATURES")) }
        val data = SessionReader.read(d)
        assertEquals("late", data.events.last().id)
        assertEquals(7, data.events.size)
    }

    @Test fun format01IsReadAndNewerIsRefused() {
        val d = tmp(); recordSession(d)
        val x = File(tmp(), "x"); SessionExporter.exportToDir(Fixtures.site, d, x, 1_700_000_100_000, "test")
        val sj = File(x, "session.json"); val orig = sj.readText()
        assertTrue(orig.contains("\"$FORMAT_VERSION\""))
        sj.writeText(orig.replace("\"$FORMAT_VERSION\"", "\"0.1.0\""))
        assertEquals(emptyList(), SessionImporter.load(x).problems)
        sj.writeText(orig.replace("\"$FORMAT_VERSION\"", "\"0.3.0\""))
        assertTrue(SessionImporter.load(x).problems.single().contains("plus récent"))
        sj.writeText(orig.replace("\"$FORMAT_VERSION\"", "\"1.0.0\""))
        assertTrue(SessionImporter.load(x).problems.single().contains("plus récent"))
    }

    @Test fun exportZipRoundTrip() {
        val d = tmp(); recordSession(d)
        val out = tmp()
        val m = SessionExporter.exportToDir(Fixtures.site, d, File(out, "x"), 1_700_000_100_000, "test")
        val zip = File(out, "x.zip"); SessionExporter.zipDir(File(out, "x"), zip)
        val loaded = SessionImporter.load(zip)
        assertEquals(emptyList(), loaded.problems)
        assertEquals(FORMAT_VERSION, loaded.manifest.formatVersion)
        assertEquals(21, loaded.poses.size); assertEquals(1, loaded.imu.size); assertEquals(1, loaded.gps.size)
        assertEquals(5_199_990_000, loaded.imu[0].t.sensorNs)
        assertEquals(1, loaded.manifest.events.filterIsInstance<CalibrationEvent>().size)
        assertEquals(1, loaded.manifest.events.filterIsInstance<RelocalizationEvent>().size)
        assertEquals("ABC123", loaded.syncEvents.single().syncCode)
        // raw vs derived are separated and labelled
        assertEquals(setOf("raw"), m.files.filter { it.path.startsWith("raw/") }.map { it.category }.toSet())
        assertEquals(setOf("derived"), m.files.filter { it.path.startsWith("derived/") }.map { it.category }.toSet())
        // derived trajectory uses relocalization after its timestamp
        assertEquals(21, loaded.trajectory.size)
        assertEquals("rel", loaded.trajectory.last().transformEventId)
        assertEquals(0.4, loaded.trajectory.last().site.e, 1e-9)
        val sp = loaded.surveyPoints.single()
        assertEquals(5.0, sp.site!!.n, 1e-9); assertEquals("ECL_LUMINAIRES", sp.dxfLayer)
        val survey = loaded.manifest.events.filterIsInstance<SurveyEvent>().single()
        assertEquals(JsonPrimitive(120), survey.payload!!["lux"])
    }

    @Test fun tamperedExportIsDetected() {
        val d = tmp(); recordSession(d)
        val out = File(tmp(), "x")
        SessionExporter.exportToDir(Fixtures.site, d, out, 0, "test")
        File(out, "raw/gps.ndjson").appendText("{}\n")
        val loaded = SessionImporter.load(out)
        assertTrue(loaded.problems.any { it.contains("sha256") }, loaded.problems.toString())
    }

    @Test fun unknownFieldsFromNewerMinorVersionsAreIgnored() {
        val d = tmp(); recordSession(d)
        val out = File(tmp(), "x")
        SessionExporter.exportToDir(Fixtures.site, d, out, 0, "test")
        val f = File(out, "session.json")
        f.writeText(f.readText().replaceFirst("{", "{\"futureField\": {\"a\": 1},"))
        assertEquals("sess-1", SessionImporter.load(out).manifest.session.id)
    }

    @Test fun resumeAfterCrashMidLineKeepsNewRecordsReadable() {
        val d = tmp(); recordSession(d)
        File(d, Streams.POSES).appendText("{\"t\":{\"utcEpochMs\":1,") // crash mid-write
        SessionJournal(d).use { it.pose(Fixtures.pose(9_000_000_000, 1.0, 1.0)) }
        val data = SessionReader.read(d)
        assertEquals(22, data.poses.size)
        assertEquals(9_000_000_000, data.poses.last().t.monotonicNs)
        assertEquals(listOf(22), data.skippedLines[Streams.POSES])
    }

    @Test fun exportWhileJournalIsStillWrittenIsConsistent() {
        val d = tmp(); recordSession(d)
        val j = SessionJournal(d)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val writer = Thread {
            var i = 0L
            while (!stop.get()) j.imu(ImuSample(Fixtures.stamp(6_000_000_000 + i++), 1, "accel", listOf(0.1f, 9.8f, 0.0f), 3))
        }
        writer.start()
        try {
            repeat(3) { k ->
                Thread.sleep(50)
                val out = File(tmp(), "x$k")
                SessionExporter.exportToDir(Fixtures.site, d, out, 0, "test")
                val loaded = SessionImporter.load(out)
                assertEquals(emptyList(), loaded.problems)
                assertTrue(loaded.imu.size > 1)
            }
        } finally { stop.set(true); writer.join(); j.close() }
    }

    @Test fun sourceTruncationIsAWarningNotAnExportProblem() {
        val d = tmp(); recordSession(d)
        File(d, Streams.IMU).appendText("{\"t\":{\"utcEpochMs\":1,")
        SessionJournal(d).use { it.imu(ImuSample(Fixtures.stamp(9_000_000_000), 1, "accel", listOf(0f, 9.8f, 0f), 3)) }
        val out = File(tmp(), "x")
        val m = SessionExporter.exportToDir(Fixtures.site, d, out, 0, "test")
        assertEquals(mapOf("raw/imu.ndjson" to listOf(2)), m.integrity)
        val loaded = SessionImporter.load(out)
        assertEquals(emptyList(), loaded.problems)
        assertEquals(1, loaded.warnings.size)
        assertEquals(2, loaded.imu.size)
    }
}
