package app.lumisite.core.export

import app.lumisite.core.model.Attachment
import kotlinx.serialization.json.JsonNull
import app.lumisite.core.compute.CorrectedPoint
import app.lumisite.core.compute.SurveyPositions
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.Segments
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SessionHeader
import app.lumisite.core.model.Site
import app.lumisite.core.model.SurveyEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/** One input file frozen at the snapshot (closed segments and attachments are immutable once written). */
@Serializable
data class ExportInput(val path: String, val bytes: Long)

/** One zip entry already written, synced and recorded: a restart never writes it again. */
@Serializable
data class ExportEntryRecord(
    val name: String, val offset: Long, val end: Long, val crc: Long, val compressedBytes: Long, val bytes: Long,
    val sha256: String, val category: String, val recordType: String, val records: Int,
    val unreadableLines: List<Int> = emptyList(),
)

/** exports/<export-id>/manifest.json: persistent state of a resumable export (D14). */
@Serializable
data class ResumableManifest(
    val exportId: String,
    val sessionId: String,
    val createdUtcMs: Long,
    val state: String,
    val zipName: String,
    val inputs: List<ExportInput>,
    val entries: List<ExportEntryRecord> = emptyList(),
    val zipBytes: Long? = null,
    val zipSha256: String? = null,
    val copiedTo: String? = null,
    val notes: List<String> = emptyList(),
)

/**
 * D14: resumable streaming export. Steps Préparation → Instantané → Assemblage → Vérification → Copie → Terminé.
 * - The snapshot is the list of closed segments and attachments (immutable files): nothing is copied before zipping.
 * - The zip is written entry by entry straight from the segments ([ZipAppender]); after each entry the file is synced
 *   and the entry recorded in manifest.json, so an interrupted export resumes after the last recorded entry.
 * - Verification re-reads the finished zip (sizes + SHA-256 of every entry) before the copy to its destination.
 * - Only the temporary files of this export (*.part, *.tmp in exports/<export-id>/) are deleted; session data never.
 * Memory: only the events (small) are held; raw streams and media are streamed by blocks.
 */
object ResumableExport {
    enum class Step(val label: String) {
        PREPARATION("Préparation"), INSTANTANE("Instantané"), ASSEMBLAGE("Assemblage"), VERIFICATION("Vérification"), COPIE("Copie"), TERMINE("Terminé")
    }

    data class Progress(val step: Step, val detail: String, val doneBytes: Long, val totalBytes: Long) {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data class Result(val dir: File, val zip: File, val manifest: ResumableManifest, val sessionManifest: SessionManifest,
                      val writtenNow: List<String>, val reused: List<String>, val problems: List<String>)

    const val MANIFEST = "manifest.json"

    private val rawStreams = listOf(
        Triple(Streams.POSES, "raw/poses.ndjson", "PoseSample"),
        Triple(Streams.IMU, "raw/imu.ndjson", "ImuSample"),
        Triple(Streams.GPS, "raw/gps.ndjson", "GpsFix"),
        Triple(Streams.GPS_STATUS, "raw/gps_status.ndjson", "GpsStatusSample"),
        Triple(Streams.EVENTS, "raw/events.ndjson", "SessionEvent"),
        Triple(Streams.CAPTATION, "raw/captation.jsonl", "Captation"),
    )

    /** Closed files of a stream in write order: legacy root file, then finished segments (.ndjson or .ndjson.gz). */
    fun closedFiles(sessionDir: File, stream: String): List<File> =
        listOfNotNull(File(sessionDir, stream).takeIf { it.isFile }) + Segments.list(sessionDir).filter { it.stream == stream && !it.open }.map { it.file }

    /** Paquet v6: closed files then the open segments (left by an abrupt stop, or still being written), in write order. */
    fun snapshotFiles(sessionDir: File, stream: String): List<File> =
        listOfNotNull(File(sessionDir, stream).takeIf { it.isFile }) + Segments.list(sessionDir).filter { it.stream == stream }.map { it.file }

    /** An open segment listed at the snapshot may have been closed since (rotation: .part → .ndjson, then .ndjson.gz). */
    fun current(f: File): File {
        if (f.exists() || !f.name.endsWith(".part")) return f
        val base = f.name.removeSuffix(".part")
        return listOf("$base.ndjson", "$base.ndjson.gz").map { File(f.parentFile, it) }.firstOrNull { it.isFile } ?: f
    }

