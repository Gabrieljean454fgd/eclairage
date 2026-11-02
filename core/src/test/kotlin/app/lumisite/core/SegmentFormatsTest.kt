package app.lumisite.core

import app.lumisite.core.export.ResumableExport
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.journal.Segments
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SessionStarted
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D15 (gzip segments) and LOT 1.5 (old formats stay readable, no destructive migration). */
class SegmentFormatsTest {
    private val s = 1_000_000_000L

    @Test fun compressedImuSegmentsAreReadOnceAndExported() {
        val dir = Files.createTempDirectory("gz").toFile()
        SessionJournal(dir, Fixtures.header).use { j ->
            j.event(SessionStarted("e0", Fixtures.stamp(5 * s)))
            repeat(300) { j.imu(ImuSample(Fixtures.stamp(5 * s + it * 10_000_000L), 1, "accel", listOf(0.1f, 9.8f, 0.0f), 3)) }
            j.rotateAll()
            repeat(200) { j.imu(ImuSample(Fixtures.stamp(9 * s + it * 10_000_000L), 1, "accel", listOf(0.1f, 9.8f, 0.0f), 3)) }
        }
        val closed = Segments.list(dir).filter { it.stream == Streams.IMU && !it.open }.map { it.file }
        val plainBytes = closed.sumOf { it.length() }
        val gz = closed.map { Segments.compress(it) }
        assertTrue(gz.all { it.name.endsWith(".ndjson.gz") && it.isFile } && closed.none { it.exists() })
        assertTrue(gz.sumOf { it.length() } * 5 < plainBytes, "gzip divides the IMU volume by more than 5")
        assertEquals(500, SessionReader.read(dir).imu.size)
        // Stop between the rename and the deletion: the plain file wins, no line is read twice.
        val again = Segments.file(dir, Streams.IMU, 1, part = false)
        java.util.zip.GZIPInputStream(gz.first().inputStream()).use { i -> again.outputStream().use { i.copyTo(it) } }
        assertEquals(500, SessionReader.read(dir).imu.size)
        val r = ResumableExport.run(Fixtures.site, dir, Files.createTempDirectory("exp").toFile(), "test", 1_700_000_000_000)
        assertEquals(500, r.manifest.entries.first { it.name == "raw/imu.ndjson" }.records)
        assertEquals(emptyList(), SessionImporter.verify(r.zip))
    }

    @Test fun legacyRootFilesOfVersion01AreStillReadAndExported() {
        // Format 0.1/0.2: one file per stream at the session root (events, poses.ndjson, imu.ndjson), no seg/.
        val dir = Files.createTempDirectory("old").toFile()
        File(dir, Streams.HEADER).writeText(LumiJson.encodeToString(app.lumisite.core.model.SessionHeader.serializer(), Fixtures.header))
        File(dir, Streams.EVENTS).writeText(LumiJson.encodeToString(SessionEvent.serializer(), SessionStarted("e0", Fixtures.stamp(5 * s))) + "\n")
        File(dir, Streams.POSES).writeText((0..9).joinToString("") { LumiJson.encodeToString(PoseSample.serializer(), Fixtures.pose(5 * s + it * 100_000_000L, 0.1 * it, 0.0)) + "\n" })
        File(dir, Streams.IMU).writeText(LumiJson.encodeToString(ImuSample.serializer(), ImuSample(Fixtures.stamp(5 * s), 1, "accel", listOf(0f, 9.8f, 0f), 3)) + "\n")
        val before = dir.walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes().contentHashCode() }
        val d = SessionReader.read(dir)
        assertEquals(1, d.events.size); assertEquals(10, d.poses.size); assertEquals(1, d.imu.size)
        val r = ResumableExport.run(Fixtures.site, dir, Files.createTempDirectory("exp").toFile(), "test", 1_700_000_000_000)
        assertEquals(emptyList(), SessionImporter.verify(r.zip))
        assertEquals(before, dir.walkTopDown().filter { it.isFile && it.path in before }.associate { it.path to it.readBytes().contentHashCode() },
            "reading and exporting never modify the old files")
        // Writing again (resume) appends in seg/ without touching the old root files.
        SessionJournal(dir).use { it.pose(Fixtures.pose(20 * s, 1.0, 1.0)) }
        assertEquals(11, SessionReader.read(dir).poses.size)
    }
}
