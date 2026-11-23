package app.lumisite.core.sync

import app.lumisite.core.export.ExportPackage
import app.lumisite.core.export.ExportPackageManifest
import app.lumisite.core.export.PrettyJson
import app.lumisite.core.journal.LumiJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

/**
 * One synchro run = one folder `files/synchro/<début>/` (master or slave): `synchro.ndjson` (append-only journal),
 * `synchro.json` (this descriptor), `capteurs.csv`, `gps.csv`, `top_*.wav`, `capture_*.wav`, `media/`.
 * The descriptor ties the run to the site and the visit announced by the master, so the slave's own recordings
 * can be exported from the slave and included in the site export ([forSite]).
 */
@Serializable
data class SyncFolderMeta(
    val schema: String = SCHEMA,
    val role: String,
    val device: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("started_utc_ms") val startedUtcMs: Long,
    @SerialName("time_base_id") val timeBaseId: String? = null,
    @SerialName("master_name") val masterName: String? = null,
    @SerialName("site_id") val siteId: String? = null,
    @SerialName("site_name") val siteName: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
) {
    companion object { const val SCHEMA = "lumisite-synchro/1" }
}

object SyncFolder {
    const val META = "synchro.json"
    const val JOURNAL = "synchro.ndjson"
    const val SENSORS = "capteurs.csv"
    const val GPS = "gps.csv"
    const val GPS_HEADER = "t_mono_ns,utc_ms,source,lat,lon,precision_m,alt_m,precision_vert_m\n"

    fun read(dir: File): SyncFolderMeta? =
        runCatching { LumiJson.decodeFromString(SyncFolderMeta.serializer(), File(dir, META).readText()) }.getOrNull()

    /** Atomic (temporary file, then rename): a crash never leaves a half-written descriptor. */
    fun write(dir: File, m: SyncFolderMeta) {
        dir.mkdirs()
        val tmp = File(dir, "$META.tmp")
        tmp.writeText(PrettyJson.encodeToString(SyncFolderMeta.serializer(), m))
        if (!tmp.renameTo(File(dir, META))) { File(dir, META).delete(); check(tmp.renameTo(File(dir, META))) }
    }

    /** Run folders under [root] (`files/synchro`), most recent first. */
    fun runs(root: File): List<File> = root.listFiles { f -> f.isDirectory }.orEmpty().sortedByDescending { it.name }

    /** Run folders tied to [siteId] (master's own runs and slave runs that received this site from the master). */
    fun forSite(root: File, siteId: String): List<File> = runs(root).filter { read(it)?.siteId == siteId }

    /** « synchro.ndjson 12 Ko · capteurs.csv 3,4 Mo · gps.csv 2 Ko · 1 média » for the screen. */
    fun summary(dir: File): String {
        fun size(b: Long) = when { b >= 1_000_000 -> "%.1f Mo".format(java.util.Locale.FRANCE, b / 1e6); b >= 1000 -> "${b / 1000} Ko"; else -> "$b o" }
        val main = listOf(JOURNAL, SENSORS, GPS).mapNotNull { n -> File(dir, n).takeIf { it.exists() }?.let { "$n ${size(it.length())}" } }
        val wav = dir.listFiles { f -> f.name.endsWith(".wav") }.orEmpty().size
        val media = File(dir, "media").listFiles { f -> !f.name.endsWith(".json") }.orEmpty().size
        return (main + listOfNotNull(wav.takeIf { it > 0 }?.let { "$it WAV" }, media.takeIf { it > 0 }?.let { "$it média(s) reçu(s)" })).joinToString(" · ")
    }

    /** Copies run folders into `<root>/synchro/<run>/` (site export and slave export share this layout). */
    fun copyInto(runs: List<File>, root: File) = runs.forEach { r ->
        r.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
            f.copyTo(File(root, "synchro/${r.name}/" + f.relativeTo(r).invariantSeparatorsPath), overwrite = true)
        }
    }

    /**
     * Slave export: same package format as the site export (LumiSite_<site>/ + manifest.json with sizes and SHA-256),
     * with the run folders under synchro/ and no session. Returns the package root (zip it with [ExportPackage.zipStored]).
     */
    fun buildPackage(runs: List<File>, parent: File, nowUtcMs: Long, generator: String): File {
        val meta = runs.firstNotNullOfOrNull { read(it) }
        val id = meta?.siteId ?: "synchro"
        val root = File(parent, "LumiSite_$id").apply { deleteRecursively(); mkdirs() }
        copyInto(runs, root)
        ExportPackage.writeManifest(root, ExportPackageManifest(
            projectId = id, projectName = meta?.siteName ?: "Synchro ${meta?.device ?: ""}".trim(), catalogVersion = null,
            exportedUtc = Instant.ofEpochMilli(nowUtcMs).toString(), generator = generator,
            devices = runs.mapNotNull { read(it)?.deviceId }.distinct(), sessions = emptyList(), files = emptyList(),
        ))
        return root
    }
}