    /** Length of the complete lines of [f] within its first [len] bytes (an open segment may end with a line being written). */
    fun completeLength(f: File, len: Long): Long {
        if (!f.name.endsWith(".part")) return len
        // Closed between listing and here: the closed segment only holds complete lines, read it all.
        if (current(f) != f) return Long.MAX_VALUE
        java.io.RandomAccessFile(f, "r").use { raf -> var end = minOf(len, raf.length()); while (end > 0) { raf.seek(end - 1); if (raf.read() == '\n'.code) break; end-- }; return end }
    }

    private fun limited(i: InputStream, max: Long): InputStream = object : java.io.FilterInputStream(i) {
        var left = max
        override fun read(): Int = if (left <= 0) -1 else super.read().also { if (it >= 0) left-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int { if (left <= 0) return -1; val n = super.read(b, off, minOf(len.toLong(), left).toInt()); if (n > 0) left -= n; return n }
    }

    fun openInput(f: File): InputStream = if (f.name.endsWith(".gz")) GZIPInputStream(f.inputStream(), 64 * 1024) else f.inputStream().buffered(64 * 1024)

    fun run(
        site: Site, sessionDir: File, exportsRoot: File, generator: String, nowUtcMs: Long,
        progress: (Progress) -> Unit = {}, copy: ((File) -> String?)? = null,
    ): Result {
        progress(Progress(Step.PREPARATION, "Préparation…", 0, 1))
        val header = LumiJson.decodeFromString(SessionHeader.serializer(), File(sessionDir, Streams.HEADER).readText())
        // Instantané: closed files, then the open segments up to their last complete line (paquet v6: an open segment left
        // by an abrupt stop is exported and named in the notes, never silently left out).
        val rawFiles = rawStreams.associate { (stream, _, _) -> stream to snapshotFiles(sessionDir, stream) }
        val snapLen = rawFiles.values.flatten().associateWith { completeLength(it, it.length()) }
        val openSegs = Segments.list(sessionDir).filter { it.open }.map { it.file.name }
        val events = rawFiles.getValue(Streams.EVENTS).flatMap { f -> lines(f, snapLen.getValue(f)).mapNotNull { runCatching { LumiJson.decodeFromString(SessionEvent.serializer(), it) }.getOrNull() } }
        val refs = events.filterIsInstance<SurveyEvent>().flatMap { it.attachments }.distinctBy { it.file }
        val media = refs.mapNotNull { a -> File(sessionDir, a.file).takeIf { it.isFile }?.let { a to it } }
        // Synchro recordings (sync_*.wav) are not attachments of a survey: exported as they are next to the photos.
        val audio = sessionDir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".wav") && media.none { m -> m.second == it } }.sortedBy { it.name }
        val inputs = (rawFiles.values.flatten().map { it to snapLen.getValue(it) } + (media.map { it.second } + audio).map { it to it.length() })
            .map { (f, n) -> ExportInput(f.relativeTo(sessionDir).invariantSeparatorsPath, n) }
        progress(Progress(Step.INSTANTANE, "Instantané : ${inputs.size} fichiers", 0, 1))
        val exportId = header.id + "-" + sha256(LumiJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(ExportInput.serializer()), inputs).toByteArray()).take(10)
        val dir = File(exportsRoot, exportId).apply { mkdirs() }
        val mf = File(dir, MANIFEST)
        val zipName = "${header.id}.zip"
        var m = if (mf.isFile) runCatching { LumiJson.decodeFromString(ResumableManifest.serializer(), mf.readText()) }.getOrNull() else null
        if (m == null || m.inputs != inputs) m = ResumableManifest(exportId, header.id, nowUtcMs, Step.ASSEMBLAGE.name, zipName, inputs,
            notes = if (openSegs.isEmpty()) emptyList() else listOf("Segments encore ouverts, exportés jusqu'à leur dernière ligne complète : $openSegs"))
        val zip = File(dir, zipName)
        val part = File(dir, "$zipName.part")
        val reused = m.entries.map { it.name }
        val written = ArrayList<String>()
        val inputBytes = inputs.sumOf { it.bytes }
        val total = inputBytes * 2 + 1
        var done = m.entries.sumOf { it.bytes }.coerceAtMost(inputBytes)
        fun save(n: ResumableManifest) { m = n; writeAtomically(mf, PrettyJson.encodeToString(ResumableManifest.serializer(), n)) }

