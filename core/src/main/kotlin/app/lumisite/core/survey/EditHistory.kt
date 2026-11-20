package app.lumisite.core.survey

import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.ZoneEvent
import app.lumisite.core.time.Stamp

/**
 * « Annuler » / « Refaire » sur le journal append-only (Session > Annoter).
 * Rien n'est réécrit : chaque annulation ou rétablissement ajoute un événement qui remplace la tête
 * de la chaîne d'édition de l'objet ([Shapes.head]). Ces fonctions calculent l'événement à ajouter ;
 * c'est l'appelant qui choisit l'id et l'horodatage.
 */
object EditHistory {
    private fun previousShape(events: List<SessionEvent>, e: ShapeEvent) =
        e.supersedes?.let { p -> events.firstOrNull { it.id == p } as? ShapeEvent }

    private fun previousZone(events: List<SessionEvent>, e: ZoneEvent) =
        e.supersedes?.let { p -> events.firstOrNull { it.id == p } }
            ?.let { (it as? ZoneEvent) ?: (it as? ShapeEvent)?.let(ZoneEvent::of) }

    private fun previousSurvey(events: List<SessionEvent>, e: SurveyEvent) =
        e.supersedes?.let { p -> events.firstOrNull { it.id == p } as? SurveyEvent }

    /**
     * Événement à ajouter pour annuler l'action enregistrée sous [id] : le contenu de la version
     * précédente (ou la suppression logique quand [id] était une création). null = rien à annuler.
     */
    fun undoRecord(events: List<SessionEvent>, id: String, newId: String, t: Stamp): SessionEvent? {
        val done = events.firstOrNull { it.id == id } ?: return null
        val head = Shapes.head(events, id)
        return when (done) {
            is ShapeEvent -> {
                val prev = previousShape(events, done)
                (prev ?: done).copy(id = newId, t = t, supersedes = head, deleted = if (prev == null) true else prev.deleted)
            }
            is ZoneEvent -> {
                val prev = previousZone(events, done)
                (prev ?: done).copy(id = newId, t = t, supersedes = head, deleted = if (prev == null) true else prev.deleted)
            }
            is SurveyEvent -> {
                val prev = previousSurvey(events, done)
                (prev ?: done).copy(id = newId, t = t, supersedes = head, deleted = if (prev == null) true else prev.deleted)
            }
            else -> null
        }
    }

    /**
     * Événement à ajouter pour rétablir l'action [id] qui vient d'être annulée : son contenu est
     * re-appliqué au sommet de la chaîne (une suppression annulée redevient une suppression).
     */
    fun redoRecord(events: List<SessionEvent>, id: String, newId: String, t: Stamp): SessionEvent? {
        val done = events.firstOrNull { it.id == id } ?: return null
        val head = Shapes.head(events, id)
        return when (done) {
            is ShapeEvent -> done.copy(id = newId, t = t, supersedes = head)
            is ZoneEvent -> done.copy(id = newId, t = t, supersedes = head)
            is SurveyEvent -> done.copy(id = newId, t = t, supersedes = head)
            else -> null
        }
    }
}
