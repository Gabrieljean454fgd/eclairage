package app.lumisite.android.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.lumisite.core.export.sha256
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.model.Site
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Stage 1.2: visible copy in Documents/LumiSite/ that survives uninstallation. Android 10+ writes through MediaStore
 * (Files collection, Documents/ relative path): no storage permission is needed to create these files. Android 9 and
 * below write the public directory directly (WRITE_EXTERNAL_STORAGE, maxSdkVersion 28).
 * Every copy is read back and compared by SHA-256 before it is counted as done.
 */
class PublicStore(private val ctx: Context) {
    companion object {
        const val ROOT = "LumiSite"
        const val INDEX = "copies.json"
        fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(40).ifEmpty { "site" }
        fun siteFolder(site: Site) = "${safe(site.name)}_${site.id}"
        fun displayPath(parts: List<String>) = (listOf("Documents", ROOT) + parts).joinToString("/")
        private val indexSer = MapSerializer(String.serializer(), String.serializer())
    }

    data class Copied(val uri: Uri, val sha256: String, val displayPath: String)

    private fun relativePath(parts: List<String>) = (listOf(Environment.DIRECTORY_DOCUMENTS, ROOT) + parts).joinToString("/") + "/"

    private fun mimeOf(name: String) = when {
        name.endsWith(".json") -> "application/json"
        name.endsWith(".zip") -> "application/zip"
        name.endsWith(".txt") -> "text/plain"
        name.endsWith(".jpg") -> "image/jpeg"
        name.endsWith(".m4a") -> "audio/mp4"
        name.endsWith(".wav") -> "audio/x-wav"
        else -> "application/octet-stream"
    }

    /** Copies [src] to Documents/LumiSite/<parts>/<name> (replacing an earlier copy made by this install) and verifies it. */
    fun copy(src: File, parts: List<String>, name: String = src.name): Copied {
        val expected = sha256(src)
        return src.inputStream().use { write(it, parts, name, expected) }
    }

    fun writeText(text: String, parts: List<String>, name: String): Copied {
        val bytes = text.toByteArray()
        return write(bytes.inputStream(), parts, name, app.lumisite.core.export.sha256(bytes))
    }