        val ltp = LocalTangentPlane(site.origin)
        var sessionManifest: SessionManifest? = null
        if (m!!.state == Step.ASSEMBLAGE.name || !zip.isFile) {
            if (m!!.state != Step.ASSEMBLAGE.name) save(m!!.copy(state = Step.ASSEMBLAGE.name, entries = emptyList(), zipBytes = null, zipSha256 = null))
            val appender = ZipAppender(part, m!!.entries.lastOrNull()?.end ?: 0L, m!!.createdUtcMs)
            appender.use { z ->
                fun entry(name: String, category: String, type: String, body: (OutputStream) -> Pair<Int, List<Int>>) {
                    if (m!!.entries.any { it.name == name }) return
                    progress(Progress(Step.ASSEMBLAGE, name, done, total))
                    val rec = z.entry(name) { out -> body(out) }.let { (r, counts) -> r.copy(category = category, recordType = type, records = counts.first, unreadableLines = counts.second) }
                    save(m!!.copy(entries = m!!.entries + rec)); written += name
                }
                val nStreams = rawStreams.size
                rawStreams.forEachIndexed { i, (stream, target, type) ->
                    entry(target, "raw", type) { out ->
                        var ok = 0; val bad = ArrayList<Int>(); var n = 0
                        val ser = serializerOf(type)
                        for (f in rawFiles.getValue(stream)) {
                            var pending = 0L
                            limited(openInput(current(f)), if (f.name.endsWith(".part")) snapLen.getValue(f) else Long.MAX_VALUE).bufferedReader().useLines { seq -> seq.forEach { line ->
                                if (line.isEmpty()) return@forEach
                                n++
                                val b = (line + "\n").toByteArray(); out.write(b)
                                if (runCatching { LumiJson.decodeFromString(ser, line) }.isSuccess) ok++ else bad += n
                                pending += b.size
                                if (pending > (1 shl 20)) { done += pending; pending = 0; progress(Progress(Step.ASSEMBLAGE, "Capteurs ${i + 1}/$nStreams", done, total)) }
                            } }
                            done += pending
                        }
                        ok to bad
                    }
                }
                entry("derived/trajectory_site.ndjson", "derived", "CorrectedPoint") { out ->
                    var count = 0
                    val poses = rawFiles.getValue(Streams.POSES).asSequence().flatMap { lines(it, snapLen.getValue(it)) }
                        .mapNotNull { runCatching { LumiJson.decodeFromString(PoseSample.serializer(), it) }.getOrNull() }
                    TrajectoryCorrector.correctEach(poses, events, ltp) { c -> out.write((LumiJson.encodeToString(CorrectedPoint.serializer(), c) + "\n").toByteArray()); count++ }
                    count to emptyList()
                }
                entry("derived/survey_points.ndjson", "derived", "DerivedSurveyPoint") { out ->
                    val pts = derivedPoints(events)
                    pts.forEach { out.write((LumiJson.encodeToString(DerivedSurveyPoint.serializer(), it) + "\n").toByteArray()) }
                    pts.size to emptyList()
                }
                val fc = Releves.featureCollection(events, ltp, header.device.id, header.id)
                entry(RELEVES, "derived", "FeatureCollection") { out ->
                    out.write(PrettyJson.encodeToString(JsonObject.serializer(), fc).toByteArray())
                    (fc["features"] as JsonArray).size to emptyList()
                }
                media.forEachIndexed { i, (a, src) ->
                    entry("media/" + a.file.substringAfterLast('/'), "media", a.mediaType) { out ->
                        progress(Progress(Step.ASSEMBLAGE, "Photos ${i + 1}/${media.size}", done, total))
                        src.inputStream().use { copyBlocks(it, out) { b -> done += b } }
                        1 to emptyList()
                    }
                }
                // Paquet v6: business tags of each photo in a JSON file next to it (EXIF keeps GPS, orientation, date).
                media.filter { it.first.mediaType.startsWith("image/") }.forEach { (a, _) ->
                    val name = a.file.substringAfterLast('/')
                    entry("media/$name.json", "derived", "PhotoTags") { out -> out.write(PrettyJson.encodeToString(JsonObject.serializer(), Releves.photoTags(fc, name, a, header, events.filterIsInstance<SurveyEvent>().lastOrNull { e -> e.attachments.any { it.file == a.file } })).toByteArray()); 1 to emptyList() }
                }
                audio.forEach { f ->
                    entry("media/" + f.name, "media", "audio/x-wav") { out -> f.inputStream().use { copyBlocks(it, out) { b -> done += b } }; 1 to emptyList() }
                }
                entry(LISEZMOI, "doc", "text/plain") { out -> out.write(Releves.lisezmoi(header, site).toByteArray()); 1 to emptyList() }
                val sm = sessionManifestOf(m!!.entries, site, header, events, generator, nowUtcMs, m!!.notes)
                sessionManifest = sm
                entry("session.json", "manifest", "SessionManifest") { out -> out.write(PrettyJson.encodeToString(SessionManifest.serializer(), sm).toByteArray()); 1 to emptyList() }
                z.finish(m!!.entries)
            }
            check(part.renameTo(zip)) { "Impossible de finaliser ${zip.name}" }
            save(m!!.copy(state = Step.VERIFICATION.name))
        }

