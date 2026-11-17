package app.lumisite.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.text.Normalizer

/** One type of catalog 2 (catalog/lumisite_catalog_2.json, decision DS2 of 15/11): the app keeps only code, family and label. */
@Serializable
data class CatalogEntry(
    /** `LS_*` code, ASCII, ≤ 31 characters, unique; written as the record kind. */
    val code: String,
    /** Former DWG block names (sessions 0.x): translated to [code] at read time, nothing is rewritten. */
    val aliases: List<String> = emptyList(),
    val label: String,
    /** Family code (ECLAIRAGE, ELEC, …). */
    val famille: String,
    @SerialName("sous_famille") val sousFamille: String = "",
    val orientable: Boolean = false,
    @SerialName("details_schema") val detailsSchema: JsonObject = JsonObject(emptyMap()),
    val keywords: List<String> = emptyList(),
    /** false for « Autre (à classer) ». */
    @SerialName("is_block") val isBlock: Boolean = true,
)

@Serializable
data class CatalogFamily(val code: String, val label: String, val color: String, val symbol: String)

@Serializable
data class EquipmentCatalog(
    @SerialName("catalog_version") val version: String,
    val familles: List<CatalogFamily> = emptyList(),
    val entries: List<CatalogEntry>,
) {
    private val index: Map<String, CatalogEntry> by lazy {
        val m = HashMap<String, CatalogEntry>()
        for (e in entries) (listOf(e.code) + e.aliases).forEach { n ->
            val k = key(n)
            require(m.putIfAbsent(k, e) == null || m[k] === e) { "Code ou alias en double dans le catalogue : $n" }
        }
        m
    }

    /** Entry for a code or a former DWG block name (case and Unicode normalisation insensitive), or null. */
    fun find(name: String): CatalogEntry? = index[key(name)]

    /** Like [find] but refuses unknown types. */
    fun resolve(name: String): CatalogEntry = find(name) ?: throw IllegalArgumentException("Type inconnu du catalogue $version : « $name »")

    /** Entry used for records that cannot be classified (legacy kinds, free text). */
    val unclassified: CatalogEntry get() = entries.first { !it.isBlock }

    fun family(code: String): CatalogFamily? = familles.firstOrNull { it.code == code }

    /** Families in display order (« Autres » last), then sub-families and types: Famille › Sous-famille › Type. */
    fun sousFamilles(famille: String): List<String> = entries.filter { it.famille == famille }.map { it.sousFamille }.distinct()
    fun types(famille: String, sousFamille: String): List<CatalogEntry> = entries.filter { it.famille == famille && it.sousFamille == sousFamille }

    /** « Éclairage › Encastré › Downlight encastre au plafond ». */
    fun breadcrumb(e: CatalogEntry): String = listOfNotNull(family(e.famille)?.label ?: e.famille, e.sousFamille.ifBlank { null }, e.label).joinToString(" › ")

    /** Search on code, label, synonyms, aliases and sub-family, accent and case insensitive; label prefix matches first. */
    fun search(query: String): List<CatalogEntry> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { e ->
            val hay = fold((listOf(e.code.replace('_', ' '), e.label, e.sousFamille, family(e.famille)?.label ?: "") + e.keywords + e.aliases).joinToString(" "))
            words.all { it in hay }
        }.sortedBy { if (fold(it.label).startsWith(words.first())) 0 else 1 }
    }

    companion object {
        const val PAYLOAD_SCHEMA = "lumisite-equipment/2"
        const val ASSET = "lumisite_catalog_2.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): EquipmentCatalog = json.decodeFromString(serializer(), text).also { c ->
            require(c.entries.isNotEmpty()) { "Catalogue vide" }
            c.index
        }

        private fun key(s: String) = Normalizer.normalize(s.trim(), Normalizer.Form.NFC).lowercase()

        /** Lowercase, without diacritics, punctuation as spaces: « Encastré » and « encastre » match. */
        fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()

        /** Stable id fragment for test tags: « Amplification et prises » → amplification_et_prises. */
        fun slug(s: String): String = fold(s).replace(' ', '_')
    }
}

/** Mapping of the v0.1 placeholder kinds (LUMI_<KIND>_GENERIC era) to how the office tool should read them. */
object LegacyKinds {
    enum class Target { UNCLASSIFIED, TEXT_ANNOTATION, HEIGHT_OR_TEXT }

    fun target(kind: String): Target? = when (kind.uppercase()) {
        "LUMINAIRE", "AUTRE" -> Target.UNCLASSIFIED
        "OBSERVATION" -> Target.TEXT_ANNOTATION
        "MESURE" -> Target.HEIGHT_OR_TEXT
        else -> null
    }

    fun isLegacyBlock(blockName: String?) = blockName != null && blockName.startsWith("LUMI_") && blockName.endsWith("_GENERIC")
}
