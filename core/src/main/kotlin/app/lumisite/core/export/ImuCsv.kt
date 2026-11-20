package app.lumisite.core.export

import app.lumisite.core.model.ImuSample
import app.lumisite.core.time.TimeBase
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.round

/**
 * v176f F2: IMU of the session export as `raw/imu.csv.gz` — one gzipped CSV instead of `raw/imu.ndjson`
 * (~22x smaller on the 15/11 capture, docs/PROPOSITIONS.md §2.1). The on-device journal (`seg/`) is unchanged.
 *
 * Layout: comment lines start with `#`; the first data line is the column header
 * `dt_us,sensor_dt_us,type,x,y,z,w`, then one row per sample in journal order.
 * - `dt_us`: (monotonicNs − t_mono_ns_initial of the sample's time base) / 1000, truncated to µs.
 * - `sensor_dt_us`: same origin for Stamp.sensorNs (empty when the sensor gave no timestamp).
 * - `type`: D9 vocabulary (`imu.accel`, `imu.gyro`, `imu.mag`, `imu.rotation`, `imu.other.<Sensor.TYPE_*>`).
 * - `x,y,z`: first three components of ImuSample.values; `w`: the rest joined by `;` (quaternion w of
 *   rotation vectors, or components 4+ of an exotic sensor). Missing components are empty cells.
 * - A `# time_base=<id> t_mono_ns_initial=<ns>` comment precedes the first row of each time base
 *   (a reboot starts a new monotonic clock: dt_us restarts at 0).
 * Values are rounded to 6 decimals, deterministically. sensor_name and accuracy of ImuSample are
 * not in the CSV (they stay in the on-device journal); re-imported samples get sensorName="",
 * accuracy=-1 and a UTC derived from the session time bases.
 */
object ImuCsv {
    const val PATH = "raw/imu.csv.gz"
    const val FORMAT_ID = "lumisite-imu-csv/1"
    const val COLUMNS = "dt_us,sensor_dt_us,type,x,y,z,w"

    /** Android Sensor.TYPE_* → CSV type (same names as the D9 captation frames; "imu.other.<n>" keeps the raw type). */
    fun typeOf(sensorType: Int): String = when (sensorType) {
        1 -> "imu.accel"; 2 -> "imu.mag"; 4 -> "imu.gyro"; 11, 15 -> "imu.rotation"
        else -> "imu.other.$sensorType"
    }

    /** Best-effort reverse of [typeOf]: "imu.rotation" reads back as TYPE_ROTATION_VECTOR (11). */
    fun sensorTypeOf(type: String): Int = when {
        type.startsWith("imu.other.") -> type.removePrefix("imu.other.").toIntOrNull() ?: -1
        type == "imu.other" -> -1
        else -> when (type) {
            "imu.accel" -> 1; "imu.mag" -> 2; "imu.gyro" -> 4; "imu.rotation" -> 11
            else -> -1
        }
    }

    /** Stable rounding: 6 decimals, plain notation, "0" for zero (also −0). */
    fun fmt(v: Float): String {
        val r = round(v.toDouble() * 1e6) / 1e6
        if (r == 0.0) return "0"
        return String.format(java.util.Locale.ROOT, "%.6f", r).trimEnd('0').trimEnd('.')
    }

    /** One data row. [t0Ns] = monotonicNs initial of the sample's time base (first sample of that base). */
    fun row(s: ImuSample, t0Ns: Long): String {
        val dt = Math.floorDiv(s.t.monotonicNs - t0Ns, 1000L)
        val sdt = s.t.sensorNs?.let { Math.floorDiv(it - t0Ns, 1000L) }?.toString() ?: ""
        val x = s.values
        fun cell(i: Int) = x.getOrNull(i)?.let(::fmt) ?: ""
        val w = if (x.size > 3) x.subList(3, x.size).joinToString(";") { fmt(it) } else ""
        return "$dt,$sdt,${typeOf(s.sensorType)},${cell(0)},${cell(1)},${cell(2)},$w"
    }

