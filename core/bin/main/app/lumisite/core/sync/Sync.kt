package app.lumisite.core.sync

import app.lumisite.core.model.SyncEvent
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Sync events of one device/time base, as read from its session. */
data class DeviceSyncLog(val deviceId: String, val timeBaseId: String, val events: List<SyncEvent>)

@Serializable
data class SyncPairEstimate(
    val referenceDeviceId: String,
    val deviceId: String,
    val referenceTimeBaseId: String,
    val timeBaseId: String,
    /** offset such that monotonic_ref ≈ monotonic_dev + offsetNs. One value per shared sync code. */
    val perCodeOffsetsNs: Map<String, Long>,
    val meanOffsetNs: Double,
    /** max - min of per-code offsets: observed inconsistency (includes drift + detection error). */
    val spreadNs: Long,
    /** Linear drift estimate (ppm), only with >= 2 codes. */
    val driftPpm: Double?,
    val sharedCodes: Int,
    val method: String,
    val caveats: List<String>,
)

object SyncEstimator {
    /**
     * Offsets between monotonic clocks from shared sync events (same syncCode on both devices).
     * Acoustic propagation delay (≈2.9 ms per metre) and audio pipeline latency are NOT compensated.
     */
    fun estimate(reference: DeviceSyncLog, other: DeviceSyncLog): SyncPairEstimate? {
        val refByCode = reference.events.associateBy { it.syncCode }
        val pairs = other.events.mapNotNull { e -> refByCode[e.syncCode]?.let { r -> Triple(e.syncCode, r.eventMonotonicNs, e.eventMonotonicNs) } }
        if (pairs.isEmpty()) return null
        val offsets = pairs.associate { (c, r, d) -> c to (r - d) }
        val mean = offsets.values.map { it.toDouble() }.average()
        val drift = if (pairs.size >= 2) {
            val xs = pairs.map { it.third.toDouble() }; val ys = pairs.map { (it.second - it.third).toDouble() }
            val mx = xs.average(); val my = ys.average()
            val den = xs.sumOf { (it - mx) * (it - mx) }
            if (den > 0) xs.zip(ys).sumOf { (x, y) -> (x - mx) * (y - my) } / den * 1e6 else null
        } else null
        val methods = (reference.events + other.events).map { it.method.name }.distinct().joinToString("+")
        val caveats = buildList {
            add("Audio propagation delay (~2.9 ms/m) and input pipeline latency are not compensated.")
            if (pairs.size < 2) add("Single shared event: drift and repeatability cannot be assessed.")
            if (methods.contains("MANUAL_TAP")) add("MANUAL_TAP depends on human reaction time (typically tens to hundreds of ms).")
        }
        return SyncPairEstimate(
            reference.deviceId, other.deviceId, reference.timeBaseId, other.timeBaseId, offsets, mean,
            offsets.values.max() - offsets.values.min(), drift, pairs.size, methods, caveats,
        )
    }
}

/** Linear chirp used as an experimental acoustic sync marker. */
object Chirp {
    const val DEFAULT_SAMPLE_RATE = 48_000
    const val DEFAULT_F0 = 2_000.0
    const val DEFAULT_F1 = 6_000.0
    const val DEFAULT_DURATION_MS = 60.0

    fun generate(sampleRate: Int = DEFAULT_SAMPLE_RATE, f0: Double = DEFAULT_F0, f1: Double = DEFAULT_F1, durationMs: Double = DEFAULT_DURATION_MS): FloatArray {
        val n = (sampleRate * durationMs / 1000.0).toInt()
        val dur = n.toDouble() / sampleRate
        val k = (f1 - f0) / dur
        return FloatArray(n) { i ->
            val tt = i.toDouble() / sampleRate
            val win = 0.5 - 0.5 * kotlin.math.cos(2 * PI * i / (n - 1)) // Hann window
            (win * sin(2 * PI * (f0 * tt + 0.5 * k * tt * tt))).toFloat()
        }
    }
}

data class ChirpDetection(val index: Int, val score: Double, val noiseFloor: Double) {
    /** Peak-to-median ratio; a weak ratio means the detection should not be trusted. */
    val snr: Double get() = if (noiseFloor > 0) score / noiseFloor else Double.POSITIVE_INFINITY
}

object ChirpDetector {
    /**
     * Normalised cross-correlation of [signal] with [template]. Returns the start index of the best match.
     * Brute force O(N·M): fine for a few seconds of audio at 48 kHz on a tablet.
     */
    fun detect(signal: FloatArray, template: FloatArray, step: Int = 1): ChirpDetection? {
        val m = template.size
        if (signal.size < m) return null
        val tNorm = sqrt(template.sumOf { (it * it).toDouble() })
        // prefix sums of energy for window normalisation
        val e = DoubleArray(signal.size + 1)
        for (i in signal.indices) e[i + 1] = e[i] + signal[i] * signal[i]
        val count = (signal.size - m) / step + 1
        val scores = DoubleArray(count)
        var best = -1; var bestScore = 0.0
        for (j in 0 until count) {
            val s = j * step
            var acc = 0.0
            for (k in 0 until m) acc += signal[s + k] * template[k]
            val wn = sqrt(e[s + m] - e[s])
            val sc = if (wn > 1e-9) abs(acc) / (wn * tNorm) else 0.0
            scores[j] = sc
            if (sc > bestScore) { bestScore = sc; best = s }
        }
        if (best < 0) return null
        val median = scores.sorted()[count / 2]
        return ChirpDetection(best, bestScore, median)
    }
}