        // Vérification: every entry re-read from the finished zip (size + SHA-256), then the whole zip hashed.
        val problems = ArrayList<String>()
        if (m!!.state == Step.VERIFICATION.name) {
            var vDone = inputBytes
            ZipFile(zip).use { zf ->
                for (r in m!!.entries) {
                    val e = zf.getEntry(r.name) ?: run { problems += "${r.name} absent du zip"; null } ?: continue
                    var n = 0L
                    val sha = zf.getInputStream(e).use { sha256(it) { b -> n += b; vDone += b; if (vDone % (4 shl 20) < b) progress(Progress(Step.VERIFICATION, r.name, vDone.coerceAtMost(total), total)) } }
                    if (n != r.bytes) problems += "${r.name} : $n octets au lieu de ${r.bytes}"
                    if (sha != r.sha256) problems += "${r.name} : empreinte SHA-256 différente"
                }
            }
            if (problems.isNotEmpty()) {
                // A bad archive is never presented as an export: it is removed with its records, the next run rebuilds it.
                zip.delete(); save(m!!.copy(state = Step.ASSEMBLAGE.name, entries = emptyList()))
                throw IllegalStateException("Vérification de l'export : ${problems.joinToString()}")
            }
            save(m!!.copy(state = Step.COPIE.name, zipBytes = zip.length(), zipSha256 = sha256(zip)))
        }
        if (m!!.state == Step.COPIE.name) {
            progress(Progress(Step.COPIE, "Copie", total - 1, total))
            val dest = copy?.invoke(zip)
            save(m!!.copy(state = Step.TERMINE.name, copiedTo = dest ?: m!!.copiedTo))
        }
        dir.listFiles().orEmpty().filter { it.name.endsWith(".part") || it.name.endsWith(".tmp") }.forEach { it.delete() }
        progress(Progress(Step.TERMINE, "Terminé", total, total))
        val sm = sessionManifest ?: ZipFile(zip).use { zf -> zf.getInputStream(zf.getEntry("session.json")).use { LumiJson.decodeFromString(SessionManifest.serializer(), it.readBytes().decodeToString()) } }
        return Result(dir, zip, m!!, sm, written, reused, problems)
    }

    const val RELEVES = "releves.geojson"
    const val LISEZMOI = "LISEZMOI.txt"

    private fun sessionManifestOf(entries: List<ExportEntryRecord>, site: Site, header: SessionHeader, events: List<SessionEvent>, generator: String, now: Long, notes: List<String>): SessionManifest {
        val traj = entries.firstOrNull { it.name == "derived/trajectory_site.ndjson" }?.records ?: 0
        return SessionManifest(
            exportedUtcMs = now, generator = generator, site = site, session = header, events = events,
            files = entries.map { FileEntry(it.name, it.category, it.recordType, it.records, it.sha256, it.bytes) },
            derivedSummary = DerivedSummary(traj, 0.0, 0, emptyMap(), listOf(
                "Trajectory is an estimate from the platform tracker + user calibration/relocalizations; it is not ground truth.",
                "Each pose and object uses the transform of its own segment (D13); a resume starts a new AR frame.",
            ) + notes),
            integrity = entries.filter { it.unreadableLines.isNotEmpty() }.associate { it.name to it.unreadableLines },
        )
    }

    fun derivedPoints(events: List<SessionEvent>): List<DerivedSurveyPoint> {
        val zones = app.lumisite.core.survey.Zones.current(events)
        return SurveyPositions.resolveAll(events).map { r ->
            val s = r.event
            DerivedSurveyPoint(s.id, s.kind, s.t.monotonicNs, s.t.utcEpochMs, r.site, s.positionSource, r.transformEventId, s.tracking, s.dxf?.layer, s.dxf?.blockName,
                zoneId = r.site?.let { app.lumisite.core.survey.Zones.zoneOf(it, zones)?.id })
        }
    }

    private fun serializerOf(type: String): KSerializer<*> = when (type) {
        "PoseSample" -> PoseSample.serializer(); "ImuSample" -> app.lumisite.core.model.ImuSample.serializer()
        "GpsFix" -> app.lumisite.core.model.GpsFix.serializer(); "GpsStatusSample" -> app.lumisite.core.model.GpsStatusSample.serializer()
        "SessionEvent" -> SessionEvent.serializer(); else -> JsonObject.serializer()
    }

    private fun lines(f: File, max: Long = Long.MAX_VALUE): Sequence<String> = sequence {
        limited(openInput(current(f)), if (f.name.endsWith(".part")) max else Long.MAX_VALUE).bufferedReader().use { r -> while (true) { val l = r.readLine() ?: break; if (l.isNotBlank()) yield(l) } }
    }

    private fun copyBlocks(input: InputStream, out: OutputStream, onBytes: (Long) -> Unit) {
        val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); onBytes(n.toLong()) }
    }

    fun writeAtomically(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { it.write(text.toByteArray()); it.fd.sync() }
        check(tmp.renameTo(f)) { "Impossible d'écrire ${f.name}" }
    }
}

