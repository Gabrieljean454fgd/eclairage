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
import java.io.RandomAccessFile

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
    init { Ndjson.sealPartialLine(file) }
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
    /**
     * If a crash left a truncated last line, terminates it so that new records start on their own line.
     * The truncated bytes are kept (and reported as unreadable on read), never rewritten.
     */
    fun sealPartialLine(file: File) {
        if (!file.exists() || file.length() == 0L) return
        val last = RandomAccessFile(file, "r").use { it.seek(it.length() - 1); it.read() }
        if (last != '\n'.code) FileOutputStream(file, true).use { it.write('\n'.code); it.fd.sync() }
    }

    /**
     * Copies the complete lines of [src] present at call time into [dst]. A line still being written
     * concurrently (no trailing newline yet) is left out; it stays in [src] and will be in the next copy.
     */
    fun snapshotCompleteLines(src: File, dst: File) {
        if (!src.exists()) { dst.writeText(""); return }
        val len = src.length()
        src.inputStream().use { input ->
            FileOutputStream(dst).use { out ->
                val buf = ByteArray(64 * 1024); var left = len
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n); left -= n
                }
            }
        }
        RandomAccessFile(dst, "rw").use { raf ->
            var end = raf.length()
            while (end > 0) { raf.seek(end - 1); if (raf.read() == '\n'.code) break; end-- }
            raf.setLength(end)
        }
    }

    fun <T> read(file: File, serializer: KSerializer<T>): ReadResult<T> = readAll(listOf(file), serializer)

    /** Reads several files as one stream; line numbers of unreadable lines are counted over the concatenation. */
    fun <T> readAll(files: List<File>, serializer: KSerializer<T>): ReadResult<T> {
        val items = ArrayList<T>(); val skipped = ArrayList<Int>()
        forEachLine(files) { n, line ->
            try { items += LumiJson.decodeFromString(serializer, line) } catch (e: Exception) { skipped += n }
        }
        return ReadResult(items, skipped)
    }

    /** Streams the non-blank lines of [files] (1-based line number over the concatenation), never loading a file whole. */
    fun forEachLine(files: List<File>, block: (Int, String) -> Unit) {
        var n = 0
        for (f in files) {
            if (!f.exists()) continue
            f.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { line -> n++; if (line.isNotBlank()) block(n, line) } }
        }
    }
}

/**
 * On-device segments (stage 1.2, reserve R2). Each stream is written in small segments under `seg/`:
 * `<stream>.<seq>.part` while open, renamed atomically to `<stream>.<seq>.ndjson` once closed (fsync first).
 * A finished segment never changes again, so it can be copied and verified by hash while the session continues.
 * A `.part` left by an abrupt stop is repaired on the next opening: a truncated last line is terminated (kept, then
 * reported as unreadable), the file is renamed `.ndjson`, and the caller writes a `stream_state` REPAIRED event.
 * Sessions written before stage 1 (one `<stream>.ndjson` file at the root) are still read: that file comes first.
 */
object Segments {
    const val DIR = "seg"
    private val NAME = Regex("""^(.+)\.(\d{6})\.(part|ndjson)$""")

    fun base(stream: String) = stream.removeSuffix(".ndjson")
    fun file(dir: File, stream: String, seq: Int, part: Boolean) =
        File(File(dir, DIR), "${base(stream)}.%06d.${if (part) "part" else "ndjson"}".format(seq))

    data class SegFile(val file: File, val stream: String, val seq: Int, val open: Boolean)

    fun list(dir: File): List<SegFile> = File(dir, DIR).listFiles().orEmpty().mapNotNull { f ->
        NAME.matchEntire(f.name)?.let { m -> SegFile(f, m.groupValues[1] + ".ndjson", m.groupValues[2].toInt(), m.groupValues[3] == "part") }
    }.sortedWith(compareBy({ it.stream }, { it.seq }))

    /** Every file of [stream], in write order: legacy root file first, then segments (finished or still open). */
    fun files(dir: File, stream: String): List<File> =
        listOfNotNull(File(dir, stream).takeIf { it.exists() }) + list(dir).filter { it.stream == stream }.map { it.file }

