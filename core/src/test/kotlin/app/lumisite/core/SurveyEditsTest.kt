package app.lumisite.core

import app.lumisite.core.model.Attachment
import app.lumisite.core.survey.SurveyEdits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** v176a A3/A5/A8: Session > Relevé > Détail. */
class SurveyEditsTest {
    @Test fun clearedNoteIsRemovedAndUneditedNoteIsKept() {
        assertNull(SurveyEdits.note("ancienne note", ""), "a note emptied by the user is erased")
        assertNull(SurveyEdits.note("ancienne note", "   "))
        assertEquals("ancienne note", SurveyEdits.note("ancienne note", null), "not edited: kept")
        assertEquals("nouvelle", SurveyEdits.note("ancienne note", "nouvelle"))
    }

    @Test fun attachmentsAreCountedPerRole() {
        val a = listOf(
            Attachment("attachments/ph_1.jpg", "image/jpeg", role = "etiquette"),
            Attachment("attachments/ph_2.jpg", "image/jpeg", role = "vue"),
            Attachment("attachments/ph_3.jpg", "image/jpeg", role = "vue"),
            Attachment("attachments/ph_4.jpg", "image/jpeg"),
            Attachment("attachments/vn_1.m4a", "audio/mp4", role = "voix"),
        )
        assertEquals(mapOf("etiquette" to 1, "vue" to 3, "voix" to 1), SurveyEdits.attachmentCounts(a))
    }
}
