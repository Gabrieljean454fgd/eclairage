package app.lumisite.core.export

import app.lumisite.core.compute.SurveyPositions
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.Ndjson
import app.lumisite.core.journal.ReadResult
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.CalibrationEvent
import app.lumisite.core.model.RelocalizationEvent
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SessionStopped
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.Site
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.SyncEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.round

/** GeoJSON exchange format with the office tool (fiche E, docs/GEOJSON_EXPORT.md). No DXF is written by the app. */
object GeoJsonFeatures {
    const val FRAME_ID = "site"
    const val DEFAULT_STATUS = "existant"

    private fun r(v: Double, d: Int) = round(v * Math.pow(10.0, d.toDouble())) / Math.pow(10.0, d.toDouble())

    /** One equipment Feature (RFC 7946: lon/lat, no crs member; geometry null if the absolute position is unknown). */
    fun feature(
        s: SurveyEvent, site: Enu?, ltp: LocalTangentPlane, featureId: String, rev: Int,
        deviceId: String, sessionId: String, operator: String? = null,
    ): JsonObject {
        val op = when { s.deleted == true -> "delete"; s.supersedes != null -> "update"; else -> "create" }
        val payload = s.payload
        val catalogVersion = payload?.get("catalog_version")?.jsonPrimitive?.contentOrNull
        return buildJsonObject {
            put("type", "Feature")
            put("id", featureId)
            put("geometry", if (site == null || op == "delete") JsonNull else ltp.toLatLon(site.copy(u = 0.0)).let { ll ->
                buildJsonObject { put("type", "Point"); put("coordinates", buildJsonArray { add(JsonPrimitive(r(ll.lon, 8))); add(JsonPrimitive(r(ll.lat, 8))) }) }
            })
            put("properties", buildJsonObject {
                put("rev", rev); put("op", op); put("kind", "equipment")
                put("block_name", s.dxf?.blockName ?: s.kind)
                put("catalog_version", catalogVersion)
                put("layer", s.dxf?.layer ?: "RES - Elec")
                put("status", payload?.get("status")?.jsonPrimitive?.contentOrNull ?: DEFAULT_STATUS)
                put("local", if (site == null) JsonNull else buildJsonObject {
                    put("frame_id", FRAME_ID); put("x", r(site.e, 3)); put("y", r(site.n, 3)); put("z", r(site.u, 3))
                })
                put("accuracy", buildJsonObject { put("sigma_xy_m", JsonNull); put("sigma_z_m", JsonNull); put("method", s.positionSource) })
                put("time", buildJsonObject {
                    put("captured_utc", Instant.ofEpochMilli(s.t.utcEpochMs).toString())
                    put("device_monotonic_ns", s.t.monotonicNs)
                    put("clock_offset_ms", JsonNull); put("clock_sigma_ms", JsonNull); put("sync_ref", JsonNull)
                })
                put("device_id", deviceId); put("session_id", sessionId); put("operator", operator)
                put("event_id", s.id)
                s.note?.let { put("note", it) }
                payload?.get("details")?.let { put("details", it) }
                s.supersedes?.let { put("supersedes", it) }
                if (s.attachments.isNotEmpty()) put("photos", buildJsonArray {
                    s.attachments.filter { it.mediaType.startsWith("image/") }.forEach { a -> add(buildJsonObject {
                        put("photo_id", a.file.substringAfterLast('/').substringBeforeLast('.'))
                        put("path", "photos/$deviceId/$sessionId/${a.file.substringAfterLast('/')}"); put("sha256", a.sha256)
                    }) }
                })
            })
        }
    }

    /** All lines of a session in journal order: an edit keeps the same id with rev+1. */
    fun lines(events: List<SessionEvent>, ltp: LocalTangentPlane, deviceId: String, sessionId: String): List<JsonObject> {
        val timeline = TrajectoryCorrector.timeline(events)
        val root = HashMap<String, String>(); val rev = HashMap<String, Int>()
        return events.mapNotNull { e ->
            val (eid, sup) = when (e) { is SurveyEvent -> e.id to e.supersedes; is ShapeEvent -> e.id to e.supersedes; else -> return@mapNotNull null }
            val id = sup?.let { root[it] ?: it } ?: eid
            root[eid] = id
            val n = (rev[id] ?: 0) + 1; rev[id] = n
            when (e) {
                is SurveyEvent -> feature(e, SurveyPositions.resolve(e, timeline).site, ltp, id, n, deviceId, sessionId)
                is ShapeEvent -> shape(e, ltp, id, n, deviceId, sessionId)
                else -> null
            }
        }
    }

