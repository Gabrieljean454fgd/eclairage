package app.lumisite.core

import app.lumisite.core.sync.HubElection
import app.lumisite.core.sync.MultiDeviceAlign
import app.lumisite.core.sync.MultiDeviceAlign.Detection
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 3 desk simulator: 3 devices with drift, crossed signatures, a link cut and the loss of the hub. */
class MultiDeviceSyncTest {
    // True clocks: t_local = (t_true - offset) / (1 + drift)
    private val truth = mapOf("A" to (0.0 to 0.0), "B" to (12.345 to 40e-6), "C" to (-7.5 to -25e-6))
    private fun local(dev: String, t: Double) = truth.getValue(dev).let { (o, d) -> (t - o) / (1 + d) }

    private fun simulate(noiseS: Double, seed: Long = 1): List<Detection> {
        val rnd = Random(seed); val out = ArrayList<Detection>()
        val dist = mapOf(setOf("A", "B") to 4.0, setOf("A", "C") to 6.0, setOf("B", "C") to 3.0)
        val hours = 4.0; var seq = 0
        var t = 0.0
        while (t < hours * 3600) {
            for ((k, e) in listOf("A", "B", "C").withIndex()) {
                val te = t + k * 2.0 // staggered emissions (no collision)
                val hubLost = e == "A" && te > 2.5 * 3600
                if (hubLost) continue
                for (r in listOf("A", "B", "C")) {
                    if (r == e || (r == "A" && te > 2.5 * 3600)) continue
                    val bOut = (e == "B" || r == "B") && te in 3600.0..4200.0 // B out of range 10 min
                    if (bOut) continue
                    val d = dist.getValue(setOf(e, r))
                    val heard = te + d / MultiDeviceAlign.SPEED_OF_SOUND + rnd.nextGaussian() * noiseS
                    out += Detection(e, seq, local(e, te), r, local(r, heard), d)
                }
                seq++
            }
            t += 60.0 // re-sync interval: a parameter, to be measured in field trials
        }
        return out
    }

    @Test fun threeDevicesAlignDespiteCutAndHubLoss() {
        val res = MultiDeviceAlign.align(listOf("A", "B", "C"), "A", simulate(noiseS = 0.0005))
        for (dev in listOf("B", "C")) {
            val m = res.models.getValue(dev)!!
            for (t in listOf(0.0, 3700.0, 3.5 * 3600)) {
                val err = res.toRef(dev, local(dev, t))!! - t
                assertTrue(kotlin.math.abs(err) < 0.002, "$dev at $t: error $err s")
            }
            assertTrue(m.sigmaS in 0.0001..0.002, "$dev sigma ${m.sigmaS}")
            assertEquals(truth.getValue(dev).second, m.drift, 2e-7)
        }
    }

    @Test fun isolatedDeviceIsNotInvented() {
        val det = simulate(0.0005).filter { it.emitter != "C" && it.receiver != "C" }
        val res = MultiDeviceAlign.align(listOf("A", "B", "C"), "A", det)
        assertNull(res.models["C"]); assertNull(res.toRef("C", 10.0))
    }

    @Test fun noisierSignaturesGiveLargerUncertainty() {
        val calm = MultiDeviceAlign.align(listOf("A", "B", "C"), "A", simulate(0.0002)).models.getValue("B")!!.sigmaS
        val noisy = MultiDeviceAlign.align(listOf("A", "B", "C"), "A", simulate(0.005)).models.getValue("B")!!.sigmaS
        assertTrue(noisy > 5 * calm, "calm $calm noisy $noisy")
    }

    @Test fun hubFailoverIsDeterministic() {
        assertEquals("A", HubElection.hub(listOf("C", "A", "B")))
        assertEquals("B", HubElection.hub(listOf("C", "B")))
        assertNull(HubElection.hub(emptyList()))
    }
}
