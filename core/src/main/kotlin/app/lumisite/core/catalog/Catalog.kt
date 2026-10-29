package app.lumisite.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.text.Normalizer

/** One family of the closed equipment list (catalog/equipment_catalog.json, decision D5). */
@Serializable
data class CatalogEntry(
    @SerialName("block_name") val blockName: String,
    val aliases: List<String> = emptyList(),
    val label: String,
    val famille: String,
    @SerialName("default_layer") val defaultLayer: String,
    val orientable: Boolean = false,
    @SerialName("scale_rules") val scaleRules: JsonObject = JsonObject(emptyMap()),
    @SerialName("details_schema") val detailsSchema: JsonObject = JsonObject(emptyMap()),
    val icon: String? = null,
    val keywords: List<String> = emptyList(),
    /** Proposed canonical name not yet validated: never written, informative only. */
    @SerialName("pending_rename") val pendingRename: String? = null,
    /** false for « autre (à classer) », which matches no DWG block. */
    @SerialName("is_block") val isBlock: Boolean = true,
)

@Serializable
data class EquipmentCatalog(
    @SerialName("catalog_version") val version: String,
    val entries: List<CatalogEntry>,
) {
    private val index: Map<String, CatalogEntry> by lazy {
        val m = HashMap<String, CatalogEntry>()
        for (e in entries) (listOf(e.blockName) + e.aliases).forEach { n ->
            val k = key(n)
            require(m.putIfAbsent(k, e) == null || m[k] === e) { "Nom ou alias en double dans le catalogue : $n" }
        }
        m
    }

    /** Canonical entry for a block name or one of its aliases (case and Unicode normalisation insensitive), or null. */
    fun find(name: String): CatalogEntry? = index[key(name)]

    /** Like [find] but refuses unknown families. */
    fun resolve(name: String): CatalogEntry = find(name) ?: throw IllegalArgumentException("Famille inconnue du catalogue $version : « $name »")

    /** Entry used for records that cannot be classified (legacy kinds, free text). */
    val unclassified: CatalogEntry get() = entries.first { !it.isBlock }

    companion object {
        const val PAYLOAD_SCHEMA = "lumisite-equipment/1"
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): EquipmentCatalog = json.decodeFromString(serializer(), text).also { c ->
            require(c.entries.isNotEmpty()) { "Catalogue vide" }
            c.index
        }

        private fun key(s: String) = Normalizer.normalize(s.trim(), Normalizer.Form.NFC).lowercase()
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