    /** Shape / annotation / zone Feature: WGS84 geometry plus the same geometry in site metres (properties.local). */
    fun shape(s: ShapeEvent, ltp: LocalTangentPlane, featureId: String, rev: Int, deviceId: String, sessionId: String): JsonObject {
        val op = when { s.deleted == true -> "delete"; s.supersedes != null -> "update"; else -> "create" }
        fun ll(p: Enu) = ltp.toLatLon(p.copy(u = 0.0)).let { buildJsonArray { add(JsonPrimitive(r(it.lon, 8))); add(JsonPrimitive(r(it.lat, 8))) } }
        val ring = when (s.geometryType) {
            "rectangle" -> if (s.points.size >= 2) s.points[0].let { a -> s.points[1].let { b -> listOf(a, Enu(b.e, a.n, 0.0), b, Enu(a.e, b.n, 0.0), a) } } else s.points
            "circle" -> s.points.firstOrNull()?.let { c -> (0..32).map { i -> val t = 2 * Math.PI * i / 32; Enu(c.e + (s.radiusM ?: 1.0) * Math.cos(t), c.n + (s.radiusM ?: 1.0) * Math.sin(t), 0.0) } } ?: emptyList()
            "polygon" -> if (s.points.isNotEmpty()) s.points + s.points.first() else s.points
            else -> s.points
        }
        val geom = when {
            op == "delete" || ring.isEmpty() -> JsonNull
            s.geometryType == "point" || ring.size == 1 -> buildJsonObject { put("type", "Point"); put("coordinates", ll(ring[0])) }
            s.geometryType in setOf("rectangle", "circle", "polygon") -> buildJsonObject { put("type", "Polygon"); put("coordinates", buildJsonArray { add(JsonArray(ring.map(::ll))) }) }
            else -> buildJsonObject { put("type", "LineString"); put("coordinates", JsonArray(ring.map(::ll))) }
        }
        return buildJsonObject {
            put("type", "Feature"); put("id", featureId); put("geometry", geom)
            put("properties", buildJsonObject {
                put("rev", rev); put("op", op); put("kind", s.kind); put("shape", s.geometryType)
                put("local", buildJsonObject {
                    put("frame_id", FRAME_ID)
                    put("points", JsonArray(s.points.map { p -> buildJsonArray { add(JsonPrimitive(r(p.e, 3))); add(JsonPrimitive(r(p.n, 3))); add(JsonPrimitive(r(p.u, 3))) } }))
                    s.radiusM?.let { put("radius_m", r(it, 3)) }
                })
                s.label?.let { put("label", it) }; s.comment?.let { put("note", it) }; s.annotationType?.let { put("annotation_type", it) }
                put("style", buildJsonObject { put("stroke", s.style.stroke); put("fill", s.style.fill); put("width_dp", s.style.widthDp); put("opacity", s.style.opacity) })
                s.attachedTo?.let { put("attached_to", it) }
                put("time", buildJsonObject { put("captured_utc", Instant.ofEpochMilli(s.t.utcEpochMs).toString()); put("device_monotonic_ns", s.t.monotonicNs) })
                put("device_id", deviceId); put("session_id", sessionId); put("event_id", s.id)
                s.supersedes?.let { put("supersedes", it) }
            })
        }
    }
}

/**
 * Live GeoJSONSeq (RFC 8142) writer: one Feature per line, appended and fsynced after each object during the session.
 * On resume, the last truncated line is sealed and ids/revisions are rebuilt from the complete lines.
 */
class GeoJsonSeqWriter(val file: File, private val ltp: LocalTangentPlane, private val deviceId: String, private val sessionId: String) : AutoCloseable {
    private val root = HashMap<String, String>()
    private val rev = HashMap<String, Int>()
    private val out: FileOutputStream

    init {
        file.parentFile?.mkdirs()
        if (file.exists()) {
            Ndjson.sealPartialLine(file)
            GeoJsonSeq.read(file).items.forEach { f ->
                val id = f["id"]!!.jsonPrimitive.content; val p = f["properties"]!!.jsonObject
                p["event_id"]?.jsonPrimitive?.contentOrNull?.let { root[it] = id }
                rev[id] = maxOf(rev[id] ?: 0, p["rev"]?.jsonPrimitive?.intOrNull ?: 0)
            }
        }
        out = FileOutputStream(file, true)
    }

