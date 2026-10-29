package app.lumisite.core.survey

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Optional equipment details (fiche F § 6). Empty fields stay absent (power absent = null, never 0). No size/mirror field. */
object Details {
    val FIELDS = listOf("sous_type", "technologie", "culot", "puissance_W", "temp_couleur_K", "ref_etiquette", "etat", "status")
    val STATUS = listOf("existant", "projeté", "à déposer", "en défaut")
    val ANNOTATIONS = listOf("Passage de câble", "Câble à nu", "En default", "Capteur", "Cablette de terre", "Niveau", "Texte")
    val SUBTYPES = mapOf("spot" to listOf("PAR20", "MR16 dichroïque", "GU10 LED", "LED basique", "autre"))
    const val MAX_POWER_W = 5_000.0
    val CCT_RANGE = 1_000.0..10_000.0

    /** Error message in French, or null if valid. */
    fun validate(field: String, raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return when (field) {
            "puissance_W" -> v.replace(',', '.').toDoubleOrNull().let {
                if (it == null || it <= 0.0 || it > MAX_POWER_W) "Puissance invalide (1 à ${MAX_POWER_W.toInt()} W)" else null
            }
            "temp_couleur_K" -> v.replace(',', '.').toDoubleOrNull().let {
                if (it == null || it !in CCT_RANGE) "Température de couleur invalide (1000 à 10000 K)" else null
            }
            "status" -> if (v in STATUS) null else "Statut inconnu"
            else -> null
        }
    }

    /** Builds the details object from the form, skipping empty values; throws if a value is invalid. */
    fun build(values: Map<String, String>): JsonObject? {
        values.forEach { (k, v) -> validate(k, v)?.let { throw IllegalArgumentException(it) } }
        val o = buildJsonObject {
            values.filterValues { it.isNotBlank() }.filterKeys { it in FIELDS && it != "status" }.forEach { (k, v) ->
                put(k, if (k == "puissance_W" || k == "temp_couleur_K") JsonPrimitive(v.trim().replace(',', '.').toDouble()) else JsonPrimitive(v.trim()))
            }
        }
        return o.takeIf { it.isNotEmpty() }
    }
}
