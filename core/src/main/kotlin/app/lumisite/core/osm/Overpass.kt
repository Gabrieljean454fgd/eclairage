package app.lumisite.core.osm

import app.lumisite.core.model.BBox

/** Overpass fallback (fiche C): used only when the OSM API v0.6 fails (network, 429, 509, 5xx). */
object Overpass {
    val MIRRORS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )
    const val TIMEOUT_S = 25
    const val MAX_ELEMENTS = 500
    val BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L)

    /** Query on the site rectangle, with server timeout and element cap. */
    fun query(b: BBox, maxElements: Int = MAX_ELEMENTS, timeoutS: Int = TIMEOUT_S): String {
        val bb = "%.7f,%.7f,%.7f,%.7f".format(java.util.Locale.ROOT, b.south, b.west, b.north, b.east)
        return """[out:json][timeout:$timeoutS];
(
  way["building"]($bb);
  relation["building"]($bb);
  way["building:part"]($bb);
  way["indoor"]($bb);
  node["highway"="street_lamp"]($bb);
  node["man_made"="street_lamp"]($bb);
  node["entrance"]($bb);
);
out geom tags $maxElements;"""
    }

    /** Should the OSM API failure fall back to Overpass? */
    fun shouldFallback(e: Throwable): Boolean = when (e) {
        is OsmHttpException -> e.failure == OsmGuard.Failure.RATE_LIMITED || e.code >= 500
        is CallCapReached -> false
        is java.io.IOException -> true
        else -> false
    }
}

/** HTTP POST of a form body, one request. Interface so the retry logic is testable without network. */
fun interface FormPoster { fun post(url: String, form: String): String }

/** Mirrors in order, [attempts] tries each, waiting 1 s, 2 s then 4 s between tries. */
class MirrorFetcher(
    private val poster: FormPoster,
    private val mirrors: List<String> = Overpass.MIRRORS,
    private val attempts: Int = 2,
    private val backoffMs: List<Long> = Overpass.BACKOFF_MS,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val onCall: (Int) -> Unit = {},
) {
    init { require(mirrors.all { it.startsWith("https://") }) { "Miroirs Overpass : https:// obligatoire" } }

    val tried = ArrayList<String>()

    fun fetch(form: String): Pair<String, String> {
        var last: Exception? = null
        var waitIdx = 0
        for (m in mirrors) repeat(attempts) {
            if (tried.isNotEmpty()) sleep(backoffMs[minOf(waitIdx++, backoffMs.lastIndex)])
            tried += m
            onCall(tried.size)
            try { return m to poster.post(m, form) } catch (e: Exception) { last = e }
        }
        throw java.io.IOException("Overpass indisponible sur ${mirrors.size} miroirs (${tried.size} essais)", last)
    }
}
