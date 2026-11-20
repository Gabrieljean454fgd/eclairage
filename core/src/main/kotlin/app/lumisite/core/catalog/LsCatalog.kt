package app.lumisite.core.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

/**
 * Catalogue léger LS_* (v176e, DS2 / PLAN_V176 E1-E3, menu-bloc : catalogue 2.0 complet).
 *
 * L'appli manipule des codes LS_* et leurs libellés. [blocDwg] et [calque] sont les correspondances
 * bureau (table LS→DWG) ; [aliases] porte les anciens noms du catalogue 1.0 pour relire les relevés
 * existants ; [symbole] désigne un glyphe de [LsSymbols] ; [isBlock] = false pour les pseudo-blocs
 * (« à classer », LS_PERSO_*).
 *
 * Ce fichier est indépendant de Catalog.kt / equipment_catalog.json.
 */
@Serializable
data class LsItem(
    val code: String,
    val label: String,
    val famille: String,
    val sousFamille: String? = null,
    val type: String,
    val synonyms: List<String> = emptyList(),
    /** Calque bureau (LUMI-ELEC, LUMI-ECLAIRAGE, …) ; utilisé pour le hint DXF. */
    val calque: String? = null,
    /** Nom corrigé du bloc DWG de la table LS→DWG, null si pas encore de bloc déterminé. */
    @SerialName("bloc_dwg") val blocDwg: String? = null,
    /** Anciens noms (catalogue 1.0, block_name + alias) : permet de relire les relevés existants. */
    val aliases: List<String> = emptyList(),
    /** Identifiant de glyphe dans [LsSymbols] (null → symbole « autre »). */
    val symbole: String? = null,
    /** false pour « autre (à classer) » et les types LS_PERSO_* créés sur le terrain. */
    @SerialName("is_block") val isBlock: Boolean = true,
)

/** Erreur de chargement ou de validation du catalogue LS_*. */
class LsCatalogException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