    fun nextSeq(dir: File, stream: String): Int = (list(dir).filter { it.stream == stream }.maxOfOrNull { it.seq } ?: 0) + 1

    data class Repaired(val stream: String, val file: File, val bytes: Long, val truncatedTail: Boolean)

    /** Closes the segments left open by an abrupt stop (see class doc). Legacy root files get their partial line sealed. */
    fun repair(dir: File): List<Repaired> {
        listOf(Streams.EVENTS, Streams.POSES, Streams.IMU, Streams.GPS, Streams.GPS_STATUS).forEach { Ndjson.sealPartialLine(File(dir, it)) }
        list(dir).filter { !it.open }.forEach { Ndjson.sealPartialLine(it.file) }
        return list(dir).filter { it.open }.map { sf ->
            val truncated = sf.file.length() > 0 && RandomAccessFile(sf.file, "r").use { it.seek(it.length() - 1); it.read() != '\n'.code }
            Ndjson.sealPartialLine(sf.file)
            val done = file(dir, sf.stream, sf.seq, part = false)
            check(sf.file.renameTo(done)) { "Cannot finalize segment ${sf.file}" }
            Repaired(sf.stream, done, done.length(), truncated)
        }
    }

    /** Concatenation of the complete lines of all files of [stream] present at call time (export snapshot). */
    fun snapshot(dir: File, stream: String, dst: File) {
        val tmp = File(dst.parentFile, dst.name + ".one")
        FileOutputStream(dst).use { out ->
            for (f in files(dir, stream)) {
                Ndjson.snapshotCompleteLines(f, tmp)
                tmp.inputStream().use { it.copyTo(out, 64 * 1024) }
            }
        }
        tmp.delete()
    }

    fun totalBytes(dir: File, stream: String): Long = files(dir, stream).sumOf { it.length() }
}

/**
 * Append-only writer of one stream in segments. Lines are flushed every [flushEvery] lines and fsynced at least every
 * [syncEveryMs] (and at each segment end); a segment is closed after [maxBytes] or [maxAgeMs]. At worst an abrupt stop
 * loses the lines written since the last fsync of the open segment (≤ [syncEveryMs]), never a finished segment.
 */
class SegmentedAppender(
    private val dir: File,
    val stream: String,
    private val flushEvery: Int,
    private val maxBytes: Long = 1L shl 20,
    private val maxAgeMs: Long = 60_000,
    private val syncEveryMs: Long = 2_000,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val onSegmentClosed: (File) -> Unit = {},
) : AutoCloseable {
    private var seq = Segments.nextSeq(dir, stream) - 1
    private var out: FileOutputStream? = null
    private var writer: BufferedWriter? = null
    private var bytes = 0L
    private var openedMs = 0L
    private var lastSyncMs = 0L
    private var pending = 0

    private fun open() {
        seq++
        val f = Segments.file(dir, stream, seq, part = true).apply { parentFile.mkdirs() }
        out = FileOutputStream(f, true); writer = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8))
        bytes = 0; openedMs = nowMs(); lastSyncMs = openedMs
    }

    @Synchronized
    fun appendLine(json: String) {
        require(!json.contains('\n'))
        if (writer == null) open()
        writer!!.write(json); writer!!.write("\n")
        bytes += json.length + 1
        val now = nowMs()
        if (++pending >= flushEvery) { writer!!.flush(); pending = 0 }
        if (now - lastSyncMs >= syncEveryMs) flush(sync = true)
        if (bytes >= maxBytes || now - openedMs >= maxAgeMs) rotate()
    }

    @Synchronized
    fun flush(sync: Boolean = false) {
        val w = writer ?: return
        w.flush(); pending = 0
        if (sync) { out!!.fd.sync(); lastSyncMs = nowMs() }
    }

    /** Closes the open segment (fsync, then atomic rename to .ndjson) and reports it. Next line opens a new one. */
    @Synchronized
    fun rotate() {
        val w = writer ?: return
        w.flush(); out!!.fd.sync(); w.close()
        writer = null; out = null
        val part = Segments.file(dir, stream, seq, part = true)
        val done = Segments.file(dir, stream, seq, part = false)
        check(part.renameTo(done)) { "Cannot finalize segment $part" }
        runCatching { onSegmentClosed(done) }
    }

    @Synchronized
    override fun close() = rotate()
}