    @Synchronized fun appendShape(s: ShapeEvent) {
        val id = s.supersedes?.let { root[it] ?: it } ?: s.id
        root[s.id] = id
        val n = (rev[id] ?: 0) + 1; rev[id] = n
        out.write((GeoJsonFeatures.shape(s, ltp, id, n, deviceId, sessionId).toString() + "\n").toByteArray(Charsets.UTF_8))
        out.flush(); out.fd.sync()
    }

    @Synchronized fun append(s: SurveyEvent, site: Enu?) {
        val id = s.supersedes?.let { root[it] ?: it } ?: s.id
        root[s.id] = id
        val n = (rev[id] ?: 0) + 1; rev[id] = n
        out.write((GeoJsonFeatures.feature(s, site, ltp, id, n, deviceId, sessionId).toString() + "\n").toByteArray(Charsets.UTF_8))
        out.flush(); out.fd.sync()
    }

    override fun close() { runCatching { out.fd.sync() }; out.close() }

    companion object { const val FILE = "features.geojsonl" }
}

object GeoJsonSeq {
    /** Reads complete lines; a truncated/invalid line (typically the last one after a crash) is skipped and reported. */
    fun read(file: File): ReadResult<JsonObject> = Ndjson.read(file, JsonObject.serializer())

    /** Current state: highest rev per id wins; deleted ids are dropped. */
    fun latest(lines: List<JsonObject>): Map<String, JsonObject> =
        lines.groupBy { it["id"]!!.jsonPrimitive.content }
            .mapValues { (_, l) -> l.maxBy { it["properties"]!!.jsonObject["rev"]!!.jsonPrimitive.intOrNull ?: 0 } }
            .filterValues { it["properties"]!!.jsonObject["op"]?.jsonPrimitive?.contentOrNull != "delete" }
}

/** <session_id>.frames.json: site origin and AR → site transformations, sync events. */
object Frames {
    fun of(site: Site, events: List<SessionEvent>): JsonObject = buildJsonObject {
        put("frame_id", GeoJsonFeatures.FRAME_ID)
        put("origin", buildJsonObject { put("lat", site.origin.lat); put("lon", site.origin.lon); put("crs", "EPSG:4326"); put("source", site.originSource) })
        put("axes", "X est, Y nord, Z haut, mètres, origine du site")
        put("calibrations", JsonArray(events.filterIsInstance<CalibrationEvent>().map { LumiJson.encodeToJsonElement(SessionEvent.serializer(), it) }))
        put("relocalizations", JsonArray(events.filterIsInstance<RelocalizationEvent>().map { LumiJson.encodeToJsonElement(SessionEvent.serializer(), it) }))
        put("sync", JsonArray(events.filterIsInstance<SyncEvent>().map { LumiJson.encodeToJsonElement(SessionEvent.serializer(), it) }))
    }
}

@Serializable
data class PackageFile(val path: String, val bytes: Long, val sha256: String)

@Serializable
data class PackageSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("features_file") val featuresFile: String,
    @SerialName("frames_file") val framesFile: String,
    @SerialName("feature_count") val featureCount: Int,
    val closed: Boolean,
    @SerialName("truncated_lines") val truncatedLines: List<Int> = emptyList(),
)

@Serializable
data class ExportPackageManifest(
    val schema: String = SCHEMA,
    @SerialName("schema_version") val schemaVersion: String = SCHEMA_VERSION,
    @SerialName("project_id") val projectId: String,
    @SerialName("project_name") val projectName: String,
    @SerialName("catalog_version") val catalogVersion: String?,
    @SerialName("exported_utc") val exportedUtc: String,
    val generator: String,
    val devices: List<String>,
    val sessions: List<PackageSession>,
    val files: List<PackageFile>,
) {
    companion object {
        const val SCHEMA = "lumisite-geojson-export"
        const val SCHEMA_VERSION = "1.0.0"
        const val FILE = "manifest.json"
    }
}

object ExportPackage {
    /** Inputs of one session: its journal directory and its live features file (absent for format 0.1 sessions). */
    data class SessionInput(val dir: File)