    private fun write(input: InputStream, parts: List<String>, name: String, expected: String): Copied {
        val display = displayPath(parts + name)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), (listOf(ROOT) + parts).joinToString("/")).apply { mkdirs() }
            val tmp = File(dir, "$name.tmp")
            tmp.outputStream().use { input.copyTo(it, 64 * 1024) }
            val dst = File(dir, name); dst.delete(); check(tmp.renameTo(dst)) { "Copie impossible : $display" }
            val got = sha256(dst)
            check(got == expected) { "Empreinte différente après copie : $display" }
            return Copied(Uri.fromFile(dst), got, display)
        }
        val resolver = ctx.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val rel = relativePath(parts)
        val existing = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(rel, name), null)?.use { c ->
            if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }
        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: error("MediaStore a refusé la création de $display")
        try {
            resolver.openOutputStream(uri, "wt")!!.use { out -> input.copyTo(out, 64 * 1024) }
            if (existing == null) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            val got = resolver.openInputStream(uri)!!.use { sha256(it) }
            check(got == expected) { "Empreinte différente après copie : $display" }
            return Copied(uri, got, display)
        } catch (e: Exception) {
            if (existing == null) runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    /**
     * Copies session files to Documents/LumiSite/<site>/<session>/ on one background thread, as soon as they are final
     * (finished journal segments, attachments, header). `mirror_state.json` (local) remembers what is already copied;
     * `copies.json` (public) lists path → SHA-256 so that a reimport can verify every file.
     */
    inner class SessionMirror(private val site: Site, private val sessionDir: File, private val onEvent: (String, Throwable?) -> Unit) {
        private val exec: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "mirror").apply { isDaemon = true } }
        private val stateFile = File(sessionDir, "mirror_state.json")
        private val done: MutableMap<String, String> = runCatching { LumiJson.decodeFromString(indexSer, stateFile.readText()).toMutableMap() }.getOrDefault(mutableMapOf())
        val parts = listOf(siteFolder(site), sessionDir.name)
        val displayDir get() = displayPath(parts)
        @Volatile var failures = 0; private set
        val copiedCount get() = synchronized(done) { done.size }

        fun enqueue(file: File) { exec.execute { copyOne(file) } }

        private fun copyOne(file: File) {
            if (!file.isFile) return
            val rel = file.relativeTo(sessionDir).invariantSeparatorsPath
            try {
                val sha = sha256(file)
                if (synchronized(done) { done[rel] } == sha) return
                val sub = rel.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }
                copy(file, parts + sub, file.name)
                val snapshot = synchronized(done) { done[rel] = sha; done.toMap() }
                File(sessionDir, "mirror_state.json.tmp").apply { writeText(LumiJson.encodeToString(indexSer, snapshot)); check(renameTo(stateFile)) }
                writeText(LumiJson.encodeToString(indexSer, snapshot), parts, INDEX)
            } catch (e: Throwable) {
                failures++
                onEvent("Copie vers $displayDir/$rel impossible", e)
            }
        }

        /** Queues every final file of the session (segments already closed, attachments, header, site). */
        fun syncAll() {
            File(sessionDir, "site.json").apply { if (!exists()) writeText(LumiJson.encodeToString(Site.serializer(), site)) }
            val files = sessionDir.walkTopDown().filter { f ->
                f.isFile && !f.name.endsWith(".part") && !f.name.endsWith(".tmp") && f.name != "mirror_state.json" && f.name != INDEX &&
                    !f.path.contains("/capture/")
            }.toList()
            files.forEach(::enqueue)
        }

        fun drain(timeoutMs: Long = 30_000): Boolean {
            val latch = java.util.concurrent.CountDownLatch(1)
            exec.execute { latch.countDown() }
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    // ------------------------------------------------------------------ reimport (after reinstall)

    data class TreeEntry(val docId: String, val name: String, val isDir: Boolean)

    private fun children(tree: Uri, docId: String): List<TreeEntry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return ctx.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(TreeEntry(c.getString(0), c.getString(1), c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)) }
        }.orEmpty()
    }

    data class FoundSession(val docId: String, val name: String, val path: String)

    /** Session folders (containing session_header.json) found under the folder chosen with ACTION_OPEN_DOCUMENT_TREE. */
    fun findSessions(tree: Uri): List<FoundSession> {
        val out = ArrayList<FoundSession>()
        fun walk(docId: String, path: String, depth: Int) {
            val kids = children(tree, docId)
            if (kids.any { !it.isDir && it.name == app.lumisite.core.journal.Streams.HEADER }) { out += FoundSession(docId, path.substringAfterLast('/'), path); return }
            if (depth < 3) kids.filter { it.isDir && it.name != "_journal" && it.name != "exports" }.forEach { walk(it.docId, "$path/${it.name}", depth + 1) }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        return out
    }

    /**
     * Copies a session folder into [dest] (empty) and checks every file against copies.json. Returns the problems
     * (missing or different files); an empty list means a complete, verified copy.
     */
    fun importSession(tree: Uri, s: FoundSession, dest: File): List<String> {
        val problems = ArrayList<String>()
        fun walk(docId: String, dir: File) {
            for (k in children(tree, docId)) {
                if (k.isDir) { walk(k.docId, File(dir, k.name)); continue }
                dir.mkdirs()
                val target = File(dir, k.name)
                ctx.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, k.docId))!!.use { input ->
                    target.outputStream().use { input.copyTo(it, 64 * 1024) }
                }
            }
        }
        walk(s.docId, dest)
        val index = runCatching { LumiJson.decodeFromString(indexSer, File(dest, INDEX).readText()) }.getOrNull()
        if (index == null) problems += "$INDEX absent : copie non vérifiable"
        index?.forEach { (rel, sha) ->
            val f = File(dest, rel)
            if (!f.isFile) problems += "$rel manquant" else if (sha256(f) != sha) problems += "$rel : empreinte différente"
        }
        File(dest, INDEX).delete()
        index?.let { File(dest, "mirror_state.json").writeText(LumiJson.encodeToString(indexSer, it)) }
        return problems
    }
}
