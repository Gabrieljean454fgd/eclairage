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

/**
 * Identification frame sent right after the chirp (protocol "lumisite-chirp-id/1"): 32 bits, BFSK, Hann-windowed
 * 10 ms per bit at 7 kHz (0) / 8.5 kHz (1), starting 20 ms after the chirp end. Bits: emitter id (16), emission
 * counter (8), checksum (8) = id_hi xor id_lo xor counter xor 0xA5. Own implementation (no ggwave dependency).
 */
object IdFrame {
    const val PROTOCOL = "lumisite-chirp-id/1"
    const val F_ZERO = 7_000.0
    const val F_ONE = 8_500.0
    const val BIT_MS = 10.0
    const val GAP_MS = 20.0
    const val BITS = 32

    data class Decoded(val emitterId: Int, val counter: Int, val ok: Boolean) {
        val hex: String get() = "%04X".format(emitterId)
    }

    fun idOf(deviceId: String): Int = deviceId.hashCode() and 0xFFFF
    private fun checksum(id: Int, counter: Int) = ((id shr 8) xor (id and 0xFF) xor (counter and 0xFF) xor 0xA5) and 0xFF
    private fun bitsOf(id: Int, counter: Int): Long = (id.toLong() shl 16) or ((counter and 0xFF).toLong() shl 8) or checksum(id, counter).toLong()

    fun bitSamples(rate: Int) = (rate * BIT_MS / 1000).toInt()
    fun offsetAfterChirpStart(rate: Int, chirpMs: Double = Chirp.DEFAULT_DURATION_MS) = (rate * (chirpMs + GAP_MS) / 1000).toInt()

    fun encode(id: Int, counter: Int, rate: Int = Chirp.DEFAULT_SAMPLE_RATE): FloatArray {
        val b = bitSamples(rate); val bits = bitsOf(id, counter)
        return FloatArray(BITS * b) { i ->
            val bit = (bits shr (BITS - 1 - i / b)) and 1L
            val k = i % b
            val win = 0.5 - 0.5 * kotlin.math.cos(2 * PI * k / (b - 1))
            (win * sin(2 * PI * (if (bit == 1L) F_ONE else F_ZERO) * i / rate)).toFloat()
        }
    }

    /** Chirp + gap + frame, ready to play. */
    fun signal(id: Int, counter: Int, rate: Int = Chirp.DEFAULT_SAMPLE_RATE): FloatArray {
        val chirp = Chirp.generate(rate); val frame = encode(id, counter, rate); val off = offsetAfterChirpStart(rate)
        return FloatArray(off + frame.size).also { out -> chirp.copyInto(out); frame.copyInto(out, off) }
    }

    private fun goertzel(x: FloatArray, from: Int, n: Int, f: Double, rate: Int): Double {
        val w = 2 * kotlin.math.cos(2 * PI * f / rate); var s1 = 0.0; var s2 = 0.0
        for (i in from until from + n) { val s0 = x[i] + w * s1 - s2; s2 = s1; s1 = s0 }
        return s1 * s1 + s2 * s2 - w * s1 * s2
    }

    /** Decodes the frame following a chirp detected at [chirpStart]; null if the recording is too short. */
    fun decode(x: FloatArray, chirpStart: Int, rate: Int = Chirp.DEFAULT_SAMPLE_RATE): Decoded? {
        val b = bitSamples(rate); val start = chirpStart + offsetAfterChirpStart(rate)
        if (start < 0 || start + BITS * b > x.size) return null
        var bits = 0L
        for (i in 0 until BITS) bits = (bits shl 1) or (if (goertzel(x, start + i * b, b, F_ONE, rate) > goertzel(x, start + i * b, b, F_ZERO, rate)) 1L else 0L)
        val id = ((bits shr 16) and 0xFFFF).toInt(); val ctr = ((bits shr 8) and 0xFF).toInt()
        return Decoded(id, ctr, (bits and 0xFF).toInt() == checksum(id, ctr))
    }
}

/** Several chirps in one recording (all devices of a "Synchroniser" round, or a long dictaphone recording). */
object MultiChirp {
    const val MIN_SCORE = 0.3
    const val MIN_SNR = 8.0

    data class Heard(val detection: ChirpDetection, val frame: IdFrame.Decoded?)

    /** Coarse scan (step [coarse]) then refinement at sample resolution around every peak. */
    fun detectAll(signal: FloatArray, template: FloatArray, rate: Int = Chirp.DEFAULT_SAMPLE_RATE, minGapMs: Double = 400.0, coarse: Int = 4): List<Heard> {
        val m = template.size
        if (signal.size < m) return emptyList()
        val tNorm = sqrt(template.sumOf { (it * it).toDouble() })
        val e = DoubleArray(signal.size + 1)
        for (i in signal.indices) e[i + 1] = e[i] + signal[i] * signal[i]
        fun score(s: Int): Double {
            var acc = 0.0
            for (k in 0 until m) acc += signal[s + k] * template[k]
            val wn = sqrt(e[s + m] - e[s])
            return if (wn > 1e-9) abs(acc) / (wn * tNorm) else 0.0
        }
        val count = (signal.size - m) / coarse + 1
        val sc = DoubleArray(count) { score(it * coarse) }
        val median = sc.sorted()[count / 2].coerceAtLeast(1e-9)
        val gap = (rate * minGapMs / 1000 / coarse).toInt()
        val peaks = sc.indices.filter { j -> sc[j] >= MIN_SCORE && sc[j] / median >= MIN_SNR }.sortedByDescending { sc[it] }
        val kept = mutableListOf<Int>()
        peaks.forEach { j -> if (kept.none { kotlin.math.abs(it - j) < gap }) kept += j }
        return kept.map { j ->
            val c = j * coarse
            val best = (maxOf(0, c - coarse)..minOf(signal.size - m, c + coarse)).maxBy { score(it) }
            Heard(ChirpDetection(best, score(best), median), IdFrame.decode(signal, best, rate))
        }.sortedBy { it.detection.index }
    }
}
