package app.lumisite.core

import app.lumisite.core.geo.Enu
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import app.lumisite.core.model.ZoneEvent
import app.lumisite.core.survey.EditHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Session > Annoter : « Annuler » / « Refaire » (v176f F1), journal append-only. */
class EditHistoryTest {
    private val t = Fixtures.stamp(1)
    private val pts = listOf(Enu(0.0, 0.0, 0.0), Enu(2.0, 1.0, 0.0))
    private fun shape(id: String, supersedes: String? = null, label: String? = null, deleted: Boolean? = null) =
        ShapeEvent(id, t, "shape", "rectangle", pts, label = label, supersedes = supersedes, deleted = deleted)

    @Test fun undoACreationMarksTheHeadDeleted() {
        val ev = listOf(shape("s1", label = "A"))
        val u = assertIs<ShapeEvent>(EditHistory.undoRecord(ev, "s1", "u1", t))
        assertEquals("s1", u.supersedes)
        assertTrue(u.deleted == true, "undoing a creation deletes the object (logically)")
    }

    @Test fun undoAnEditRestoresThePreviousVersion() {
        val ev = listOf(shape("s1", label = "A"), shape("s2", "s1", label = "B"))
        val u = assertIs<ShapeEvent>(EditHistory.undoRecord(ev, "s2", "u1", t))
        assertEquals("s2", u.supersedes, "the undo record supersedes the chain head")
        assertEquals("A", u.label, "the previous content is restored")
        assertNull(u.deleted)
    }

    @Test fun undoADeletionBringsTheObjectBack() {
        val ev = listOf(shape("s1", label = "A"), shape("s2", "s1", deleted = true))
        val u = assertIs<ShapeEvent>(EditHistory.undoRecord(ev, "s2", "u1", t))
        assertNull(u.deleted, "undoing a deletion restores the object")
        assertEquals("A", u.label)
    }

    @Test fun redoReappliesTheUndoneChange() {
        val ev = listOf(shape("s1", label = "A"), shape("s2", "s1", label = "B"))
        val u = assertIs<ShapeEvent>(EditHistory.undoRecord(ev, "s2", "u1", t))
        val ev2 = ev + u
        val r = assertIs<ShapeEvent>(EditHistory.redoRecord(ev2, "s2", "r1", t))
        assertEquals("u1", r.supersedes, "the redo record sits on top of the undo record")
        assertEquals("B", r.label, "the undone change is applied again")
        assertNull(r.deleted)
    }

    @Test fun redoAnUndoneDeletionDeletesAgain() {
        val ev = listOf(shape("s1", label = "A"), shape("s2", "s1", deleted = true))
        val u = assertIs<ShapeEvent>(EditHistory.undoRecord(ev, "s2", "u1", t))
        val r = assertIs<ShapeEvent>(EditHistory.redoRecord(ev + u, "s2", "r1", t))
        assertTrue(r.deleted == true, "redoing a deletion deletes again")
    }

    @Test fun zoneChainsStayZoneEvents() {
        val z1 = ZoneEvent("z1", t, pts, label = "A")
        val z2 = z1.copy(id = "z2", supersedes = "z1", label = "B")
        val u = assertIs<ZoneEvent>(EditHistory.undoRecord(listOf(z1, z2), "z2", "u1", t))
        assertEquals("A", u.label)
        val r = assertIs<ZoneEvent>(EditHistory.redoRecord(listOf(z1, z2, u), "z2", "r1", t))
        assertEquals("B", r.label)
    }

    @Test fun surveyEditsAreUndoableToo() {
        val s1 = SurveyEvent("v1", t, "luminaire", tracking = TrackingState.TRACKING, positionSource = "MANUAL_MAP", note = "avant")
        val s2 = s1.copy(id = "v2", supersedes = "v1", note = "après")
        val u = assertIs<SurveyEvent>(EditHistory.undoRecord(listOf(s1, s2), "v2", "u1", t))
        assertEquals("avant", u.note)
    }

    @Test fun nothingToUndoOnUnknownOrNonEditableEvents() {
        assertNull(EditHistory.undoRecord(listOf(shape("s1")), "zzz", "u", t))
        assertNull(EditHistory.redoRecord(listOf(shape("s1")), "zzz", "r", t))
        val start = SessionStarted("st", t)
        assertNull(EditHistory.undoRecord(listOf(start), "st", "u", t))
        assertNull(EditHistory.redoRecord(listOf(start), "st", "r", t))
    }
}