/**
 * Minimal resumable zip writer (deflate, data descriptors, no ZIP64: an entry or archive ≥ 4 GiB is refused).
 * Each entry is synced to disk before its record is returned; [resumeAt] truncates a partial entry left by a stop.
 */
class ZipAppender(private val file: File, resumeAt: Long, utcMs: Long) : AutoCloseable {
    private val dosTime: Int
    private val dosDate: Int
    private val out: FileOutputStream
    private var pos: Long

    init {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = utcMs }
        dosTime = (c.get(java.util.Calendar.HOUR_OF_DAY) shl 11) or (c.get(java.util.Calendar.MINUTE) shl 5) or (c.get(java.util.Calendar.SECOND) / 2)
        dosDate = ((c.get(java.util.Calendar.YEAR) - 1980).coerceAtLeast(0) shl 9) or ((c.get(java.util.Calendar.MONTH) + 1) shl 5) or c.get(java.util.Calendar.DAY_OF_MONTH)
        RandomAccessFile(file, "rw").use { it.setLength(resumeAt) }
        out = FileOutputStream(file, true)
        pos = resumeAt
    }

    private class Counting(val o: OutputStream) : OutputStream() {
        var n = 0L
        override fun write(b: Int) { o.write(b); n++ }
        override fun write(b: ByteArray, off: Int, len: Int) { o.write(b, off, len); n += len }
        override fun flush() = o.flush()
    }

    private class Hashing(val o: OutputStream) : OutputStream() {
        val crc = CRC32(); val md: MessageDigest = MessageDigest.getInstance("SHA-256"); var n = 0L
        override fun write(b: Int) { o.write(b); crc.update(b); md.update(b.toByte()); n++ }
        override fun write(b: ByteArray, off: Int, len: Int) { o.write(b, off, len); crc.update(b, off, len); md.update(b, off, len); n += len }
        override fun flush() = o.flush()
    }

    private fun le(v: Long, bytes: Int): ByteArray = ByteArray(bytes) { ((v shr (8 * it)) and 0xff).toByte() }

    fun <T> entry(name: String, body: (OutputStream) -> T): Pair<ExportEntryRecord, T> {
        val nb = name.toByteArray(Charsets.UTF_8)
        val offset = pos
        val buffered = java.io.BufferedOutputStream(out, 64 * 1024)
        buffered.write(le(0x04034b50, 4) + le(20, 2) + le(0x0808, 2) + le(8, 2) + le(dosTime.toLong(), 2) + le(dosDate.toLong(), 2) +
            le(0, 4) + le(0, 4) + le(0, 4) + le(nb.size.toLong(), 2) + le(0, 2) + nb)
        val counting = Counting(buffered)
        val deflater = Deflater(Deflater.BEST_SPEED, true)
        val dos = DeflaterOutputStream(counting, deflater, 64 * 1024)
        val hashing = Hashing(dos)
        val res: T
        try { res = body(hashing); dos.finish() } finally { deflater.end() }
        require(hashing.n < 0xFFFFFFFFL && counting.n < 0xFFFFFFFFL) { "Entrée $name ≥ 4 Gio : non prise en charge" }
        buffered.write(le(0x08074b50, 4) + le(hashing.crc.value, 4) + le(counting.n, 4) + le(hashing.n, 4))
        buffered.flush(); out.fd.sync()
        pos = offset + 30 + nb.size + counting.n + 16
        val sha = hashing.md.digest().joinToString("") { "%02x".format(it) }
        return ExportEntryRecord(name, offset, pos, hashing.crc.value, counting.n, hashing.n, sha, "", "", 0) to res
    }

    /** Central directory + end record from the recorded entries (also those written by an earlier run). */
    fun finish(entries: List<ExportEntryRecord>) {
        require(entries.size < 0xFFFF && pos < 0xFFFFFFFFL) { "Archive ≥ 4 Gio ou trop d'entrées : non prise en charge" }
        val cdStart = pos
        val b = java.io.ByteArrayOutputStream()
        for (r in entries) {
            val nb = r.name.toByteArray(Charsets.UTF_8)
            b.write(le(0x02014b50, 4) + le(20, 2) + le(20, 2) + le(0x0808, 2) + le(8, 2) + le(dosTime.toLong(), 2) + le(dosDate.toLong(), 2) +
                le(r.crc, 4) + le(r.compressedBytes, 4) + le(r.bytes, 4) + le(nb.size.toLong(), 2) + le(0, 2) + le(0, 2) + le(0, 2) + le(0, 2) + le(0, 4) +
                le(r.offset, 4) + nb)
        }
        val cd = b.toByteArray()
        out.write(cd)
        out.write(le(0x06054b50, 4) + le(0, 2) + le(0, 2) + le(entries.size.toLong(), 2) + le(entries.size.toLong(), 2) + le(cd.size.toLong(), 4) + le(cdStart, 4) + le(0, 2))
        out.flush(); out.fd.sync()
        pos += cd.size + 22
    }

    override fun close() = out.close()
}

