package app.lumisite.core.journal

import app.lumisite.core.model.GpsFix
import app.lumisite.core.model.GpsStatusSample
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SessionHeader
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

val LumiJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}

/** Stream file names inside a session directory. */
object Streams {
    const val HEADER = "session_header.json"
    const val EVENTS = "events.ndjson"
    const val POSES = "raw_poses.ndjson"
    const val IMU = "raw_imu.ndjson"
    const val GPS = "raw_gps.ndjson"
    const val GPS_STATUS = "raw_gps_status.ndjson"
}

/**
 * Append-only NDJSON journal. Each line is a complete JSON object; a crash can at worst lose the
 * unflushed tail and leave one truncated final line, which [read] skips and reports.
 */
class NdjsonAppender(file: File, private val flushEvery: Int = 50) : AutoCloseable {
    private val out: FileOutputStream = FileOutputStream(file, true)
    private val writer = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8))
    private var pending = 0

    @Synchronized
    fun appendLine(json: String) {
        require(!json.contains('\n'))
        writer.write(json); writer.write("\n")
        if (++pending >= flushEvery) flush()
    }

    @Synchronized
    fun flush(sync: Boolean = false) {
        writer.flush(); pending = 0
        if (sync) out.fd.sync()
    }

    @Synchronized
    override fun close() { writer.flush(); out.fd.sync(); writer.close() }
}

data class ReadResult<T>(val items: List<T>, val skippedLines: List<Int>)

object Ndjson {
    fun <T> read(file: File, serializer: KSerializer<T>): ReadResult<T> {
        if (!file.exists()) return ReadResult(emptyList(), emptyList())
        val items = ArrayList<T>(); val skipped = ArrayList<Int>()
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEachIndexed { i, line ->
                if (line.isBlank()) return@forEachIndexed
                try { items += LumiJson.decodeFromString(serializer, line) } catch (e: Exception) { skipped += i + 1 }
            }
        }
        return ReadResult(items, skipped)
    }
}

/** Writes all streams of one session. Events are flushed+fsynced immediately; samples in batches. */
class SessionJournal(val dir: File, header: SessionHeader? = null) : AutoCloseable {
    private val events: NdjsonAppender
    private val poses: NdjsonAppender
    private val imu: NdjsonAppender
    private val gps: NdjsonAppender
    private val gpsStatus: NdjsonAppender

    init {
        dir.mkdirs()
        val hf = File(dir, Streams.HEADER)
        if (header != null) {
            check(!hf.exists()) { "Session header already exists, refusing to overwrite: $hf" }
            hf.writeText(LumiJson.encodeToString(SessionHeader.serializer(), header))
        }
        events = NdjsonAppender(File(dir, Streams.EVENTS), flushEvery = 1)
        poses = NdjsonAppender(File(dir, Streams.POSES), flushEvery = 20)
        imu = NdjsonAppender(File(dir, Streams.IMU), flushEvery = 500)
        gps = NdjsonAppender(File(dir, Streams.GPS), flushEvery = 1)
        gpsStatus = NdjsonAppender(File(dir, Streams.GPS_STATUS), flushEvery = 1)
    }

    fun event(e: SessionEvent) { events.appendLine(LumiJson.encodeToString(SessionEvent.serializer(), e)); events.flush(sync = true) }
    fun pose(p: PoseSample) = poses.appendLine(LumiJson.encodeToString(PoseSample.serializer(), p))
    fun imu(s: ImuSample) = imu.appendLine(LumiJson.encodeToString(ImuSample.serializer(), s))
    fun gps(f: GpsFix) = gps.appendLine(LumiJson.encodeToString(GpsFix.serializer(), f))
    fun gpsStatus(s: GpsStatusSample) = gpsStatus.appendLine(LumiJson.encodeToString(GpsStatusSample.serializer(), s))

    fun flushAll() { listOf(events, poses, imu, gps, gpsStatus).forEach { it.flush(sync = true) } }
    override fun close() { listOf(events, poses, imu, gps, gpsStatus).forEach { it.close() } }
}

/** Everything read back from a session directory. */
data class SessionData(
    val header: SessionHeader,
    val events: List<SessionEvent>,
    val poses: List<PoseSample>,
    val imu: List<ImuSample>,
    val gps: List<GpsFix>,
    val gpsStatus: List<GpsStatusSample>,
    val skippedLines: Map<String, List<Int>>,
)

object SessionReader {
    fun read(dir: File, includeImu: Boolean = true): SessionData {
        val header = LumiJson.decodeFromString(SessionHeader.serializer(), File(dir, Streams.HEADER).readText())
        val ev = Ndjson.read(File(dir, Streams.EVENTS), SessionEvent.serializer())
        val po = Ndjson.read(File(dir, Streams.POSES), PoseSample.serializer())
        val im = if (includeImu) Ndjson.read(File(dir, Streams.IMU), ImuSample.serializer()) else ReadResult(emptyList(), emptyList())
        val gp = Ndjson.read(File(dir, Streams.GPS), GpsFix.serializer())
        val gs = Ndjson.read(File(dir, Streams.GPS_STATUS), GpsStatusSample.serializer())
        val skipped = mapOf(
            Streams.EVENTS to ev.skippedLines, Streams.POSES to po.skippedLines, Streams.IMU to im.skippedLines,
            Streams.GPS to gp.skippedLines, Streams.GPS_STATUS to gs.skippedLines,
        ).filterValues { it.isNotEmpty() }
        return SessionData(header, ev.items, po.items, im.items, gp.items, gs.items, skipped)
    }
}
