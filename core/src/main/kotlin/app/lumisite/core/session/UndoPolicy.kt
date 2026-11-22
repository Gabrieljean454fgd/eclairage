package app.lumisite.core.session

import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.ZoneEvent
import app.lumisite.core.survey.EditHistory
import app.lumisite.core.time.Stamp

/**
 * « Annuler » / « Refaire » (Session > Annoter), règle pure sur le journal append-only : l'effet d'une
 * annulation est un NOUVEL événement qui ré-applique l'état précédent ([supersedes] vers la tête de la
 * chaîne d'édition, [deleted] quand une création n'avait pas d'état avant elle). L'événement annulé
 * n'est jamais réécrit ; la mécanique vit dans [EditHistory], cet objet ajoute la règle de préfixe
 * d'id utilisée par le contrôleur pour nommer l'événement ajouté.
 */
object UndoPolicy {
    /** Préfixe d'id de l'événement d'annulation / rétablissement de [id] : zone, relevé ou forme. */
    fun idPrefix(events: List<SessionEvent>, id: String): String = when (events.firstOrNull { it.id == id }) {
        is ZoneEvent -> "zon"
        is SurveyEvent -> "srv"
        else -> "shp"
    }

    /** Événement à ajouter pour annuler l'action [id] ; null quand il n'y a rien à annuler. */
    fun undo(events: List<SessionEvent>, id: String, newId: String, t: Stamp): SessionEvent? =
        EditHistory.undoRecord(events, id, newId, t)

    /** Événement à ajouter pour rétablir l'action [id] qui vient d'être annulée ; null sinon. */
    fun redo(events: List<SessionEvent>, id: String, newId: String, t: Stamp): SessionEvent? =
        EditHistory.redoRecord(events, id, newId, t)
}
