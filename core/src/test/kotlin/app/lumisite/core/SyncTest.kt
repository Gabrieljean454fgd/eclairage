package app.lumisite.core

import app.lumisite.core.model.SyncEvent
import app.lumisite.core.model.SyncMethod
import app.lumisite.core.model.SyncRole
import app.lumisite.core.sync.Chirp
import app.lumisite.core.sync.ChirpDetector
import app.lumisite.core.sync.DeviceSyncLog
import app.lumisite.core.sync.SyncEstimator
import app.lumisite.core.time.Stamp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncTest {
    private fun ev(code: String, mono: Long, tb: String) =
        SyncEvent("id-$code-$tb", Stamp(0, mono, tb), code, SyncMethod.AUDIO_CHIRP, SyncRole.RECEIVER, mono)

    @Test fun offsetAndDriftFromSharedCodes() {
        // device B clock = A clock - 3 s, and runs 50 ppm fast
        val aTimes = listOf(10_000_000_000L, 70_000_000_000L, 130_000_000_000L)
        val a = DeviceSyncLog("A", "tbA", aTimes.mapIndexed { i, t -> ev("c$i", t, "tbA") })
        val b = DeviceSyncLog("B", "tbB", aTimes.mapIndexed { i, t ->
            ev("c$i", ((t - 3_000_000_000L) * (1 + 50e-6)).toLong(), "tbB")
        })
        val est = assertNotNull(SyncEstimator.estimate(a, b))
        assertEquals(3, est.sharedCodes)
        assertEquals(-50.0, est.driftPpm!!, 0.5) // ref - dev offset decreases as dev runs fast
        assertTrue(est.spreadNs > 0)
        assertEquals(3_000_000_000.0 - 3.5e6, est.meanOffsetNs, 1e6)
    }

    @Test fun noSharedCodeGivesNoEstimate() {
        assertNull(SyncEstimator.estimate(DeviceSyncLog("A", "a", listOf(ev("x", 1, "a"))), DeviceSyncLog("B", "b", listOf(ev("y", 1, "b")))))
    }

    @Test fun singleEventEstimateHasCaveat() {
        val est = SyncEstimator.estimate(DeviceSyncLog("A", "a", listOf(ev("x", 100, "a"))), DeviceSyncLog("B", "b", listOf(ev("x", 40, "b"))))!!
        assertEquals(60.0, est.meanOffsetNs); assertNull(est.driftPpm)
        assertTrue(est.caveats.any { it.contains("Single shared event") })
    }

    /** Detector check on a synthetic recording (this validates the DSP code only, not real acoustics). */
    @Test fun chirpDetectedInNoiseAtCorrectIndex() {
        val tpl = Chirp.generate()
        val rnd = Random(1)
        val signal = FloatArray(48_000) { (rnd.nextFloat() - 0.5f) * 0.4f }
        val at = 23_456
        for (i in tpl.indices) signal[at + i] += tpl[i] * 0.3f
        val d = assertNotNull(ChirpDetector.detect(signal, tpl))
        assertEquals(at, d.index)
        assertTrue(d.snr > 5, "snr=${d.snr}")
    }
}
