package app.lumisite.android.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.lumisite.android.BuildConfig
import app.lumisite.core.compute.SurveyPositions
import app.lumisite.core.model.BBox
import app.lumisite.core.osm.Bboxes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.lumisite.core.export.LoadedExport
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.export.SessionExporter
import app.lumisite.core.export.SessionImporter
import app.lumisite.core.export.SessionManifest
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.journal.Streams
import app.lumisite.core.model.SessionHeader
import app.lumisite.core.model.Site
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only storage: everything lives under the app private files dir, no network needed. */
class Repository(private val ctx: Context) {
    private val root = ctx.filesDir
    private val sitesDir = File(root, "sites").apply { mkdirs() }
    private val sessionsDir = File(root, "sessions").apply { mkdirs() }
    val exportsDir = File(root, "exports").apply { mkdirs() }

    fun saveSite(site: Site) {
        val f = File(sitesDir, "${site.id}.json")
        val tmp = File(sitesDir, "${site.id}.json.tmp")
        tmp.writeText(LumiJson.encodeToString(Site.serializer(), site))
        tmp.renameTo(f)
    }

    fun sites(): List<Site> = sitesDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .mapNotNull { runCatching { LumiJson.decodeFromString(Site.serializer(), it.readText()) }.getOrNull() }
        .sortedByDescending { it.createdUtcMs }

    fun site(id: String): Site? = sites().firstOrNull { it.id == id }

    fun sessionDir(sessionId: String) = File(sessionsDir, sessionId)

    fun sessions(siteId: String): List<SessionHeader> = sessionsDir.listFiles().orEmpty()
        .mapNotNull { d -> File(d, Streams.HEADER).takeIf { it.exists() } }
        .mapNotNull { runCatching { LumiJson.decodeFromString(SessionHeader.serializer(), it.readText()) }.getOrNull() }
        .filter { it.siteId == siteId }
        .sortedByDescending { it.createdUtcMs }

    data class ExportResult(val zip: File, val manifest: SessionManifest, val verification: LoadedExport)

    /** Builds the export zip, then immediately re-reads it to verify hashes and counts. */
    fun export(site: Site, sessionId: String): ExportResult {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val dir = File(exportsDir, "${sessionId}_$stamp")
        val manifest = SessionExporter.exportToDir(site, sessionDir(sessionId), dir, System.currentTimeMillis(), "LumiSite Android ${BuildConfig.VERSION_NAME}")
        val zip = File(exportsDir, "${dir.name}.zip")
        SessionExporter.zipDir(dir, zip)
        dir.deleteRecursively()
        return ExportResult(zip, manifest, SessionImporter.load(zip))
    }

    /** Android 10+ (API 29): copies the zip into the public Downloads collection via MediaStore. */
    fun publishToDownloads(zip: File): Uri {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "MediaStore Downloads requires Android 10+" }
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, zip.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore a refusé la création de ${zip.name}")
        try {
            resolver.openOutputStream(uri)!!.use { out -> zip.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /** Below Android 10: copies the zip to a document chosen by the user (ACTION_CREATE_DOCUMENT). */
    fun copyTo(zip: File, target: Uri) {
        ctx.contentResolver.openOutputStream(target)!!.use { out -> zip.inputStream().use { it.copyTo(out) } }
    }

    /** Current survey records of every session of a site, positioned with the shared position function. */
    fun siteSurveys(site: Site): List<SurveyPositions.Resolved> = sessions(site.id).flatMap { h ->
        runCatching { SurveyPositions.resolveAll(SessionReader.read(sessionDir(h.id), includeImu = false).events) }.getOrDefault(emptyList())
    }

    /** Raw dated OSM responses of a site (offline use and traceability), sites/<id>/osm_source.json. */
    fun saveOsmSource(siteId: String, retrievedUtcMs: Long, endpoint: String, raw: List<Pair<BBox, String>>) {
        val dir = File(sitesDir, siteId).apply { mkdirs() }
        val doc = buildJsonObject {
            put("retrievedUtcMs", retrievedUtcMs); put("endpoint", endpoint)
            put("license", "ODbL 1.0"); put("attribution", "© OpenStreetMap contributors")
            put("responses", buildJsonArray {
                raw.forEach { (b, body) -> add(buildJsonObject {
                    put("bbox", Bboxes.apiParam(b))
                    put("body", runCatching { LumiJson.parseToJsonElement(body) }.getOrElse { JsonPrimitive(body) })
                }) }
            })
        }
        File(dir, "osm_source.json").writeText(doc.toString())
    }

    private val recentFile = File(root, "recent_zones.json")

    fun recentZones(): List<RecentZone> = runCatching {
        LumiJson.decodeFromString(ListSerializer(RecentZone.serializer()), recentFile.readText())
    }.getOrDefault(emptyList())

    fun addRecentZone(z: RecentZone) {
        val l = (listOf(z) + recentZones().filter { it.bbox != z.bbox }).take(10)
        recentFile.writeText(LumiJson.encodeToString(ListSerializer(RecentZone.serializer()), l))
    }
}

@kotlinx.serialization.Serializable
data class RecentZone(val name: String, val bbox: BBox)
