package app.lumisite.core

import app.lumisite.core.geo.Enu
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.ControlPointRemoved
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.TimeBaseAdded
import app.lumisite.core.session.ControlPointPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Points de contrôle « actifs » (Session > Calibrer / Recaler) : fenêtre courante, suppressions, vides. */
class ControlPointPolicyTest {
    private var seq = 0
    private fun at() = Fixtures.stamp(++seq * 1_000_000_000L)

    private fun point(id: String, kind: String = "CALIBRATION") = ControlPointEvent(
        id, at(), kind,
        LocalObservation(null, ReferencePoint("repère $id", Enu(seq.toDouble(), 0.0), source = "MAP_TAP")))

    private fun removal(id: String, pointId: String) = ControlPointRemoved(id, at(), pointId)

    private fun resume() = TimeBaseAdded("tb-add-$seq", at(), Fixtures.timeBase, "session resumed")

    @Test fun twoPointsAddedThenOneRemovedKeepsOnlyTheOther() {
        val journal = listOf(point("cp-1"), point("cp-2"), removal("ev-r1", "cp-2"))
        assertEquals(listOf("cp-1"), ControlPointPolicy.active(journal).map { it.id },
            "le point retiré dans la fenêtre courante n'est plus actif, l'autre reste")
    }

    @Test fun aTimeBaseAddedCutsTheWindow() {
        val journal = listOf(point("cp-avant"), resume(), point("cp-apres"))
        assertEquals(listOf("cp-apres"), ControlPointPolicy.active(journal).map { it.id },
            "les points d'avant la reprise ne pilotent plus le calcul")
        assertEquals(listOf("cp-avant"), ControlPointPolicy.previous(journal).map { it.id },
            "ils restent visibles comme « session précédente »")
    }

    @Test fun withoutResumePreviousWindowIsEmpty() {
        val journal = listOf(point("cp-1"), removal("ev-r1", "cp-1"), point("cp-2"))
        assertTrue(ControlPointPolicy.previous(journal).isEmpty())
        assertEquals(listOf("cp-2"), ControlPointPolicy.active(journal).map { it.id })
    }

    @Test fun aRemovalAfterResumeDoesNotReachThePreviousWindow() {
        // « Supprimer » concerne la fenêtre courante : un retrait postérieur à la reprise ne masque pas
        // l'affichage « session précédente » du point d'avant.
        val journal: List<SessionEvent> = listOf(point("cp-avant"), resume(), removal("ev-r1", "cp-avant"))
        assertTrue(ControlPointPolicy.active(journal).isEmpty())
        assertEquals(listOf("cp-avant"), ControlPointPolicy.previous(journal).map { it.id })
    }

    @Test fun severalTimeBasesKeepOnlyTheLatestWindow() {
        val journal = listOf(point("cp-1"), resume(), point("cp-2"), resume(), point("cp-3"))
        assertEquals(listOf("cp-3"), ControlPointPolicy.active(journal).map { it.id })
        assertEquals(listOf("cp-1", "cp-2"), ControlPointPolicy.previous(journal).map { it.id },
            "la fenêtre « précédente » regroupe tous les repères AR avant le dernier")
    }

    @Test fun clearRelocalizationsOnlyKeepsCalibrationPoints() {
        val journal = listOf(point("cp-cal"), point("cp-rel-1", "RELOCALIZATION"), point("cp-rel-2", "RELOCALIZATION"))
        assertEquals(listOf("cp-rel-1", "cp-rel-2"), ControlPointPolicy.idsToClear(journal, relocalizationsOnly = true))
        assertEquals(listOf("cp-cal", "cp-rel-1", "cp-rel-2"), ControlPointPolicy.idsToClear(journal, relocalizationsOnly = false))
    }

    @Test fun clearSkipsAlreadyRemovedAndPreviousWindowPoints() {
        val journal = listOf(
            point("cp-avant", "RELOCALIZATION"), resume(),
            point("cp-r1", "RELOCALIZATION"), removal("ev-r1", "cp-r1"), point("cp-r2", "RELOCALIZATION"))
        assertEquals(listOf("cp-r2"), ControlPointPolicy.idsToClear(journal, relocalizationsOnly = true))
    }
}
