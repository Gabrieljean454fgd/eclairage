package app.lumisite.core.osm

import java.text.Normalizer
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nouveau site > « Adresse / lieu » : validated addresses kept on the phone (recent_addresses.json) and offered from the first
 * letter, without network. Each typed word must start a word of the address; nearest first (500 m bands), then most recent.
 */
object AddressHistory {
    const val MAX = 200
    const val BAND_M = 500.0

    data class Entry(val text: String, val lat: Double? = null, val lon: Double? = null, val usedMs: Long = 0L)

    /** Lower case, no accents, punctuation as spaces, single spaces. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Adds or refreshes [text] at the head (same normalized text = same address, coordinates kept if the new ones are unknown). */
    fun record(history: List<Entry>, text: String, lat: Double?, lon: Double?, nowMs: Long): List<Entry> {
        val t = text.trim()
        if (normalize(t).isEmpty()) return history
        val key = normalize(t)
        val old = history.firstOrNull { normalize(it.text) == key }
        val e = Entry(t, lat ?: old?.lat, lon ?: old?.lon, nowMs)
        return (listOf(e) + history.filter { normalize(it.text) != key }).take(MAX)
    }

    /** Every typed word starts a word of the address (« 39 rue D » → « 39 rue Dupont »). Empty query: nothing. */
    fun matches(entry: Entry, query: String): Boolean {
        val q = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (q.isEmpty()) return false
        val words = normalize(entry.text).split(' ')
        return q.all { w -> words.any { it.startsWith(w) } }
    }

    fun suggest(history: List<Entry>, query: String, hereLat: Double?, hereLon: Double?, limit: Int = 6): List<Entry> =
        history.filter { matches(it, query) }
            .sortedWith(compareBy<Entry> { band(it, hereLat, hereLon) }.thenByDescending { it.usedMs })
            .take(limit)

    /** 500 m band index from the current position; unknown position or coordinates go after the known ones. */
    fun band(e: Entry, hereLat: Double?, hereLon: Double?): Int {
        if (hereLat == null || hereLon == null || e.lat == null || e.lon == null) return Int.MAX_VALUE
        return (distanceM(hereLat, hereLon, e.lat, e.lon) / BAND_M).toInt()
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(a))
    }

    /** IGN geocoder bias towards the position: « &lat=…&lon=… » ("" if unknown). */
    fun ignProximity(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "" else "&lat=%.5f&lon=%.5f".format(Locale.ROOT, lat, lon)

    /** Nominatim preference box of ± 0.1° around the position, without `bounded` (results outside stay allowed). */
    fun nominatimViewbox(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) "" else "&viewbox=%.5f,%.5f,%.5f,%.5f".format(Locale.ROOT, lon - 0.1, lat + 0.1, lon + 0.1, lat - 0.1)
}