/** D16: objects as one RFC 7946 FeatureCollection (WGS84 geometry; site E/N/height only in properties) + LISEZMOI.txt. */
object Releves {
    fun featureCollection(events: List<SessionEvent>, ltp: LocalTangentPlane, deviceId: String, sessionId: String): JsonObject {
        val latest = GeoJsonSeq.latest(GeoJsonFeatures.lines(events, ltp, deviceId, sessionId))
        val resolved = SurveyPositions.resolveAll(events).associateBy { SurveyPositions.rootId(events, it.event.id) }
        val features = latest.values.filter { (it["properties"] as JsonObject)["op"]?.jsonPrimitive?.contentOrNull != "delete" }.map { f ->
            val props = f["properties"] as JsonObject
            val r = resolved[f["id"]?.jsonPrimitive?.contentOrNull]
            val provenance = buildJsonObject {
                if (r != null) {
                    val pv = app.lumisite.core.contract.Provenance.of(r)
                    put("methode", pv.method.name); put("source_journal", r.event.positionSource)
                    put("horodatage_utc", java.time.Instant.ofEpochMilli(pv.utcEpochMs).toString())
                    put("t_mono_ns", pv.tMonoNs); put("time_base_id", pv.timeBaseId)
                    put("repere", pv.frame); put("qualite", pv.quality.label)
                }
                // Contrat de données : pas de hauteur en mètres sans sol de référence connu.
                put("hauteur", app.lumisite.core.contract.SiteHeight.UNQUALIFIED_LABEL)
            }
            // Paquet v6: paths of photos / voice notes = their entry in this zip (media/<name>).
            fun inZip(key: String) = (props[key] as? JsonArray)?.let { arr -> key to JsonArray(arr.map { o ->
                val obj = o as JsonObject; val path = obj["path"]?.jsonPrimitive?.contentOrNull
                if (path == null) obj else JsonObject(obj + ("path" to JsonPrimitive("media/" + path.substringAfterLast('/'))))
            }) }
            val media = listOfNotNull(inZip("photos"), inZip("voice_notes")).toMap()
            JsonObject(f + ("properties" to JsonObject(props + media + ("provenance" to provenance))))
        }
        return buildJsonObject { put("type", "FeatureCollection"); put("features", JsonArray(features)) }
    }