    /** Full CSV text (tests and small fixtures); same layout as [Writer]. */
    fun render(samples: List<ImuSample>): String {
        val sb = StringBuilder()
        sb.append("# ").append(FORMAT_ID).append('\n').append(COLUMNS).append('\n')
        var base: String? = null; var t0 = 0L
        for (s in samples) {
            if (s.t.timeBaseId != base) {
                base = s.t.timeBaseId; t0 = s.t.monotonicNs
                sb.append("# time_base=").append(base).append(" t_mono_ns_initial=").append(t0).append('\n')
            }
            sb.append(row(s, t0)).append('\n')
        }
        return sb.toString()
    }

    /** A data line (not a comment, not the column header). */
    fun isDataLine(line: String) = line.isNotBlank() && !line.startsWith("#") && line != COLUMNS

    /** Rows of a CSV payload, without loading it whole when [countOnly] is used by callers that stream. */
    fun countRows(text: String): Int = text.lineSequence().count(::isDataLine)

    /** Decompresses a `imu.csv.gz` payload. */
    fun ungzip(input: InputStream): String = GZIPInputStream(input, 64 * 1024).use { it.readBytes().decodeToString() }

    private val TIME_BASE = Regex("""# time_base=(\S+) t_mono_ns_initial=(\d+)""")

    /**
     * Reads a CSV payload back into samples (best effort, for SessionImporter.load):
     * monotonicNs = t_mono_ns_initial + dt_us·1000 (µs precision), sensorNs likewise,
     * utcEpochMs derived from the session [timeBases] (computed value, like monotonicToUtcMs),
     * sensorName = "", accuracy = −1 (absent from the CSV).
     */
    fun parse(text: String, timeBases: Map<String, TimeBase>): List<ImuSample> {
        val out = ArrayList<ImuSample>()
        var base = ""; var t0 = 0L
        for (line in text.lineSequence()) {
            if (line.startsWith("#")) {
                TIME_BASE.find(line)?.let { base = it.groupValues[1]; t0 = it.groupValues[2].toLong() }
                continue
            }
            if (!isDataLine(line)) continue
            val c = line.split(',')
            if (c.size < 7) continue
            val mono = t0 + (c[0].toLongOrNull() ?: continue) * 1000L
            val sdt = c[1].toLongOrNull()?.let { t0 + it * 1000L }
            val values = (c[3].let { listOf(it) } + c[4] + c[5] + c[6].split(';')).mapNotNull { it.toFloatOrNull() }
            val tb = timeBases[base]
            out += ImuSample(
                app.lumisite.core.time.Stamp(tb?.monotonicToUtcMs(mono)?.toLong() ?: 0L, mono, base, sdt),
                sensorTypeOf(c[2]), "", values, -1,
            )
        }
        return out
    }

    /** Streaming writer: gzip around [sink]; [finish] writes the trailer without closing [sink] (the zip entry is hashed by the caller). */
    class Writer(sink: OutputStream) {
        private val gz = GZIPOutputStream(sink, 64 * 1024)
        private val w = BufferedWriter(OutputStreamWriter(gz, Charsets.UTF_8), 64 * 1024)
        private var base: String? = null
        private var t0 = 0L

        init {
            w.write("# $FORMAT_ID\n")
            w.write(COLUMNS); w.write("\n")
        }

        @Synchronized fun write(s: ImuSample) {
            if (s.t.timeBaseId != base) {
                base = s.t.timeBaseId; t0 = s.t.monotonicNs
                w.write("# time_base=$base t_mono_ns_initial=$t0\n")
            }
            w.write(row(s, t0)); w.write("\n")
        }

        /** Flush + gzip trailer. Does NOT close [sink]. */
        @Synchronized fun finish() { w.flush(); gz.finish() }
    }
}
