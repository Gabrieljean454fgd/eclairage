package app.lumisite.core.export

import app.lumisite.core.compute.CorrectedPoint
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.Ndjson
import app.lumisite.core.journal.Segments
import app.lumisite.core.journal.SessionData
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.GpsFix
import app.lumisite.core.model.GpsStatusSample
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SessionHeader
import app.lumisite.core.model.Site
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.SyncEvent
import app.lumisite.core.model.TrackingState
import app.lumisite.core.compute.PoseGeometry
import app.lumisite.core.compute.SurveyPositions
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

const val FORMAT_ID = "lumisite.session"
const val FORMAT_VERSION = "0.2.1"

/** Versions this build reads: 0.1.x (before lot 7) and 0.2.x. Anything newer is refused with a clear message. */
object FormatVersions {
    private fun parts(v: String) = v.split('.').map { it.toIntOrNull() ?: -1 }.let { it + List(3 - minOf(3, it.size)) { 0 } }
    fun problem(v: String): String? {
        val (ma, mi) = parts(v); val (cMa, cMi) = parts(FORMAT_VERSION)
        return when {
            ma < 0 || mi < 0 -> "Version de format illisible : $v"
            ma > cMa || (ma == cMa && mi > cMi) -> "Format $v plus récent que cette version de LumiSite ($FORMAT_VERSION) : mettez l'outil à jour"
            ma != cMa -> "Version de format incompatible : $v"
            else -> null
        }
    }
}

@Serializable
data class FileEntry(
    val path: String,
    /** "raw" = as captured, never modified. "derived" = computed by the exporter, reproducible from raw+events. "media" = attachment copied as is (stage 1.6). */
    val category: String,
    val recordType: String,
    val records: Int,
    val sha256: String,
    val bytes: Long,
)

@Serializable
data class DerivedSurveyPoint(
    val surveyEventId: String,
    val kind: String,
    val monotonicNs: Long,
    val utcEpochMs: Long,
    /** Position recomputed with the transform active at record time (same as app), site ENU metres. */
    val site: Enu?,
    val positionSource: String,
    val transformEventId: String?,
    val tracking: TrackingState,
    val dxfLayer: String?,
    val dxfBlock: String?,
    /** Computed zone (deepest containing zone), never stored in the journal. */
    val zoneId: String? = null,
)

@Serializable
data class DerivedSummary(
    val trajectoryPoints: Int,
    val trajectoryLengthM: Double,
    val posesWithoutCalibration: Int,
    val trackingStateCounts: Map<String, Int>,
    val notes: List<String>,
)

@Serializable
data class DxfPreparation(
    val status: String = "NOT_GENERATED",
    val planarCrs: String = "SITE_ENU",
    val units: String = "m",
    val axisMapping: String = "DXF X = E (east), DXF Y = N (north), DXF Z = U (up)",
    val pointsFile: String = "derived/survey_points.ndjson",
    val trajectoryFile: String = "derived/trajectory_site.ndjson",
    val notes: List<String> = listOf(
        "Reproject SITE_ENU to a national CRS (e.g. EPSG:2154) if drawings require it; origin and definition are in site.crs.",
        "Use SurveyEvent.dxf (layer, blockName, attributes) as block insertion hints; blocks are not defined yet.",
    ),
)

@Serializable
data class SessionManifest(
    val format: String = FORMAT_ID,
    val formatVersion: String = FORMAT_VERSION,
    val exportedUtcMs: Long,
    val generator: String,
    val site: Site,
    val session: SessionHeader,
    /** All raw events (calibration, relocalization, sync, survey, tracking...) in capture order. */
    val events: List<SessionEvent>,
    val files: List<FileEntry>,
    val derivedSummary: DerivedSummary,
    val dxfPreparation: DxfPreparation = DxfPreparation(),
    val integrity: Map<String, List<Int>> = emptyMap(),
)