/**
 * Writes all streams of one session in segments (see [Segments]). Events are flushed+fsynced immediately; samples
 * in batches with an fsync at least every 2 s. [repaired] lists the segments left open by a previous abrupt stop.
 */
class SessionJournal(
    val dir: File,
    header: SessionHeader? = null,
    nowMs: () -> Long = System::currentTimeMillis,
    onSegmentClosed: (File) -> Unit = {},
) : AutoCloseable {
    private val events: SegmentedAppender
    private val poses: SegmentedAppender
    private val imu: SegmentedAppender
    private val gps: SegmentedAppender
    private val gpsStatus: SegmentedAppender
    val repaired: List<Segments.Repaired>

    init {
        dir.mkdirs()
        val hf = File(dir, Streams.HEADER)
        if (header != null) {
            check(!hf.exists()) { "Session header already exists, refusing to overwrite: $hf" }
            val tmp = File(dir, Streams.HEADER + ".tmp")
            FileOutputStream(tmp).use { it.write(LumiJson.encodeToString(SessionHeader.serializer(), header).toByteArray()); it.fd.sync() }
            check(tmp.renameTo(hf))
        }
        repaired = Segments.repair(dir)
        repaired.forEach { runCatching { onSegmentClosed(it.file) } }
        fun app(stream: String, flushEvery: Int) = SegmentedAppender(dir, stream, flushEvery, nowMs = nowMs, onSegmentClosed = onSegmentClosed)
        events = app(Streams.EVENTS, 1)
        poses = app(Streams.POSES, 20)
        imu = app(Streams.IMU, 500)
        gps = app(Streams.GPS, 1)
        gpsStatus = app(Streams.GPS_STATUS, 1)
    }

    fun event(e: SessionEvent) { events.appendLine(LumiJson.encodeToString(SessionEvent.serializer(), e)); events.flush(sync = true) }
    fun pose(p: PoseSample) = poses.appendLine(LumiJson.encodeToString(PoseSample.serializer(), p))
    fun imu(s: ImuSample) = imu.appendLine(LumiJson.encodeToString(ImuSample.serializer(), s))
    fun gps(f: GpsFix) = gps.appendLine(LumiJson.encodeToString(GpsFix.serializer(), f))
    fun gpsStatus(s: GpsStatusSample) = gpsStatus.appendLine(LumiJson.encodeToString(GpsStatusSample.serializer(), s))

    private val all get() = listOf(events, poses, imu, gps, gpsStatus)
    fun flushAll() { all.forEach { it.flush(sync = true) } }
    /** Closes the open segments (e.g. on pause) so that everything written so far can be copied out. */
    fun rotateAll() { all.forEach { it.rotate() } }
    override fun close() { all.forEach { runCatching { it.close() } } }
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
        fun <T> r(stream: String, ser: KSerializer<T>) = Ndjson.readAll(Segments.files(dir, stream), ser)
        val ev = r(Streams.EVENTS, SessionEvent.serializer())
        val po = r(Streams.POSES, PoseSample.serializer())
        val im = if (includeImu) r(Streams.IMU, ImuSample.serializer()) else ReadResult(emptyList(), emptyList())
        val gp = r(Streams.GPS, GpsFix.serializer())
        val gs = r(Streams.GPS_STATUS, GpsStatusSample.serializer())
        val skipped = mapOf(
            Streams.EVENTS to ev.skippedLines, Streams.POSES to po.skippedLines, Streams.IMU to im.skippedLines,
            Streams.GPS to gp.skippedLines, Streams.GPS_STATUS to gs.skippedLines,
        ).filterValues { it.isNotEmpty() }
        return SessionData(header, ev.items, po.items, im.items, gp.items, gs.items, skipped)
    }
}
