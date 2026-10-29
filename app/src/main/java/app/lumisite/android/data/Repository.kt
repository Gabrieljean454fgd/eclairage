package app.lumisite.android.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.lumisite.android.BuildConfig
import app.lumisite.core.export.LoadedExport
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
}