object SessionExporter {
    /** Export progress: [doneBytes] over [totalBytes] of the session (raw streams + media), and the current step. */
    data class Progress(val step: String, val doneBytes: Long, val totalBytes: Long) {
        val fraction: Float get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    private val rawStreams = listOf(
        Triple(Streams.POSES, "raw/poses.ndjson", "PoseSample"),
        Triple(Streams.IMU, "raw/imu.ndjson", "ImuSample"),
        Triple(Streams.GPS, "raw/gps.ndjson", "GpsFix"),
        Triple(Streams.GPS_STATUS, "raw/gps_status.ndjson", "GpsStatusSample"),
        Triple(Streams.EVENTS, "raw/events.ndjson", "SessionEvent"),
    )

    private fun serializerOf(type: String): KSerializer<*> = when (type) {
        "PoseSample" -> PoseSample.serializer(); "ImuSample" -> ImuSample.serializer(); "GpsFix" -> GpsFix.serializer()
        "GpsStatusSample" -> GpsStatusSample.serializer(); "SessionEvent" -> SessionEvent.serializer()
        "CorrectedPoint" -> CorrectedPoint.serializer(); "DerivedSurveyPoint" -> DerivedSurveyPoint.serializer()
        else -> error("unknown type $type")
    }

    /** Counts readable records and unreadable line numbers of an NDJSON file, one line at a time (stage 1.6). */
    fun scan(file: File, type: String, onBytes: (Long) -> Unit = {}): Pair<Int, List<Int>> {
        val ser = serializerOf(type)
        var ok = 0; val bad = ArrayList<Int>()
        Ndjson.forEachLine(listOf(file)) { n, line ->
            try { LumiJson.decodeFromString(ser, line); ok++ } catch (e: Exception) { bad += n }
            onBytes(line.length + 1L)
        }
        return ok to bad
    }

