package app.lumisite.core

import app.lumisite.core.journal.Segments
import app.lumisite.core.journal.SegmentedAppender
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.SessionStarted
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 1.2 / reserve R2: segments, atomic finalization, what an abrupt stop leaves and how it is repaired. */
class JournalSegmentsTest {
    private fun tmp(): File = Files.createTempDirectory("seg").toFile()
    private fun imu(i: Long) = ImuSample(Fixtures.stamp(6_000_000_000 + i * 20_000_000), 1, "accel", listOf(0.1f, 9.8f, 0f), 3)

    @Test fun segmentsRotateBySizeAndAgeAndAreReportedWhenClosed() {
        val d = tmp(); var now = 0L
        val closed = ArrayList<File>()
        SegmentedAppender(d, Streams.IMU, flushEvery = 10, maxBytes = 4_096, maxAgeMs = 60_000, nowMs = { now }, onSegmentClosed = { closed += it }).use { a ->
            repeat(200) { a.appendLine("{\"i\":$it,\"pad\":\"${"x".repeat(40)}\"}") }
            now += 61_000; a.appendLine("{\"i\":200}") // age rotation
        }
        val segs = Segments.list(d)
        assertTrue(segs.size >= 3, "rotations: ${segs.size}")
        assertTrue(segs.none { it.open }, "close() finalizes the last segment")
        assertEquals(segs.map { it.file.name }, closed.map { it.name })
        val lines = segs.flatMap { it.file.readLines() }
        assertEquals((0..200).toList(), lines.map { Regex("\"i\":(\\d+)").find(it)!!.groupValues[1].toInt() })
    }

    @Test fun abruptStopLeavesAPartSegmentThatIsRepairedAtNextOpening() {
        val d = tmp()
        val j = SessionJournal(d, Fixtures.header)
        j.event(SessionStarted("e0", Fixtures.stamp(5_000_000_000)))
        repeat(1_000) { j.imu(imu(it.toLong())) }
        j.flushAll() // what the periodic fsync guarantees
        // abrupt stop: no close(); the process dies in the middle of a line
        val part = Segments.list(d).first { it.stream == Streams.IMU && it.open }.file
        part.appendText("{\"t\":{\"utcEpochMs\":1,")

        val j2 = SessionJournal(d)
        val rep = j2.repaired.single { it.stream == Streams.IMU }
        assertTrue(rep.truncatedTail)
        assertTrue(rep.file.name.endsWith(".ndjson"))
        j2.imu(imu(5_000)); j2.close()
        val data = SessionReader.read(d)
        assertEquals(1_001, data.imu.size, "every fsynced line + the new one survive")
        assertEquals(listOf(1_001), data.skippedLines[Streams.IMU], "the truncated line is kept and reported, not invented")
        assertTrue(Segments.list(d).none { it.open })
    }

    @Test fun finishedSegmentsNeverChangeAfterClosing() {
        val d = tmp()
        SessionJournal(d, Fixtures.header).use { j -> repeat(10) { j.imu(imu(it.toLong())) }; j.rotateAll(); repeat(10) { j.imu(imu(100L + it)) } }
        val segs = Segments.list(d).filter { it.stream == Streams.IMU }
        assertEquals(2, segs.size)
        val firstHash = app.lumisite.core.export.sha256(segs[0].file)
        SessionJournal(d).use { j -> j.imu(imu(500)) }
        assertEquals(firstHash, app.lumisite.core.export.sha256(segs[0].file))
        assertEquals(21, SessionReader.read(d).imu.size)
    }

    @Test fun legacySingleFileSessionsAreStillRead() {
        val d = tmp()
        SessionJournal(d, Fixtures.header).close()
        File(d, Streams.IMU).writeText(app.lumisite.core.journal.LumiJson.encodeToString(ImuSample.serializer(), imu(0)) + "\n")
        SessionJournal(d).use { it.imu(imu(1)) }
        assertEquals(listOf(6_000_000_000L, 6_020_000_000L), SessionReader.read(d).imu.map { it.t.monotonicNs })
    }
}
