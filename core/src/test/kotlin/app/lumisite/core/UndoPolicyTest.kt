package app.lumisite.core

import app.lumisite.core.geo.Enu
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import app.lumisite.core.model.ZoneEvent
import app.lumisite.core.session.UndoPolicy
import app.lumisite.core.survey.Shapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Annuler » / « Refaire » : restauration par ajout d'un événement, journal jamais réécrit. */
class UndoPolicyTest {
    private var seq = 0
    private fun at() = Fixtures.stamp(++seq * 1_000_000_000L)

    private fun shape(id: String, supersedes: String? = null, deleted: Boolean? = null, label: String? = null) =
        ShapeEvent(id, at(), "shape", "polyline", listOf(Enu(0.0, 0.0), Enu(1.0, 0.0)), label = label, supersedes = supersedes, deleted = deleted)

    private fun survey(id: String, supersedes: String? = null, deleted: Boolean? = null, note: String? = null) =
        SurveyEvent(id, at(), "luminaire", note = note, tracking = TrackingState.TRACKING, positionSource = "MANUAL_MAP",
            manualSite = Enu(2.0, 3.0), supersedes = supersedes, deleted = deleted)

    private fun zone(id: String, supersedes: String? = null, label: String? = null) =
        ZoneEvent(id, at(), listOf(Enu(0.0, 0.0), Enu(4.0, 0.0), Enu(4.0, 3.0)), label = label, supersedes = supersedes)

    @Test fun undoingAShapeCreationAppendsADeletion() {
        val journal = listOf(shape("shp-1"))
        val undo = UndoPolicy.undo(journal, "shp-1", "shp-u1", at())
        assertTrue(undo is ShapeEvent)
        undo as ShapeEvent
        assertEquals("shp-1", undo.supersedes, "l'annulation coiffe la tête de la chaîne d'édition")
        assertEquals(true, undo.deleted, "pas d'état avant une création : l'annulation supprime")
        assertTrue(Shapes.current(journal + undo).isEmpty(), "la forme disparaît de l'état courant")
    }

    @Test fun undoingAShapeEditRestoresThePreviousVersion() {
        val journal = listOf(shape("shp-1", label = "avant"), shape("shp-2", supersedes = "shp-1", label = "après"))
        val undo = UndoPolicy.undo(journal, "shp-2", "shp-u1", at())
        assertTrue(undo is ShapeEvent)
        undo as ShapeEvent
        assertEquals("shp-2", undo.supersedes)
        assertEquals("avant", undo.label, "l'état courant revient à la version précédente")
        assertNull(undo.deleted)
        assertEquals(listOf("avant"), Shapes.current(journal + undo).map { it.label })
    }

    @Test fun undoingASurveyCreationMarksItDeleted() {
        val journal = listOf(survey("srv-1", note = "borne"))
        val undo = UndoPolicy.undo(journal, "srv-1", "srv-u1", at())
        assertTrue(undo is SurveyEvent)
        undo as SurveyEvent
        assertEquals("srv-1", undo.supersedes)
        assertEquals(true, undo.deleted)
    }

    @Test fun undoingASurveyEditRestoresItsNote() {
        val journal = listOf(survey("srv-1", note = "avant"), survey("srv-2", supersedes = "srv-1", note = "après"))
        val undo = UndoPolicy.undo(journal, "srv-2", "srv-u1", at())
        assertTrue(undo is SurveyEvent)
        undo as SurveyEvent
        assertEquals("avant", undo.note)
        assertEquals("srv-2", undo.supersedes)
        assertNull(undo.deleted)
    }

    @Test fun undoingADeletionBringsTheRecordBack() {
        val journal = listOf(survey("srv-1", note = "créé"), survey("srv-2", supersedes = "srv-1", deleted = true))
        val undo = UndoPolicy.undo(journal, "srv-2", "srv-u1", at())
        assertTrue(undo is SurveyEvent)
        undo as SurveyEvent
        assertEquals("créé", undo.note)
        assertNull(undo.deleted, "annuler une suppression rétablit l'enregistrement")
    }

    @Test fun undoingAZoneWhosePreviousVersionIsAnOldFormatShape() {
        // Format 0.1 : la version précédente d'une zone peut avoir été écrite en ShapeEvent(kind = "zone").
        val oldFormat = shape("zon-1", label = "v1").copy(kind = "zone", geometryType = "polygon")
        val journal = listOf(oldFormat, zone("zon-2", supersedes = "zon-1"))
        val undo = UndoPolicy.undo(journal, "zon-2", "zon-u1", at())
        assertTrue(undo is ZoneEvent, "la version 0.1 est relue comme une zone")
        undo as ZoneEvent
        assertEquals("zon-2", undo.supersedes)
        assertEquals("v1", undo.label)
    }

    @Test fun undoDoesNotMutateTheUndoneEvent() {
        val creation = shape("shp-1", label = "avant")
        val edit = shape("shp-2", supersedes = "shp-1", label = "après")
        val journal = listOf<SessionEvent>(creation, edit)
        val undo = UndoPolicy.undo(journal, "shp-2", "shp-u1", at())
        assertEquals(listOf(creation, edit), journal, "le journal reste append-only : rien n'est réécrit")
        assertNotNull(undo)
        assertTrue(undo.id !in journal.map { it.id }, "id neuf")
        assertEquals("shp-u1", Shapes.head(journal + undo, "shp-1"), "la tête de la chaîne avance vers l'annulation")
    }

    @Test fun nothingToUndoForUnknownIdOrUnhandledKind() {
        assertNull(UndoPolicy.undo(emptyList(), "inconnu", "shp-u1", at()))
        val cp = ControlPointEvent("cp-1", at(), "CALIBRATION",
            LocalObservation(null, ReferencePoint("A", Enu(0.0, 0.0), source = "MAP_TAP")))
        assertNull(UndoPolicy.undo(listOf(cp), "cp-1", "shp-u1", at()), "un point de contrôle ne s'annule pas par supersedes")
    }

    @Test fun redoReappliesTheUndoneContentOnTopOfTheChain() {
        val journal = listOf(shape("shp-1", label = "avant"), shape("shp-2", supersedes = "shp-1", label = "après"))
        val undo = UndoPolicy.undo(journal, "shp-2", "shp-u1", at())
        assertNotNull(undo)
        val j2 = journal + undo
        val redo = UndoPolicy.redo(j2, "shp-2", "shp-r1", at())
        assertTrue(redo is ShapeEvent)
        redo as ShapeEvent
        assertEquals("shp-u1", redo.supersedes, "le rétablissement coiffe la tête après annulation")
        assertEquals("après", redo.label)
        assertNull(redo.deleted)
        assertEquals(listOf("après"), Shapes.current(j2 + redo).map { it.label })
    }

    @Test fun idPrefixFollowsTheUndoneEventKind() {
        val journal = listOf<SessionEvent>(shape("shp-1"), survey("srv-1"), zone("zon-1"))
        assertEquals("zon", UndoPolicy.idPrefix(journal, "zon-1"))
        assertEquals("srv", UndoPolicy.idPrefix(journal, "srv-1"))
        assertEquals("shp", UndoPolicy.idPrefix(journal, "shp-1"))
        assertEquals("shp", UndoPolicy.idPrefix(journal, "inconnu"), "repli « forme » comme le contrôleur")
    }
}
