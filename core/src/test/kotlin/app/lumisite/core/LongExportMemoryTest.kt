package app.lumisite.core

import app.lumisite.core.export.SessionExporter
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.model.Attachment
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stage 1.6: export of a synthetic 4 h session (IMU 3 sensors × 50 Hz = 2.16 M lines, poses 10 Hz, 3 × 40 MB media)
 * in a child JVM limited to -Xmx64m: the export and its streaming verification must not run out of memory.
 */
class LongExportMemoryTest {
    @Test fun fourHourSessionExportsInSixtyFourMegabytesOfHeap() {
        val work = Files.createTempDirectory("long").toFile()
        val java = File(System.getProperty("java.home"), "bin/java").path
        val p = ProcessBuilder(java, "-Xmx64m", "-cp", System.getProperty("java.class.path"), LongExportMain::class.java.name, work.path)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor(15, TimeUnit.MINUTES))
        work.deleteRecursively()
        assertEquals(0, p.exitValue(), out)
        assertEquals("OK", out.lines().last { it.isNotBlank() }, out)
    }
}

object LongExportMain {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0]); val dir = File(root, "session")
        val hours = 4L; val start = 5_000_000_000L
        SessionJournal(dir, Fixtures.header).use { j ->
            j.event(SessionStarted("e0", Fixtures.stamp(start)))
            val atts = (1..3).map { k ->
                val f = File(dir, "attachments/photo$k.jpg").apply { parentFile.mkdirs() }
                RandomAccessFile(f, "rw").use { it.setLength(40L shl 20) }
                Attachment("attachments/photo$k.jpg", "image/jpeg", app.lumisite.core.export.sha256(f), "PHOTO", 1_700_000_000_000, start)
            }
            atts.forEachIndexed { k, a ->
                j.event(SurveyEvent("s$k", Fixtures.stamp(start + k), "LUMINAIRE", "L$k", null, TrackingState.TRACKING, null, "MANUAL", attachments = listOf(a)))
            }
            val imuEvery = 20_000_000L; val n = hours * 3_600 * 1_000_000_000L / imuEvery
            for (i in 0 until n) {
                val t = start + i * imuEvery
                for (s in 1..3) j.imu(ImuSample(Fixtures.stamp(t), s, "s$s", listOf(0.01f * s, 9.81f, -0.02f), 3))
                if (i % 5 == 0L) j.pose(Fixtures.pose(t, i * 1e-4, 0.0))
            }
        }
        val out = File(root, "export")
        var last = -1f
        val m = SessionExporter.exportToDir(Fixtures.site, dir, out, 0, "test") { pr -> check(pr.fraction >= last) { "progress went back" }; last = pr.fraction }
        check(last == 1f) { "progress ended at $last" }
        check(m.files.count { it.category == "media" } == 3)
        check(m.files.single { it.path == "raw/imu.ndjson" }.records.toLong() == hours * 3_600 * 50 * 3) { m.files.toString() }
        val zip = File(root, "export.zip"); SessionExporter.zipDir(out, zip); out.deleteRecursively()
        val problems = SessionImporter.verify(zip)
        check(problems.isEmpty()) { problems.toString() }
        println("heap max ${Runtime.getRuntime().maxMemory() shr 20} MiB")
        println("OK")
    }
}
