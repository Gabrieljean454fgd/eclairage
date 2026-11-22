package app.lumisite.core.journal

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Copy of the session tree kept in a user-visible folder that survives uninstalling the app (Documents/LumiSite).
 * The private files dir stays the working copy (fsync, append-only); the mirror is brought up to date by size:
 * append-only streams (.ndjson, .geojsonl) get only their new bytes, other files are rewritten when they change.
 * Nothing is ever deleted on either side.
 */
object Mirror {
    data class Entry(val rel: String, val size: Long)

    sealed interface Action { val rel: String }
    data class Copy(override val rel: String) : Action
    data class Append(override val rel: String, val from: Long) : Action

    fun appendOnly(rel: String) = rel.endsWith(".ndjson") || rel.endsWith(".geojsonl")

    /** Actions bringing [dst] up to date with [src]. A destination larger than the source is left alone (never truncated). */
    fun plan(src: List<Entry>, dst: List<Entry>): List<Action> {
        val have = dst.associate { it.rel to it.size }
        return src.mapNotNull { e ->
            val d = have[e.rel]
            when {
                d == null -> Copy(e.rel)
                d == e.size -> null
                appendOnly(e.rel) && d < e.size -> Append(e.rel, d)
                appendOnly(e.rel) -> null
                else -> Copy(e.rel)
            }
        }
    }

    fun list(root: File): List<Entry> =
        root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.map { Entry(it.relativeTo(root).invariantSeparatorsPath, it.length()) }.toList()

    /** File-to-file mirror (JVM tests, and restore into the private dir). Returns the number of actions applied. */
    fun sync(src: File, dst: File): Int {
        val actions = plan(list(src), if (dst.exists()) list(dst) else emptyList())
        actions.forEach { a ->
            val from = File(src, a.rel); val to = File(dst, a.rel).apply { parentFile.mkdirs() }
            when (a) {
                is Copy -> { val tmp = File(to.path + ".tmp"); from.copyTo(tmp, overwrite = true); tmp.renameTo(to) }
                is Append -> RandomAccessFile(from, "r").use { r ->
                    r.seek(a.from)
                    FileOutputStream(to, true).use { o -> val b = ByteArray(64 * 1024); while (true) { val n = r.read(b); if (n < 0) break; o.write(b, 0, n) }; o.fd.sync() }
                }
            }
        }
        return actions.size
    }
}