    /**
     * Builds LumiSite_<project_id>/ in [parent]: manifest, catalog, sessions/<device>/<session>.geojsonl + .frames.json,
     * photos/, raw/<device>/<session>.zip (unchanged lumisite.session export), refs/osm_source.json.
     */
    fun build(
        site: Site, sessions: List<SessionInput>, catalogJson: String?, osmSource: File?, parent: File, nowUtcMs: Long, generator: String,
    ): File {
        val root = File(parent, "LumiSite_${site.id}").apply { deleteRecursively(); mkdirs() }
        val ltp = LocalTangentPlane(site.origin)
        catalogJson?.let { File(root, "catalog/equipment_catalog.json").apply { parentFile.mkdirs() }.writeText(it) }
        osmSource?.takeIf { it.exists() }?.copyTo(File(root, "refs/osm_source.json"))
        val out = ArrayList<PackageSession>(); val devices = LinkedHashSet<String>(); var catalogVersion: String? = null
        for (si in sessions) {
            val data = SessionReader.read(si.dir, includeImu = false)
            val dev = data.header.device.id; val sid = data.header.id
            devices += dev
            val feat = File(root, "sessions/$dev/$sid.geojsonl").apply { parentFile.mkdirs() }
            val live = File(si.dir, GeoJsonSeqWriter.FILE)
            val truncated: List<Int>
            if (live.exists()) {
                Ndjson.snapshotCompleteLines(live, feat)
                truncated = GeoJsonSeq.read(live).skippedLines
            } else {
                feat.writeText(GeoJsonFeatures.lines(data.events, ltp, dev, sid).joinToString("") { "$it\n" })
                truncated = emptyList()
            }
            val lines = GeoJsonSeq.read(feat).items
            catalogVersion = catalogVersion ?: lines.firstNotNullOfOrNull { it["properties"]?.jsonObject?.get("catalog_version")?.jsonPrimitive?.contentOrNull }
            File(root, "sessions/$dev/$sid.frames.json").writeText(Frames.of(site, data.events).toString())
            data.events.filterIsInstance<SurveyEvent>().flatMap { it.attachments }.forEach { a ->
                val src = File(si.dir, a.file)
                if (src.exists()) src.copyTo(File(root, "photos/$dev/$sid/${src.name}"), overwrite = true)
            }
            val rawDir = File(parent, "raw_$sid").apply { deleteRecursively() }
            SessionExporter.exportToDir(site, si.dir, rawDir, nowUtcMs, generator)
            File(root, "raw/$dev").mkdirs()
            SessionExporter.zipDir(rawDir, File(root, "raw/$dev/$sid.zip"))
            rawDir.deleteRecursively()
            out += PackageSession(sid, dev, "sessions/$dev/$sid.geojsonl", "sessions/$dev/$sid.frames.json",
                GeoJsonSeq.latest(lines).size, data.events.any { it is SessionStopped }, truncated)
        }
        writeManifest(root, ExportPackageManifest(
            projectId = site.id, projectName = site.name, catalogVersion = catalogVersion,
            exportedUtc = Instant.ofEpochMilli(nowUtcMs).toString(), generator = generator,
            devices = devices.toList(), sessions = out, files = emptyList(),
        ))
        return root
    }

    /** Hashes every file (except the manifest) and writes the manifest atomically (temporary file, then rename). */
    fun writeManifest(root: File, m: ExportPackageManifest) {
        val files = root.walkTopDown().filter { it.isFile && it.name != ExportPackageManifest.FILE && !it.name.endsWith(".tmp") }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .map { PackageFile(it.relativeTo(root).invariantSeparatorsPath, it.length(), sha256(it)) }.toList()
        val tmp = File(root, ExportPackageManifest.FILE + ".tmp")
        tmp.writeText(PrettyJson.encodeToString(ExportPackageManifest.serializer(), m.copy(files = files)))
        if (!tmp.renameTo(File(root, ExportPackageManifest.FILE))) { File(root, ExportPackageManifest.FILE).delete(); check(tmp.renameTo(File(root, ExportPackageManifest.FILE))) }
    }

    fun readManifest(root: File): ExportPackageManifest =
        LumiJson.decodeFromString(ExportPackageManifest.serializer(), File(root, ExportPackageManifest.FILE).readText())

    /** Problems found: missing files, size or sha256 mismatch. */
    fun verify(root: File): List<String> = readManifest(root).files.mapNotNull { f ->
        val file = File(root, f.path)
        when {
            !file.exists() -> "absent : ${f.path}"
            file.length() != f.bytes || sha256(file) != f.sha256 -> "modifié : ${f.path}"
            else -> null
        }
    }

    /** ZIP without recompression ("store"): photos and zips are already compressed. */
    fun zipStored(dir: File, zip: File) {
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.setMethod(ZipOutputStream.STORED)
            dir.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(dir).invariantSeparatorsPath }.forEach { f ->
                val bytes = f.readBytes()
                val e = ZipEntry("${dir.name}/${f.relativeTo(dir).invariantSeparatorsPath}").apply {
                    method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                }
                zos.putNextEntry(e); zos.write(bytes); zos.closeEntry()
            }
        }
    }
}
