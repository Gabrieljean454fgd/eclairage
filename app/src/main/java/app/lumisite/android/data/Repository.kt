package app.lumisite.android.data

import android.content.Context
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
}
