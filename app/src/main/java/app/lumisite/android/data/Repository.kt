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
import app.lumisite.core.export.ExportPackage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    val publicStore = PublicStore(ctx)

    /**
     * Stage 1.6 result: [zip] is the local copy (sharing), [publicPath] the visible copy in
     * Documents/LumiSite/<site>/<session>/exports/ ([publicUri] to open it), [problems] the streaming re-read.
     */
    data class ExportResult(val zip: File, val manifest: SessionManifest, val problems: List<String>, val publicPath: String?, val publicUri: Uri?)

    /** Builds the export zip in a stream (progress in bytes), re-reads it to verify, then copies it to Documents/LumiSite. */
    fun export(site: Site, sessionId: String, progress: (SessionExporter.Progress) -> Unit = {}): ExportResult {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val dir = File(exportsDir, "${sessionId}_$stamp")
        try {
            val manifest = SessionExporter.exportToDir(site, sessionDir(sessionId), dir, System.currentTimeMillis(), "LumiSite Android ${BuildConfig.VERSION_NAME}", progress)
            val zip = File(exportsDir, "${dir.name}.zip")
            progress(SessionExporter.Progress("Compression ZIP", 0, 1))
            SessionExporter.zipDir(dir, zip)
            dir.deleteRecursively()
            val total = zip.length()
            var done = 0L
            progress(SessionExporter.Progress("Relecture", 0, total * 2))
            val problems = SessionImporter.verify(zip) { done += it; progress(SessionExporter.Progress("Relecture", done, total * 2)) }
            val parts = listOf(PublicStore.siteFolder(site), sessionId, "exports")
            val copied = runCatching { publicStore.copy(zip, parts) }
            progress(SessionExporter.Progress("Copie dans ${PublicStore.displayPath(parts)}", total * 2, total * 2))
            copied.exceptionOrNull()?.let { DiagLog.e("EXPORT", "Copie de ${zip.name} vers ${PublicStore.displayPath(parts)}", it) }
            return ExportResult(zip, manifest, problems, copied.getOrNull()?.displayPath, copied.getOrNull()?.uri)
        } finally { File(exportsDir, "${dir.name}").deleteRecursively() }
    }

    fun mirror(site: Site, sessionId: String, onEvent: (String, Throwable?) -> Unit) = publicStore.SessionMirror(site, sessionDir(sessionId), onEvent)

    data class ReimportResult(val sessionId: String, val path: String, val problems: List<String>, val skipped: String? = null)

    /**
     * « Réglages > Stockage > Réimporter une session depuis Documents/LumiSite »: copies every session found under the
     * folder chosen by the user (ACTION_OPEN_DOCUMENT_TREE) back into the app, verifies it, and restores its site.
     * A session already present locally is not overwritten.
     */
    fun reimport(tree: Uri): List<ReimportResult> = publicStore.findSessions(tree).map { found ->
        val local = sessionDir(found.name)
        if (File(local, Streams.HEADER).exists()) return@map ReimportResult(found.name, found.path, emptyList(), "déjà présente dans l'appli")
        val staging = File(sessionsDir, ".import-${found.name}").apply { deleteRecursively() }
        val problems = runCatching { publicStore.importSession(tree, found, staging) }.getOrElse { listOf("lecture impossible : ${it.message}") }.toMutableList()
        val header = runCatching { LumiJson.decodeFromString(SessionHeader.serializer(), File(staging, Streams.HEADER).readText()) }.getOrNull()
        if (header == null) { staging.deleteRecursively(); return@map ReimportResult(found.name, found.path, problems + "en-tête illisible") }
        val siteFile = File(staging, "site.json")
        if (site(header.siteId) == null) {
            runCatching { saveSite(LumiJson.decodeFromString(Site.serializer(), siteFile.readText())) }.onFailure { problems += "site.json illisible : site non restauré" }
        }
        check(staging.renameTo(sessionDir(header.id))) { "Impossible d'installer la session ${header.id}" }
        ReimportResult(header.id, found.path, problems)
    }

    /** Project package (fiche E): every session of the site as GeoJSONSeq + frames + raw lumisite.session, ZIP "store". */
    fun exportProject(site: Site): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val work = File(exportsDir, "project_${site.id}_$stamp").apply { mkdirs() }
        val catalog = runCatching { ctx.assets.open("equipment_catalog.json").bufferedReader().use { it.readText() } }.getOrNull()
        val root = ExportPackage.build(
            site, sessions(site.id).map { ExportPackage.SessionInput(sessionDir(it.id)) }, catalog,
            File(sitesDir, "${site.id}/osm_source.json"), work, System.currentTimeMillis(), "LumiSite Android ${BuildConfig.VERSION_NAME}",
        )
        val problems = ExportPackage.verify(root)
        check(problems.isEmpty()) { "Vérification du paquet : $problems" }
        val zip = File(exportsDir, "LumiSite_${site.id}_$stamp.zip")
        ExportPackage.zipStored(root, zip)
        work.deleteRecursively()
        return zip
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

    /**
     * Writes the raw dated OSM responses to a pending file right after the fetch (worker thread), so the bodies never
     * live in UI state. Each body is capped at [OsmClient.RAW_MAX] and flagged when truncated.
     */
    fun stageOsmSource(retrievedUtcMs: Long, endpoint: String, raw: List<Pair<BBox, String>>): File {
        val doc = buildJsonObject {
            put("retrievedUtcMs", retrievedUtcMs); put("endpoint", endpoint)
            put("license", "ODbL 1.0"); put("attribution", "© OpenStreetMap contributors")
            put("responses", buildJsonArray {
                raw.forEach { (b, body) -> add(buildJsonObject {
                    put("bbox", Bboxes.apiParam(b))
                    if (body.length > OsmClient.RAW_MAX) {
                        put("truncated", true); put("length", body.length); put("body", JsonPrimitive(body.take(OsmClient.RAW_MAX)))
                    } else put("body", runCatching { LumiJson.parseToJsonElement(body) }.getOrElse { JsonPrimitive(body) })
                }) }
            })
        }
        val dir = File(root, "pending").apply { mkdirs() }
        return File(dir, "osm_source_${System.nanoTime()}.json").apply { writeText(doc.toString()) }
    }

    /** Moves a staged osm_source file to sites/<id>/osm_source.json (call off the main thread). */
    fun saveOsmSource(siteId: String, staged: File) {
        val dir = File(sitesDir, siteId).apply { mkdirs() }
        val dst = File(dir, "osm_source.json")
        if (!staged.renameTo(dst)) { staged.copyTo(dst, overwrite = true); staged.delete() }
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
