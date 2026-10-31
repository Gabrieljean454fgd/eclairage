package app.lumisite.core.sync

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Stage 3 specification (docs/ETAPE3_SPECIFICATIONS.md §4), desk side: every device emits its own sound signature
 * (id + sequence number) and logs, in its OWN clock, when it emitted and when it heard the others. Each device clock is
 * modelled as t_ref = offset + (1 + drift) * t_local; the reference device has offset = drift = 0.
 * Pairwise detections give linear equations; they are solved by least squares, with a residual-based uncertainty
 * per device. A device that never connects to the reference graph is NOT aligned (null), never guessed (R1).
 */
object MultiDeviceAlign {
    /** [emitter] emitted signature at [emitLocalS] (emitter clock); [receiver] heard it at [hearLocalS] (receiver clock). */
    data class Detection(val emitter: String, val seq: Int, val emitLocalS: Double, val receiver: String, val hearLocalS: Double, val distanceM: Double? = null)

    data class ClockModel(val device: String, val offsetS: Double, val drift: Double, val sigmaS: Double, val detections: Int)

    data class Result(val reference: String, val models: Map<String, ClockModel?>) {
        fun toRef(device: String, localS: Double): Double? = models[device]?.let { it.offsetS + (1 + it.drift) * localS }
    }

    const val SPEED_OF_SOUND = 343.0

    fun align(devices: List<String>, reference: String, detections: List<Detection>): Result {
        val connected = connectedTo(reference, devices, detections)
        val unknown = devices.filter { it != reference && it in connected }
        val idx = unknown.withIndex().associate { (i, d) -> d to i }
        val n = unknown.size * 2
        val rows = ArrayList<Pair<DoubleArray, Double>>()
        for (d in detections) {
            if (d.emitter !in connected || d.receiver !in connected) continue
            // offset_e + (1+drift_e) te + delay = offset_r + (1+drift_r) tr
            val row = DoubleArray(n)
            var rhs = d.hearLocalS - d.emitLocalS - (d.distanceM?.div(SPEED_OF_SOUND) ?: 0.0)
            idx[d.emitter]?.let { row[2 * it] += 1.0; row[2 * it + 1] += d.emitLocalS }
            idx[d.receiver]?.let { row[2 * it] -= 1.0; row[2 * it + 1] -= d.hearLocalS }
            rows += row to rhs
        }
        val x = if (n == 0) DoubleArray(0) else solveLeastSquares(rows, n)
        val residualsBy = HashMap<String, MutableList<Double>>()
        for ((row, rhs) in rows) {
            val r = row.indices.sumOf { row[it] * x[it] } - rhs
            rows.size.let { }
            for ((dev, i) in idx) if (row[2 * i] != 0.0) residualsBy.getOrPut(dev) { ArrayList() } += r
        }
        val models = devices.associateWith { dev ->
            when {
                dev == reference -> ClockModel(dev, 0.0, 0.0, 0.0, detections.count { it.emitter == dev || it.receiver == dev })
                dev !in idx -> null
                else -> {
                    val res = residualsBy[dev].orEmpty()
                    val sigma = if (res.size < 3) Double.POSITIVE_INFINITY else sqrt(res.sumOf { it * it } / (res.size - 2))
                    ClockModel(dev, x[2 * idx.getValue(dev)], x[2 * idx.getValue(dev) + 1], sigma, res.size)
                }
            }
        }
        return Result(reference, models)
    }

    private fun connectedTo(ref: String, devices: List<String>, det: List<Detection>): Set<String> {
        val seen = mutableSetOf(ref); var grew = true
        while (grew) {
            grew = false
            for (d in det) {
                if (d.emitter in seen && d.receiver !in seen && d.receiver in devices) { seen += d.receiver; grew = true }
                if (d.receiver in seen && d.emitter !in seen && d.emitter in devices) { seen += d.emitter; grew = true }
            }
        }
        return seen
    }

    /** Normal equations (AᵀA)x = Aᵀb solved by Gaussian elimination with partial pivoting; columns are rescaled first. */
    private fun solveLeastSquares(rows: List<Pair<DoubleArray, Double>>, n: Int): DoubleArray {
        val scale = DoubleArray(n) { c -> rows.maxOfOrNull { abs(it.first[c]) }?.takeIf { it > 0 } ?: 1.0 }
        val m = Array(n) { DoubleArray(n + 1) }
        for ((row, rhs) in rows) for (i in 0 until n) {
            val ri = row[i] / scale[i]; if (ri == 0.0) continue
            for (j in 0 until n) m[i][j] += ri * row[j] / scale[j]
            m[i][n] += ri * rhs
        }
        for (c in 0 until n) {
            val p = (c until n).maxBy { abs(m[it][c]) }
            require(abs(m[p][c]) > 1e-12) { "Alignement indéterminé (pas assez de détections)" }
            val t = m[p]; m[p] = m[c]; m[c] = t
            for (r in 0 until n) if (r != c) { val f = m[r][c] / m[c][c]; for (k in c..n) m[r][k] -= f * m[c][k] }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] / scale[it] }
    }
}

/** Hub failover: the alive device with the smallest id becomes hub (deterministic, no negotiation needed). */
object HubElection {
    fun hub(alive: Collection<String>): String? = alive.minOrNull()
}