    /**
     * Builds an export directory (session.json + raw/ + derived/ + media/) from a session journal directory.
     * Stage 1.6: everything is streamed. Raw streams are snapshotted (complete lines only) straight into raw/, then
     * scanned line by line; the trajectory is computed pose by pose; media are copied by blocks. Only the events
     * (small) are held in memory. [progress] is called with real byte counts.
     */
    fun exportToDir(
        site: Site, sessionDir: File, outDir: File, exportedUtcMs: Long, generator: String,
        progress: (Progress) -> Unit = {},
    ): SessionManifest {
        outDir.mkdirs()
        File(outDir, "raw").mkdirs(); File(outDir, "derived").mkdirs()
        val header = LumiJson.decodeFromString(SessionHeader.serializer(), File(sessionDir, Streams.HEADER).readText())
        // Snapshot first, then compute everything from the snapshot, so that counts, hashes and derived
        // files describe exactly the exported bytes even if the journal is still being appended to.
        rawStreams.forEach { (stream, target, _) -> Segments.snapshot(sessionDir, stream, File(outDir, target)) }
        val events = Ndjson.read(File(outDir, "raw/events.ndjson"), SessionEvent.serializer()).items
        val refs = events.filterIsInstance<SurveyEvent>().flatMap { it.attachments }.distinctBy { it.file }
        val media = refs.mapNotNull { a -> File(sessionDir, a.file).takeIf { it.isFile }?.let { a to it } }
        // A referenced attachment that is absent or altered is declared in the manifest and reported by verify():
        // the export is never presented as complete without it.
        val mediaProblems = refs.mapNotNull { a ->
            val f = File(sessionDir, a.file)
            when {
                !f.isFile -> "$ATTACHMENT_PROBLEM${a.file} absent"
                a.sha256 != null && sha256(f) != a.sha256 -> "$ATTACHMENT_PROBLEM${a.file} empreinte différente de celle enregistrée"
                else -> null
            }
        }
        val total = rawStreams.sumOf { File(outDir, it.second).length() } * 2 + media.sumOf { it.second.length() }
        var done = 0L
        fun step(name: String, bytes: Long) { done += bytes; progress(Progress(name, done, total)) }

        val files = ArrayList<FileEntry>()
        val integrity = LinkedHashMap<String, List<Int>>()
        for ((_, target, type) in rawStreams) {
            val f = File(outDir, target)
            var pending = 0L
            val (count, bad) = scan(f, type) { b -> pending += b; if (pending > 1 shl 20) { step("Lecture $target", pending); pending = 0 } }
            step("Lecture $target", pending)
            if (bad.isNotEmpty()) integrity[target] = bad
            files += FileEntry(target, "raw", type, count, sha256(f) { step("Empreinte $target", it) }, f.length())
        }

        val ltp = LocalTangentPlane(site.origin)
        val trajFile = File(outDir, "derived/trajectory_site.ndjson")
        val timeline = TrajectoryCorrector.timeline(events)
        var trajPoints = 0; var length = 0.0; var prev: CorrectedPoint? = null
        var withoutCal = 0
        val trackingCounts = LinkedHashMap<String, Int>()
        trajFile.bufferedWriter().use { w ->
            val poses = File(outDir, "raw/poses.ndjson")
            poses.bufferedReader().useLines { lines ->
                val seq = lines.filter { it.isNotBlank() }.mapNotNull { runCatching { LumiJson.decodeFromString(PoseSample.serializer(), it) }.getOrNull() }
                    .onEach { p ->
                        trackingCounts[p.tracking.name] = (trackingCounts[p.tracking.name] ?: 0) + 1
                        if (TrajectoryCorrector.activeAt(timeline, p.t) == null) withoutCal++
                    }
                TrajectoryCorrector.correctEach(seq, events, ltp) { c ->
                    w.write(LumiJson.encodeToString(CorrectedPoint.serializer(), c)); w.write("\n")
                    prev?.let { if (it.timeBaseId == c.timeBaseId) length += kotlin.math.hypot(c.site.e - it.site.e, c.site.n - it.site.n) }
                    prev = c; trajPoints++
                }
            }
        }
        files += entry(outDir, "derived/trajectory_site.ndjson", "derived", "CorrectedPoint", trajPoints)

        val survey = SurveyPositions.resolveAll(events).let { resolved ->
            val zones = app.lumisite.core.survey.Zones.current(events)
            resolved.map { r ->
                val s = r.event
                DerivedSurveyPoint(s.id, s.kind, s.t.monotonicNs, s.t.utcEpochMs, r.site, s.positionSource, r.transformEventId, s.tracking, s.dxf?.layer, s.dxf?.blockName,
                    zoneId = r.site?.let { app.lumisite.core.survey.Zones.zoneOf(it, zones)?.id })
            }
        }
        writeNdjson(File(outDir, "derived/survey_points.ndjson"), survey, DerivedSurveyPoint.serializer())
        files += entry(outDir, "derived/survey_points.ndjson", "derived", "DerivedSurveyPoint", survey.size)

        for ((a, src) in media) {
            val target = "media/" + a.file.substringAfterLast('/')
            val dst = File(outDir, target).apply { parentFile.mkdirs() }
            src.inputStream().use { input -> dst.outputStream().use { out -> copyBlocks(input, out) { step("Média ${src.name}", it) } } }
            files += FileEntry(target, "media", a.mediaType, 1, sha256(dst), dst.length())
        }

        val summary = DerivedSummary(
            trajectoryPoints = trajPoints,
            trajectoryLengthM = length,
            posesWithoutCalibration = withoutCal,
            trackingStateCounts = trackingCounts,
            notes = listOf(
                "Trajectory is an estimate from the platform tracker + user calibration/relocalizations; it is not ground truth.",
                "Only TRACKING poses after the first calibration are included in derived/trajectory_site.ndjson.",
                "Poses are ordered by (time base, monotonicNs); a phone reboot starts a new time base and ends earlier transforms.",
            ) + mediaProblems,
        )
        val manifest = SessionManifest(
            exportedUtcMs = exportedUtcMs, generator = generator, site = site, session = header,
            events = events, files = files, derivedSummary = summary, integrity = integrity,
        )
        val tmp = File(outDir, "session.json.tmp")
        tmp.bufferedWriter().use { it.write(PrettyJson.encodeToString(SessionManifest.serializer(), manifest)) }
        check(tmp.renameTo(File(outDir, "session.json")))
        progress(Progress("Terminé", total, total))
        return manifest
    }

