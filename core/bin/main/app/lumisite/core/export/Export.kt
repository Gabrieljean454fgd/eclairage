package app.lumisite.core.export

import app.lumisite.core.compute.CorrectedPoint
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.Ndjson
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

const val FORMAT_ID = "lumisite.session"
const val FORMAT_VERSION = "0.1.0"

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
        val data = SessionReader.read(sessionDir)
        outDir.mkdirs()
        File(outDir, "raw").mkdirs(); File(outDir, "derived").mkdirs()
        val files = ArrayList<FileEntry>()

        fun copyRaw(stream: String, target: String, type: String, count: Int) {
            val src = File(sessionDir, stream)
            val dst = File(outDir, target)
            if (src.exists()) src.copyTo(dst, overwrite = true) else dst.writeText("")
            files += entry(outDir, target, "raw", type, count)
        }
        copyRaw(Streams.POSES, "raw/poses.ndjson", "PoseSample", data.poses.size)
        copyRaw(Streams.IMU, "raw/imu.ndjson", "ImuSample", data.imu.size)
        copyRaw(Streams.GPS, "raw/gps.ndjson", "GpsFix", data.gps.size)
        copyRaw(Streams.GPS_STATUS, "raw/gps_status.ndjson", "GpsStatusSample", data.gpsStatus.size)
        copyRaw(Streams.EVENTS, "raw/events.ndjson", "SessionEvent", data.events.size)

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
            events = data.events, files = files, derivedSummary = summary, integrity = data.skippedLines,
        )
        File(outDir, "session.json").writeText(PrettyJson.encodeToString(SessionManifest.serializer(), manifest))
        return manifest
    }

    fun derivedSurveyPoints(data: SessionData): List<DerivedSurveyPoint> {
        val tl = TrajectoryCorrector.timeline(data.events)
        return data.events.filterIsInstance<SurveyEvent>().map { s ->
            val seg = TrajectoryCorrector.activeAt(tl, s.t.monotonicNs)
            val pos = when {
                s.positionSource == "MANUAL_MAP" -> s.manualSite
                s.pose != null && seg != null -> seg.transform.apply(PoseGeometry.plane(s.pose)).let { Enu(it.x, it.y, PoseGeometry.height(s.pose)) }
                else -> null
            }
            DerivedSurveyPoint(
                s.id, s.kind, s.t.monotonicNs, s.t.utcEpochMs, pos, s.positionSource,
                if (s.positionSource == "MANUAL_MAP") null else seg?.eventId, s.tracking, s.dxf?.layer, s.dxf?.blockName,
            )
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
        return FileEntry(path, category, type, records, sha256(f.readBytes()), f.length())
    }

    private fun <T> writeNdjson(f: File, items: List<T>, ser: KSerializer<T>) =
        f.bufferedWriter().use { w -> items.forEach { w.write(LumiJson.encodeToString(ser, it)); w.write("\n") } }
}

val PrettyJson = kotlinx.serialization.json.Json(LumiJson) { prettyPrint = true }

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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
        if (manifest.format != FORMAT_ID) problems += "Unknown format ${manifest.format}"
        if (manifest.formatVersion.substringBefore('.') != FORMAT_VERSION.substringBefore('.')) problems += "Incompatible major version ${manifest.formatVersion}"
        fun <T> load(path: String, ser: KSerializer<T>): List<T> {
            val fe = manifest.files.firstOrNull { it.path == path }
            val b = bytesOf(path)
            if (b == null) { problems += "$path missing"; return emptyList() }
            if (fe != null && sha256(b) != fe.sha256) problems += "$path sha256 mismatch"
            val tmp = File.createTempFile("lumi", ".ndjson"); tmp.writeBytes(b)
            val r = Ndjson.read(tmp, ser); tmp.delete()
            if (r.skippedLines.isNotEmpty()) problems += "$path: unreadable lines ${r.skippedLines}"
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
        )
    }
}
