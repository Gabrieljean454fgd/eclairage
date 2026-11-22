package app.lumisite.core.session

import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.ControlPointRemoved
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.TimeBaseAdded

/**
 * Fenêtre « courante » des points de contrôle (Session > Calibrer / Recaler), règle pure sur le journal
 * append-only : le dernier [TimeBaseAdded] (session reprise sur un nouveau repère AR) ferme la fenêtre ;
 * les points des repères AR antérieurs restent affichés comme « session précédente » sans piloter le
 * calcul. Un [ControlPointRemoved] annule son point à l'intérieur de sa propre fenêtre uniquement —
 * le journal garde les deux événements.
 */
object ControlPointPolicy {
    /** Événements du repère AR courant : tout ce qui suit le dernier [TimeBaseAdded]. */
    fun currentWindow(events: List<SessionEvent>): List<SessionEvent> =
        events.subList(events.indexOfLast { it is TimeBaseAdded } + 1, events.size)

    /** Événements des repères AR précédents ; vide tant que la session n'a jamais été reprise. */
    fun previousWindow(events: List<SessionEvent>): List<SessionEvent> {
        val split = events.indexOfLast { it is TimeBaseAdded }
        return if (split < 0) emptyList() else events.subList(0, split)
    }

    /** Points encore valides dans [window] : un [ControlPointRemoved] de la même fenêtre annule son point. */
    private fun retained(window: List<SessionEvent>): List<ControlPointEvent> {
        val removed = window.filterIsInstance<ControlPointRemoved>().mapTo(HashSet()) { it.pointId }
        return window.filterIsInstance<ControlPointEvent>().filter { it.id !in removed }
    }

    /** Points qui alimentent le calcul de la transformation active. */
    fun active(events: List<SessionEvent>): List<ControlPointEvent> = retained(currentWindow(events))

    /** Points des repères AR précédents (lignes « session précédente », hors calcul). */
    fun previous(events: List<SessionEvent>): List<ControlPointEvent> = retained(previousWindow(events))

    /** Ids à marquer retirés : « Nouveau calibrage » vide tout, « Nouveau recalage » les relocalisations seules. */
    fun idsToClear(events: List<SessionEvent>, relocalizationsOnly: Boolean): List<String> =
        active(events).filter { !relocalizationsOnly || it.kind == "RELOCALIZATION" }.map { it.id }
}