    private fun copyBlocks(input: java.io.InputStream, out: java.io.OutputStream, onBytes: (Long) -> Unit) {
        val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); onBytes(n.toLong()) }
    }

    fun derivedSurveyPoints(data: SessionData): List<DerivedSurveyPoint> =
        app.lumisite.core.survey.Zones.current(data.events).let { zones ->
            SurveyPositions.resolveAll(data.events).map { r ->
                val s = r.event
                DerivedSurveyPoint(s.id, s.kind, s.t.monotonicNs, s.t.utcEpochMs, r.site, s.positionSource, r.transformEventId, s.tracking, s.dxf?.layer, s.dxf?.blockName,
                    zoneId = r.site?.let { app.lumisite.core.survey.Zones.zoneOf(it, zones)?.id })
            }
        }

    fun zipDir(dir: File, zip: File) {
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            dir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(dir).path }.forEach { f ->
                zos.putNextEntry(ZipEntry(f.relativeTo(dir).invariantSeparatorsPath))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun entry(root: File, path: String, category: String, type: String, records: Int): FileEntry {
        val f = File(root, path)
        return FileEntry(path, category, type, records, sha256(f), f.length())
    }

    private fun <T> writeNdjson(f: File, items: List<T>, ser: KSerializer<T>) =
        f.bufferedWriter().use { w -> items.forEach { w.write(LumiJson.encodeToString(ser, it)); w.write("\n") } }
}

val PrettyJson = kotlinx.serialization.json.Json(LumiJson) { prettyPrint = true }

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()

fun sha256(file: File, onBytes: (Long) -> Unit = {}): String = file.inputStream().use { sha256(it, onBytes) }

fun sha256(input: java.io.InputStream, onBytes: (Long) -> Unit = {}): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024); var pending = 0L
    while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n); pending += n; if (pending >= 1 shl 20) { onBytes(pending); pending = 0 } }
    onBytes(pending)
    return md.digest().hex()
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/** Reads an export (directory or .zip), verifies hashes and record counts. */
data class LoadedExport(
    val manifest: SessionManifest,
    val poses: List<PoseSample>,
    val imu: List<ImuSample>,
    val gps: List<GpsFix>,
    val gpsStatus: List<GpsStatusSample>,
    val trajectory: List<CorrectedPoint>,
    val surveyPoints: List<DerivedSurveyPoint>,
    val problems: List<String>,
    /** Lines already unreadable in the device journal (abrupt shutdown) and declared in manifest.integrity. */
    val warnings: List<String> = emptyList(),
) {
    val syncEvents: List<SyncEvent> get() = manifest.events.filterIsInstance<SyncEvent>()
}

/** Prefix of manifest notes describing a missing/altered attachment (always reported as a problem by verify). */
const val ATTACHMENT_PROBLEM = "ATTACHMENT_PROBLEM: "
private const val ZIP_MAX_ENTRIES = 200_000
private const val ZIP_SPACE_MARGIN = 64L shl 20

object SessionImporter {
    /**
     * Streaming verification of an export (stage 1.6): every file listed in session.json is hashed by blocks and every
     * NDJSON file is scanned line by line, so a multi-hour session is checked without loading any series in memory.
     * Returns the problems (empty = valid); lines already declared in manifest.integrity are not problems.
     */
    fun verify(source: File, onBytes: (Long) -> Unit = {}): List<String> = withDir(source) { dir ->
        val manifest = LumiJson.decodeFromString(SessionManifest.serializer(), File(dir, "session.json").readText())
        val problems = ArrayList<String>()
        if (manifest.format != FORMAT_ID) problems += "Unknown format ${manifest.format}"
        FormatVersions.problem(manifest.formatVersion)?.let { problems += it }
        manifest.derivedSummary.notes.filter { it.startsWith(ATTACHMENT_PROBLEM) }.forEach { problems += it }
        for (fe in manifest.files) {
            val f = File(dir, fe.path)
            if (!f.isFile) { problems += "${fe.path} missing"; continue }
            if (sha256(f, onBytes) != fe.sha256) problems += "${fe.path} sha256 mismatch"
            if (fe.path.endsWith(".ndjson")) {
                val (count, bad) = SessionExporter.scan(f, fe.recordType)
                if (bad.isNotEmpty() && manifest.integrity[fe.path] != bad) problems += "${fe.path}: unreadable lines $bad"
                if (fe.records != count) problems += "${fe.path}: expected ${fe.records} records, read $count"
            }
        }
        problems
    }

