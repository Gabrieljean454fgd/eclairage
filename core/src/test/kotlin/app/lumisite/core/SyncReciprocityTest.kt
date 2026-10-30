package app.lumisite.core

import app.lumisite.core.model.SyncEvent
import app.lumisite.core.model.SyncMethod
import app.lumisite.core.model.SyncRole
import app.lumisite.core.time.Stamp
import app.lumisite.core.sync.SyncReciprocity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncReciprocityTest {
    private fun ev(code: String, role: SyncRole) = SyncEvent("s-$code-$role", Stamp(0, 1, "tb"), code, SyncMethod.AUDIO_CHIRP, role, 1)

    @Test fun onlyMutualExchangesCount() {
        val a = listOf(ev("AC-AAAA-1", SyncRole.EMITTER), ev("AC-BBBB-4", SyncRole.RECEIVER))
        val bHeardA = listOf(ev("AC-BBBB-4", SyncRole.EMITTER), ev("AC-AAAA-1", SyncRole.RECEIVER))
        val bMicOff = listOf(ev("AC-BBBB-4", SyncRole.EMITTER))
        assertTrue(SyncReciprocity.mutual(a, bHeardA))
        assertFalse(SyncReciprocity.mutual(a, bMicOff))
        assertFalse(SyncReciprocity.mutual(listOf(ev("AC-AAAA-1", SyncRole.EMITTER)), bHeardA))
    }
}
