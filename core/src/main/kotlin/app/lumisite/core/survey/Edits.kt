package app.lumisite.core.survey

import app.lumisite.core.catalog.CatalogEntry
import app.lumisite.core.model.Attachment

/** Pure rules of Session > Relevé > Détail (v176a). */
object SurveyEdits {
    /** [edited] null = comment not edited (kept); blank = cleared by the user (removed). */
    fun note(old: String?, edited: String?): String? = if (edited == null) old else edited.ifBlank { null }

    /** Number of attachments per role: "etiquette", "vue", "voix" (role-less audio counts as "voix", role-less image as "vue"). */
    fun attachmentCounts(attachments: List<Attachment>): Map<String, Int> =
        attachments.groupingBy { it.role ?: if (it.mediaType.startsWith("audio/")) "voix" else "vue" }.eachCount()

    /** « Éclairage › spot » for the current family and type of a record. */
    fun familyPath(entry: CatalogEntry?, kind: String): String =
        entry?.let { "${it.famille.replaceFirstChar(Char::uppercaseChar)} › ${it.label}" } ?: kind
}