    /** Runs [block] on a directory: [source] itself, or a temporary extraction of the zip (entry by entry). */
    fun <T> withDir(source: File, block: (File) -> T): T {
        if (source.isDirectory) return block(source)
        val tmp = Files.createTempDirectory("lumi-import").toFile()
        try {
            // Bounded extraction: entry count and total size are capped by the free space left (minus a margin).
            val budget = tmp.usableSpace - ZIP_SPACE_MARGIN
            var written = 0L
            ZipFile(source).use { zf ->
                var n = 0
                for (e in zf.entries().asSequence().filter { !it.isDirectory }) {
                    require(++n <= ZIP_MAX_ENTRIES) { "Zip refusé : plus de $ZIP_MAX_ENTRIES fichiers" }
                    val out = File(tmp, e.name).canonicalFile
                    require(out.path.startsWith(tmp.canonicalPath + File.separator)) { "Entrée de zip hors du dossier : ${e.name}" }
                    out.parentFile.mkdirs()
                    zf.getInputStream(e).use { input -> out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val r = input.read(buf); if (r < 0) break
                            written += r
                            require(written <= budget) { "Zip refusé : son contenu dépasse l'espace libre" }
                            o.write(buf, 0, r)
                        }
                    } }
                }
            }
            return block(tmp)
        } finally { tmp.deleteRecursively() }
    }

    fun load(source: File): LoadedExport = withDir(source) { dir -> loadDir(dir) }

    private fun loadDir(source: File): LoadedExport {
        val bytesOf: (String) -> ByteArray? = { p -> File(source, p).takeIf { it.exists() }?.readBytes() }
        val manifestBytes = requireNotNull(bytesOf("session.json")) { "session.json missing" }
        val manifest = LumiJson.decodeFromString(SessionManifest.serializer(), manifestBytes.decodeToString())
        val problems = ArrayList<String>()
        val warnings = ArrayList<String>()
        if (manifest.format != FORMAT_ID) problems += "Unknown format ${manifest.format}"
        FormatVersions.problem(manifest.formatVersion)?.let { problems += it }
        fun <T> load(path: String, ser: KSerializer<T>): List<T> {
            val fe = manifest.files.firstOrNull { it.path == path }
            val b = bytesOf(path)
            if (b == null) { problems += "$path missing"; return emptyList() }
            if (fe != null && sha256(b) != fe.sha256) problems += "$path sha256 mismatch"
            val tmp = File.createTempFile("lumi", ".ndjson"); tmp.writeBytes(b)
            val r = Ndjson.read(tmp, ser); tmp.delete()
            if (r.skippedLines.isNotEmpty()) {
                if (manifest.integrity[path] == r.skippedLines) warnings += "$path: lines ${r.skippedLines} were already unreadable in the device journal (abrupt shutdown)"
                else problems += "$path: unreadable lines ${r.skippedLines}"
            }
            if (fe != null && fe.records != r.items.size) problems += "$path: expected ${fe.records} records, read ${r.items.size}"
            return r.items
        }
        return LoadedExport(
            manifest,
            load("raw/poses.ndjson", PoseSample.serializer()),
            load("raw/imu.ndjson", ImuSample.serializer()),
            load("raw/gps.ndjson", GpsFix.serializer()),
            load("raw/gps_status.ndjson", GpsStatusSample.serializer()),
            load("derived/trajectory_site.ndjson", CorrectedPoint.serializer()),
            load("derived/survey_points.ndjson", DerivedSurveyPoint.serializer()),
            problems,
            warnings,
        )
    }
}