@Serializable
data class LsCatalog(
    val version: String,
    val items: List<LsItem>,
) {

    // ---------------------------------------------------------------- lookup

    fun byCode(code: String): LsItem? = items.firstOrNull { it.code == code }

    /** Résout une liste de codes (favoris, récents) en items, dans l'ordre, codes inconnus ignorés. */
    fun resolve(codes: List<String>): List<LsItem> = codes.mapNotNull { byCode(it) }

    // Anciens noms (catalogue 1.0) → code LS_* : index normalisé sur bloc_dwg + aliases.
    private val legacyIndex: Map<String, LsItem> by lazy {
        val m = HashMap<String, LsItem>()
        for (i in items) for (n in listOfNotNull(i.blocDwg) + i.aliases) m.putIfAbsent(LsText.normalize(n), i)
        m
    }

    /** Item pour un ancien nom de bloc (block_name ou alias du catalogue 1.0), insensible casse/accents. */
    fun byLegacyName(name: String): LsItem? = legacyIndex[LsText.normalize(name)]

    /** Code LS_* direct, sinon ancien nom résolu (relevés existants du catalogue 1.0). */
    fun byCodeOrLegacy(id: String): LsItem? = byCode(id) ?: byLegacyName(id)

    // ---------------------------------------------------------------- search

    /**
     * Recherche insensible à la casse et aux accents sur code, libellé, type et synonymes.
     * Plusieurs mots = tous doivent être trouvés (ET).
     * Requête vide = tous les items, dans l'ordre du catalogue (aucun réordonnancement ici).
     */
    fun search(query: String): List<LsItem> {
        val tokens = LsText.normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return items
        return items.filter { item ->
            val haystack = buildList {
                add(item.code)
                add(item.code.replace('_', ' '))
                add(item.label)
                add(item.type)
                addAll(item.synonyms)
                addAll(item.aliases)
            }.map { LsText.normalize(it) }
            tokens.all { token -> haystack.any { it.contains(token) } }
        }
    }

    // ------------------------------------------------------------ navigation

    /** Familles uniques, ordre d'apparition dans le catalogue, « Autres » toujours en dernier. */
    fun families(): List<String> =
        items.map { it.famille }.distinct().sortedBy { if (isOtherFamily(it)) 1 else 0 }

    /** Sous-familles uniques d'une famille (null ignorées), « Autres » en dernier. */
    fun sousFamilles(famille: String): List<String> =
        items.asSequence()
            .filter { it.famille == famille }
            .mapNotNull { it.sousFamille?.takeIf { s -> s.isNotBlank() } }
            .distinct()
            .toList()
            .sortedBy { if (LsText.isOtherLabel(it)) 1 else 0 }

    /**
     * Types (items) d'une famille / sous-famille.
     * sousFamille = null : items de la famille sans sous-famille.
     */
    fun types(famille: String, sousFamille: String?): List<LsItem> =
        items.filter {
            it.famille == famille &&
                (it.sousFamille?.takeIf { s -> s.isNotBlank() }) == sousFamille?.takeIf { s -> s.isNotBlank() }
        }

    /** Vrai si la famille est la famille « Autres » (nom AUTRE/Autres ou uniquement des codes LS_AUTRE_*). */
    fun isOtherFamily(famille: String): Boolean {
        if (LsText.isOtherLabel(famille)) return true
        val inFamily = items.filter { it.famille == famille }
        return inFamily.isNotEmpty() && inFamily.all { it.code.startsWith(OTHER_PREFIX) }
    }

    fun breadcrumb(item: LsItem): String = breadcrumb(item.famille, item.sousFamille, item.type)

    // ------------------------------------------------------------ validation

    /** Liste des erreurs de validation (vide = catalogue valide). */
    fun errors(): List<String> {
        val out = mutableListOf<String>()
        if (version.isBlank()) out += "version vide"
        val seen = HashSet<String>()
        items.forEachIndexed { i, item ->
            codeError(item.code)?.let { out += "item[$i] « ${item.code} » : $it" }
            if (!seen.add(item.code)) out += "item[$i] « ${item.code} » : code en double"
            if (item.label.isBlank()) out += "item[$i] « ${item.code} » : libellé vide"
            if (item.famille.isBlank()) out += "item[$i] « ${item.code} » : famille vide"
            if (item.type.isBlank()) out += "item[$i] « ${item.code} » : type vide"
        }
        return out
    }

    /** Lève [LsCatalogException] si le catalogue est invalide, sinon renvoie this. */
    fun validated(): LsCatalog {
        val errs = errors()
        if (errs.isNotEmpty()) throw LsCatalogException("Catalogue LS invalide :\n- " + errs.joinToString("\n- "))
        return this
    }

    companion object {
        const val PREFIX = "LS_"
        const val OTHER_PREFIX = "LS_AUTRE_"
        /** Types créés sur le terrain (« Ajouter un type… ») : jamais de bloc DWG, à classer au bureau. */
        const val PERSO_PREFIX = "LS_PERSO_"
        const val MAX_CODE_LENGTH = 31
        const val BREADCRUMB_SEPARATOR = " › "

        /** Libellés d'affichage des familles du catalogue 2.0 (ids ASCII → français). */
        val FAMILY_LABELS = mapOf(
            "ECLAIRAGE" to "Éclairage", "ELEC" to "Électricité", "SECOURS" to "Secours",
            "SECURITE" to "Sécurité", "INCENDIE" to "Incendie", "CAMERA" to "Caméra et sûreté",
            "SON" to "Sonorisation", "AUTRE" to "Autre",
        )

        fun familyLabel(famille: String): String = FAMILY_LABELS[famille] ?: famille

        /** Prochain code LS_PERSO_<n> libre (n = max existant + 1, jamais réutilisé). */
        fun nextPersonalCode(items: List<LsItem>): String {
            val max = items.mapNotNull { it.code.removePrefix(PERSO_PREFIX).toIntOrNull() }.maxOrNull() ?: 0
            return PERSO_PREFIX + (max + 1)
        }

        /** ASCII uniquement : majuscules, chiffres, underscore, préfixe LS_. */
        private val CODE_REGEX = Regex("^LS_[A-Z0-9_]+$")

        private val lsJson = Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

        /** Parse + validation. Toute erreur (JSON ou règle de code) lève [LsCatalogException]. */
        fun parse(text: String): LsCatalog {
            val catalog = try {
                lsJson.decodeFromString<LsCatalog>(text)
            } catch (e: IllegalArgumentException) { // inclut SerializationException
                throw LsCatalogException("JSON catalogue LS illisible : ${e.message}", e)
            }
            return catalog.validated()
        }

        /** null si le code est valide, sinon le motif du refus. */
        fun codeError(code: String): String? = when {
            code.isEmpty() -> "code vide"
            code.any { it.code > 0x7F } -> "caractère non ASCII"
            !code.startsWith(PREFIX) -> "préfixe $PREFIX manquant"
            code.length > MAX_CODE_LENGTH -> "longueur ${code.length} > $MAX_CODE_LENGTH"
            !CODE_REGEX.matches(code) -> "caractères autorisés : A-Z 0-9 _"
            else -> null
        }

        fun isValidCode(code: String): Boolean = codeError(code) == null

        /** « Éclairage › Encastré › Downlight » ; sous-famille vide/null omise. */
        fun breadcrumb(famille: String, sousFamille: String?, type: String?): String =
            listOfNotNull(
                famille.takeIf { it.isNotBlank() },
                sousFamille?.takeIf { it.isNotBlank() },
                type?.takeIf { it.isNotBlank() },
            ).joinToString(BREADCRUMB_SEPARATOR)
    }
}

/** Outils texte du catalogue LS (dans un objet pour éviter tout conflit avec Catalog.kt). */
object LsText {
    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("[\\s\\-'’.,/]+")

    /** Minuscules, sans accents, ponctuation légère → espace, espaces compactés. */
    fun normalize(s: String): String {
        val lowered = s.lowercase(Locale.ROOT)
            .replace("œ", "oe")
            .replace("æ", "ae")
        val stripped = MARKS.replace(Normalizer.normalize(lowered, Normalizer.Form.NFD), "")
        return SPACES.replace(stripped, " ").trim()
    }

    fun isOtherLabel(s: String): Boolean = normalize(s) in setOf("autre", "autres")
}

/** Favoris / récents : opérations pures sur des listes de codes (persistance hors de ce fichier). */
object LsRecents {
    const val MAX = 12

    /** Place [code] en tête, sans doublon, tronque à [max]. */
    fun push(recents: List<String>, code: String, max: Int = MAX): List<String> =
        (listOf(code) + recents.filter { it != code }).take(max)

    /** Ajoute ou retire [code] des favoris (ordre conservé, ajout en fin). */
    fun toggleFavorite(favorites: List<String>, code: String): List<String> =
        if (code in favorites) favorites - code else favorites + code
}
