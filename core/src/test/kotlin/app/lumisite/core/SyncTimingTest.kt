package app.lumisite.core

import app.lumisite.core.sync.Chirp
import app.lumisite.core.sync.MultiChirp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/** Session > Synchro: measures the detection phase on a full 12 s window (diagnostic of the slow button). */
class SyncTimingTest {
    @Test
    fun detectionOnTwelveSecondWindowIsTimed() {
        val rate = Chirp.DEFAULT_SAMPLE_RATE
        val rnd = Random(1)
        val signal = FloatArray(rate * 12) { (rnd.nextFloat() - 0.5f) * 0.02f }
        val chirp = Chirp.generate()
        val at = rate * 5
        for (i in chirp.indices) signal[at + i] += chirp[i] * 0.5f
        MultiChirp.detectAll(signal, chirp, rate) // warm-up (JIT)
        val t0 = System.nanoTime()
        val heard = MultiChirp.detectAll(signal, chirp, rate)
        val ms = (System.nanoTime() - t0) / 1e6
        println("TIMING detectAll 12 s @ $rate Hz : %.0f ms, %d chirp(s)".format(ms, heard.size))
        assertTrue(heard.any { kotlin.math.abs(it.detection.index - at) < rate / 100 })
    }
}
