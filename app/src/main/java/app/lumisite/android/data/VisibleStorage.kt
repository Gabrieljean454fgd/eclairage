package app.lumisite.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import app.lumisite.core.journal.Mirror
import java.io.File
import java.io.RandomAccessFile

/**
 * Folder chosen once by the user (Réglages > Stockage, e.g. Documents/LumiSite) holding a copy of sites/ and sessions/
 * that survives uninstalling the app. The private dir stays the working copy; [sync] runs every few seconds.
 */
class VisibleStorage(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("storage", Context.MODE_PRIVATE)
    private val dirs = listOf("sites", "sessions")
    @Volatile var lastError: String? = null; private set
    @Volatile var lastSyncMs: Long = 0; private set

    val treeUri: Uri? get() = prefs.getString("tree", null)?.let(Uri::parse)?.takeIf { u ->
        ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }
    }

    fun setTree(uri: Uri) {
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString("tree", uri.toString()).apply()
    }

    /** Readable path, e.g. "Documents/LumiSite" (primary storage) or the raw document id. */
    fun displayPath(): String? = treeUri?.let { u ->
        DocumentsContract.getTreeDocumentId(u).let { id -> if (id.startsWith("primary:")) id.removePrefix("primary:").ifEmpty { "(racine)" } else id }
    }

    private fun root(): DocumentFile? = treeUri?.let { DocumentFile.fromTreeUri(ctx, it) }

    private fun list(d: DocumentFile, prefix: String, out: MutableList<Pair<Mirror.Entry, DocumentFile>>) {
        d.listFiles().forEach { f ->
            val rel = if (prefix.isEmpty()) f.name ?: return@forEach else "$prefix/${f.name}"
            if (f.isDirectory) list(f, rel, out) else out += Mirror.Entry(rel, f.length()) to f
        }
    }

    private fun docFor(root: DocumentFile, rel: String): DocumentFile {
        val parts = rel.split('/'); var d = root
        parts.dropLast(1).forEach { p -> d = d.findFile(p) ?: d.createDirectory(p) ?: error("Impossible de créer $p") }
        return d.findFile(parts.last()) ?: d.createFile("application/octet-stream", parts.last()) ?: error("Impossible de créer $rel")
    }

    /** Private dir -> visible folder. Never deletes anything. Returns the number of files written. */
    @Synchronized fun sync(privateRoot: File): Int {
        val r = root() ?: return 0
        return runCatching {
            val src = dirs.flatMap { d -> File(privateRoot, d).takeIf { it.exists() }?.let { Mirror.list(it).map { e -> e.copy(rel = "$d/${e.rel}") } } ?: emptyList() }
            val dst = mutableListOf<Pair<Mirror.Entry, DocumentFile>>(); list(r, "", dst)
            val actions = Mirror.plan(src, dst.map { it.first })
            actions.forEach { a ->
                val doc = docFor(r, a.rel); val from = File(privateRoot, a.rel)
                when (a) {
                    is Mirror.Copy -> ctx.contentResolver.openOutputStream(doc.uri, "wt")!!.use { o -> from.inputStream().use { it.copyTo(o) } }
                    is Mirror.Append -> ctx.contentResolver.openOutputStream(doc.uri, "wa")!!.use { o ->
                        RandomAccessFile(from, "r").use { f -> f.seek(a.from); val b = ByteArray(64 * 1024); while (true) { val n = f.read(b); if (n < 0) break; o.write(b, 0, n) } }
                    }
                }
            }
            lastError = null; lastSyncMs = System.currentTimeMillis(); actions.size
        }.getOrElse { lastError = it.message ?: it.toString(); 0 }
    }

    /** Visible folder -> private dir, for files missing or shorter in the private dir (reinstall, migration). */
    @Synchronized fun restore(privateRoot: File): Int {
        val r = root() ?: return 0
        return runCatching {
            val src = mutableListOf<Pair<Mirror.Entry, DocumentFile>>(); list(r, "", src)
            val wanted = src.filter { (e, _) -> dirs.any { e.rel.startsWith("$it/") } }
            val have = dirs.flatMap { d -> File(privateRoot, d).takeIf { it.exists() }?.let { Mirror.list(it).map { e -> e.copy(rel = "$d/${e.rel}") } } ?: emptyList() }
            val missing = have.associate { it.rel to it.size }
            val actions = wanted.filter { (e, _) -> (missing[e.rel] ?: -1) < 0 }
            actions.forEach { (e, doc) ->
                val to = File(privateRoot, e.rel).apply { parentFile?.mkdirs() }
                ctx.contentResolver.openInputStream(doc.uri)!!.use { i -> to.outputStream().use { i.copyTo(it) } }
            }
            actions.size
        }.getOrElse { lastError = it.message ?: it.toString(); 0 }
    }
}
