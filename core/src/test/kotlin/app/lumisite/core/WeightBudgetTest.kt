package app.lumisite.core

import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.Segments
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.ImuSample
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D15: IMU volume written by the real journal code for simulated sessions (noisy values, so gzip is not flattered).
 * Before = 9 motion sensors at ~50 Hz + D9 duplicate; after = accel/gyro 100 Hz, magnetometer/rotation 10 Hz, gzip, no duplicate.
 */
class WeightBudgetTest {
    private data class S(val type: Int, val name: String, val n: Int, val hz: Int)
    private val before = listOf(S(1, "accel", 3, 50), S(4, "gyro", 3, 50), S(2, "mag", 3, 50), S(11, "rot", 5, 50), S(15, "game_rot", 4, 50),
        S(9, "gravity", 3, 50), S(35, "accel_uncal", 6, 50), S(16, "gyro_uncal", 6, 50), S(14, "mag_uncal", 6, 50))
    private val after = listOf(S(1, "accel", 3, 100), S(4, "gyro", 3, 100), S(2, "mag", 3, 10), S(11, "rot", 5, 10))

    private fun write(sensors: List<S>, minutes: Int, d9: Boolean, gzip: Boolean): Long {
        val dir = Files.createTempDirectory("w").toFile()
        val rnd = Random(1)
        SessionJournal(dir, Fixtures.header, captationFrames = d9).use { j ->
            val end = minutes * 60_000_000_000L
            sensors.forEach { s ->
                val step = 1_000_000_000L / s.hz
                var t = 0L
                while (t < end) {
                    j.imu(ImuSample(Fixtures.stamp(5_000_000_000 + t).copy(sensorNs = t), s.type, s.name, List(s.n) { rnd.nextFloat() * 2 - 1 + 9.8f * (it == 1).compareTo(false) }, 3))
                    t += step
                }
            }
            j.rotateAll()
        }
        if (gzip) Segments.list(dir).filter { it.stream == Streams.IMU && !it.open }.forEach { Segments.compress(it.file) }
        val bytes = dir.walkTopDown().filter { it.isFile && (it.name.startsWith(Segments.base(Streams.IMU) + ".") || it.name.startsWith(Segments.base(Streams.CAPTATION) + ".")) }.sumOf { it.length() }
        dir.deleteRecursively()
        return bytes
    }

    @Test fun imuWeightBeforeAndAfterD15() {
        val mo = { b: Long -> "%.1f Mo".format(b / 1e6) }
        val before10 = write(before, 10, d9 = true, gzip = false)
        val after10Plain = write(after, 10, d9 = false, gzip = false)
        val after10 = write(after, 10, d9 = false, gzip = true)
        val after35 = write(after, 35, d9 = false, gzip = true)
        println("D15 IMU 10 min : avant ${mo(before10)} (35 min ≈ ${mo(before10 * 7 / 2)}) ; après sans gzip ${mo(after10Plain)} ; après gzip ${mo(after10)} ; 35 min après gzip ${mo(after35)}")
        assertTrue(after35 < 40_000_000, "IMU 35 min after D15 = ${mo(after35)}")
    }
}
