package app.lumisite.core.journal

/**
 * v176b: copy to Documents. A closed segment never changes, so its SHA-256 is computed once and remembered with its
 * size and modification time; a later sync skips it without reading the file again. A file whose size or date changed
 * is hashed (and copied) again.
 */
class MirrorLedger(done: Map<String, String> = emptyMap(), stamps: Map<String, String> = emptyMap()) {
    private val done = done.toMutableMap()
    private val stamps = stamps.toMutableMap()

    private fun stamp(length: Long, lastModifiedMs: Long) = "$length:$lastModifiedMs"

    /** True when [rel] was copied with this exact size and date: no need to read it again. */
    @Synchronized fun unchanged(rel: String, length: Long, lastModifiedMs: Long): Boolean =
        done.containsKey(rel) && stamps[rel] == stamp(length, lastModifiedMs)

    @Synchronized fun sha(rel: String): String? = done[rel]

    @Synchronized fun record(rel: String, sha: String, length: Long, lastModifiedMs: Long) {
        done[rel] = sha; stamps[rel] = stamp(length, lastModifiedMs)
    }

    /** Only the size/date changed (same content, e.g. re-written identically): remember the new date. */
    @Synchronized fun touch(rel: String, length: Long, lastModifiedMs: Long) { if (done.containsKey(rel)) stamps[rel] = stamp(length, lastModifiedMs) }

    @Synchronized fun doneSnapshot(): Map<String, String> = done.toMap()
    @Synchronized fun stampsSnapshot(): Map<String, String> = stamps.toMap()
    @Synchronized fun size() = done.size
}