    /** Sidecar media/<photo>.json: the photo, its fingerprint and the business tags of its survey feature. */
    fun photoTags(fc: JsonObject, name: String, a: Attachment, header: SessionHeader, origin: SurveyEvent? = null): JsonObject {
        val feature = (fc["features"] as JsonArray).map { it as JsonObject }.firstOrNull { f ->
            ((f["properties"] as JsonObject)["photos"] as? JsonArray)?.any { (it as JsonObject)["path"]?.jsonPrimitive?.contentOrNull == "media/$name" } == true
        }
        return buildJsonObject {
            put("photo", "media/$name"); put("sha256", a.sha256); put("role", a.role)
            put("session_id", header.id); put("device_id", header.device.id)
            put("releve", feature ?: JsonNull)
            // Photo replaced or survey deleted: no final feature, the tags of the journal event that carried it are kept.
            if (feature == null && origin != null) put("releve_journal", LumiJson.encodeToJsonElement(SessionEvent.serializer(), origin))
        }
    }

    fun lisezmoi(header: SessionHeader, site: Site): String = """
        LumiSite — export de la session « ${header.name} » (${header.id}), site « ${site.name} ».

        releves.geojson
          Objets relevés (équipements, formes, remarques), état final de chaque objet : une seule FeatureCollection
          GeoJSON (RFC 7946), lisible dans QGIS. Géométrie en WGS84 (longitude, latitude) dans "coordinates".
          Les coordonnées locales du site (E/N en mètres, repère "site") sont dans properties.local ; elles ne sont jamais
          dans "coordinates". properties.provenance donne la méthode, l'horodatage, le repère (segment) et la qualité.
          La valeur z de properties.local est relative à l'origine AR de la session, ce n'est pas une hauteur au-dessus
          du sol (hauteur non qualifiée tant qu'aucun sol de référence n'est mesuré).
        session.json
          Manifeste : site, session, appareil, bases de temps, tous les événements, liste des fichiers avec leur nombre
          d'enregistrements, leur taille et leur empreinte SHA-256.
        raw/*.ndjson, raw/captation.jsonl
          Journaux bruts des capteurs, une ligne JSON par mesure (NDJSON), tels qu'enregistrés : poses AR, IMU, GNSS,
          état GNSS, événements, et trames au format docs/FORMAT_ECHANGE_CAPTATION.md (t_mono_ns, seq, type, valeurs).
          Les horodatages t_mono_ns / monotonicNs suivent l'horloge monotone de l'appareil ; les changements de base de
          temps (redémarrage, reprise) et les interruptions sont des événements explicites, jamais comblés.
        derived/trajectory_site.ndjson, derived/survey_points.ndjson
          Calculs reproductibles depuis raw/ : trajectoire et points relevés dans le repère du site (mètres).
        media/
          Photos, notes vocales et enregistrements de synchro (sync_*.wav), copiés tels quels (empreinte dans session.json).
          media/<photo>.json : à côté de chaque photo, le relevé auquel elle appartient (famille, détails, note,
          coordonnées, provenance). Les données EXIF de la photo (GPS, orientation, date) sont conservées.
        Segments ouverts
          Un journal resté ouvert (arrêt brutal) est exporté jusqu'à sa dernière ligne complète ; session.json le signale
          dans "notes".
    """.trimIndent() + "\n"
}
