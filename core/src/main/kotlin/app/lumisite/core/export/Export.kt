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
    /** "raw" = as captured, never modified. "derived" = computed by the exporter, reproducible from raw+events. */
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
    /** Builds an export directory (session.json + raw/ + derived/) from a session journal directory. */
    fun exportToDir(site: Site, sessionDir: File, outDir: File, exportedUtcMs: Long, generator: String): SessionManifest {
        outDir.mkdirs()
        File(outDir, "raw").mkdirs(); File(outDir, "derived").mkdirs()
        // Snapshot first, then compute everything from the snapshot, so that counts, hashes and derived
        // files describe exactly the exported bytes even if the journal is still being appended to.
        val staging = File(outDir.parentFile, outDir.name + ".snapshot").apply { deleteRecursively(); mkdirs() }
        File(sessionDir, Streams.HEADER).copyTo(File(staging, Streams.HEADER))
        val rawStreams = listOf(
            Triple(Streams.POSES, "raw/poses.ndjson", "PoseSample"),
            Triple(Streams.IMU, "raw/imu.ndjson", "ImuSample"),
            Triple(Streams.GPS, "raw/gps.ndjson", "GpsFix"),
            Triple(Streams.GPS_STATUS, "raw/gps_status.ndjson", "GpsStatusSample"),
            Triple(Streams.EVENTS, "raw/events.ndjson", "SessionEvent"),
        )
        rawStreams.forEach { (stream, _, _) -> Segments.snapshot(sessionDir, stream, File(staging, stream)) }
        val data = SessionReader.read(staging)
        val counts = mapOf(
            Streams.POSES to data.poses.size, Streams.IMU to data.imu.size, Streams.GPS to data.gps.size,
            Streams.GPS_STATUS to data.gpsStatus.size, Streams.EVENTS to data.events.size,
        )
        val files = ArrayList<FileEntry>()
        val integrity = rawStreams.mapNotNull { (stream, target, _) -> data.skippedLines[stream]?.let { target to it } }.toMap()
        rawStreams.forEach { (stream, target, type) ->
            File(staging, stream).copyTo(File(outDir, target), overwrite = true)
            files += entry(outDir, target, "raw", type, counts.getValue(stream))
        }
        staging.deleteRecursively()

        val ltp = LocalTangentPlane(site.origin)
        val traj = TrajectoryCorrector.correct(data.poses, data.events, ltp)
        writeNdjson(File(outDir, "derived/trajectory_site.ndjson"), traj, CorrectedPoint.serializer())
        files += entry(outDir, "derived/trajectory_site.ndjson", "derived", "CorrectedPoint", traj.size)

        val survey = derivedSurveyPoints(data)
        writeNdjson(File(outDir, "derived/survey_points.ndjson"), survey, DerivedSurveyPoint.serializer())
        files += entry(outDir, "derived/survey_points.ndjson", "derived", "DerivedSurveyPoint", survey.size)

        val firstCal = TrajectoryCorrector.timeline(data.events).firstOrNull()?.fromNs
        val summary = DerivedSummary(
            trajectoryPoints = traj.size,
            trajectoryLengthM = TrajectoryCorrector.pathLengthM(traj),
            posesWithoutCalibration = data.poses.count { firstCal == null || it.t.monotonicNs < firstCal },
            trackingStateCounts = data.poses.groupingBy { it.tracking.name }.eachCount(),
            notes = listOf(
                "Trajectory is an estimate from the platform tracker + user calibration/relocalizations; it is not ground truth.",
                "Only TRACKING poses after the first calibration are included in derived/trajectory_site.ndjson.",
            ),
        )
        val manifest = SessionManifest(
            exportedUtcMs = exportedUtcMs, generator = generator, site = site, session = data.header,
            events = data.events, files = files, derivedSummary = summary, integrity = integrity,
        )
        File(outDir, "session.json").writeText(PrettyJson.encodeToString(SessionManifest.serializer(), manifest))
        return manifest
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

fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
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

object SessionImporter {
    fun load(source: File): LoadedExport {
        val bytesOf: (String) -> ByteArray? = if (source.isDirectory) {
            { p -> File(source, p).takeIf { it.exists() }?.readBytes() }
        } else {
            val zf = ZipFile(source)
            val cache = zf.entries().asSequence().associate { it.name to zf.getInputStream(it).use { s -> s.readBytes() } }
            zf.close();
            { p -> cache[p] }
        }
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
