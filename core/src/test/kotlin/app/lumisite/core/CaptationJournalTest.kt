package app.lumisite.core

import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.journal.Segments
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.GpsFix
import app.lumisite.core.model.ImuSample
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D9: the session journal writes D9 capture frames, seq without gap across a restart, historical streams unchanged. */
class CaptationJournalTest {
    private fun frames(dir: java.io.File) = Segments.files(dir, Streams.CAPTATION).flatMap { it.readLines() }.filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun journalWritesD9FramesAndKeepsHistoricalStreams() {
        val dir = Files.createTempDirectory("cap").toFile()
        SessionJournal(dir, Fixtures.header).use { j ->
            j.pose(Fixtures.pose(6_000_000_000, 1.0, 2.0))
            j.imu(ImuSample(Fixtures.stamp(6_010_000_000).copy(sensorNs = 123), 4, "gyro", listOf(0.1f, 0.2f, 0.3f), 3))
            j.imu(ImuSample(Fixtures.stamp(6_020_000_000), 11, "rot", listOf(0f, 0f, 0f, 1f), 3))
            j.gps(GpsFix(Fixtures.stamp(6_030_000_000), "gps", 48.85, 2.35, horizontalAccuracyM = 40f, satellitesUsed = 3))
        }
        val f = frames(dir)
        assertEquals(listOf("pose", "imu.gyro", "imu.rotation", "gnss"), f.map { it["type"]!!.jsonPrimitive.content })
        assertEquals(listOf(1L, 2L, 3L, 4L), f.map { it["seq"]!!.jsonPrimitive.long })
        assertEquals(6_000_000_000, f[0]["t_mono_ns"]!!.jsonPrimitive.long)
        assertTrue(f.all { it["clock_offset_ns"].toString() == "null" && it["device_id"]!!.jsonPrimitive.content == "dev-1" && it["time_base"]!!.jsonPrimitive.content == "tb-1" })
        assertEquals("rad/s", f[1]["v"]!!.jsonObject["unit"]!!.jsonPrimitive.content)
        assertEquals(123L, f[1]["v"]!!.jsonObject["sensor_ns"]!!.jsonPrimitive.long)
        assertEquals(3L, f[3]["v"]!!.jsonObject["sats_used"]!!.jsonPrimitive.long)

        // Restart (resume without header): numbering continues without gap.
        SessionJournal(dir).use { it.pose(Fixtures.pose(9_000_000_000, 0.0, 0.0)) }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), frames(dir).map { it["seq"]!!.jsonPrimitive.long })

        // Historical reading is unchanged.
        val d = SessionReader.read(dir)
        assertEquals(2, d.poses.size); assertEquals(2, d.imu.size); assertEquals(1, d.gps.size)
        assertTrue(d.skippedLines.isEmpty())
    }
}
