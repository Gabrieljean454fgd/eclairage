package app.lumisite.core.osm

import kotlinx.serialization.Serializable
import java.text.Normalizer
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Retour terrain build 199 (17/11), point 3 — Nouveau site > « Adresse / lieu » : chaque adresse saisie (validée par
 * « Rechercher » ou choisie dans les résultats) est gardée localement et proposée dès la première lettre, hors réseau.
 * Les plus proches de la position actuelle passent en premier. Fonctions pures (stockage JSON fait par l'appli).
 */
@Serializable
data class SavedAddress(val text: String, val lat: Double? = null, val lon: Double? = null, val lastUsedMs: Long = 0, val uses: Int = 1)

object AddressHistory {
    const val MAX_ENTRIES = 200

    /** Minuscules, sans accents ni ponctuation, espaces simples : « 39 Rue D'Alsace » → « 39 rue d alsace ». */
    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Ajoute ou rafraîchit [text] (même texte normalisé = même entrée ; les coordonnées connues sont gardées). */
    fun record(list: List<SavedAddress>, text: String, lat: Double?, lon: Double?, nowMs: Long): List<SavedAddress> {
        val key = normalize(text)
        if (key.isEmpty()) return list
        val old = list.firstOrNull { normalize(it.text) == key }
        val entry = SavedAddress(text.trim(), lat ?: old?.lat, lon ?: old?.lon, nowMs, (old?.uses ?: 0) + 1)
        return (listOf(entry) + list.filter { normalize(it.text) != key }).sortedByDescending { it.lastUsedMs }.take(MAX_ENTRIES)
    }

    /**
     * Suggestions pour la saisie [typed] : chaque mot tapé doit commencer un mot de l'adresse (« 39 rue d » trouve
     * « 39 rue Dupont »). Tri : distance à [nearLat]/[nearLon] si connue (par tranches de 500 m), puis récence.
     */
    fun suggest(list: List<SavedAddress>, typed: String, nearLat: Double? = null, nearLon: Double? = null, limit: Int = 5): List<SavedAddress> {
        val words = normalize(typed).split(' ').filter { it.isNotEmpty() }
        val hits = list.filter { a ->
            val aw = normalize(a.text).split(' ')
            words.isEmpty() || words.all { w -> aw.any { it.startsWith(w) } }
        }
        return hits.sortedWith(compareBy<SavedAddress> { a ->
            if (nearLat == null || nearLon == null || a.lat == null || a.lon == null) Long.MAX_VALUE
            else (distanceM(nearLat, nearLon, a.lat, a.lon) / 500).toLong()
        }.thenByDescending { it.lastUsedMs }).take(limit)
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    /**
     * Paramètres de proximité à ajouter aux recherches en ligne : IGN Géoplateforme (`lat`, `lon`) et Nominatim
     * (`viewbox` ± [boxDeg], sans `bounded` : les résultats proches passent devant sans exclure le reste).
     */
    fun ignProximity(lat: Double, lon: Double): String = "&lat=%.6f&lon=%.6f".format(java.util.Locale.ROOT, lat, lon)

    fun nominatimViewbox(lat: Double, lon: Double, boxDeg: Double = 0.1): String =
        "&viewbox=%.6f,%.6f,%.6f,%.6f".format(java.util.Locale.ROOT, lon - boxDeg, lat + boxDeg, lon + boxDeg, lat - boxDeg)
}
