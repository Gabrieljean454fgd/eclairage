package app.lumisite.core

import app.lumisite.core.sync.SyncRole2
import app.lumisite.core.sync.SyncService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Retour terrain build 199, point 1f : service de premier plan de la synchro (notification, types, durée de vie). */
class SyncServiceTest {
    @Test fun serviceRunsOnlyWithAnActiveRole() {
        assertTrue(SyncService.shouldRun(true, SyncRole2.SAISIE_MAITRE))
        assertTrue(SyncService.shouldRun(true, SyncRole2.CAPTATION_ESCLAVE))
        assertFalse(SyncService.shouldRun(false, SyncRole2.SAISIE_MAITRE))
        assertFalse(SyncService.shouldRun(true, null))
        assertFalse(SyncService.shouldRun(false, null))
    }

    @Test fun connectedDeviceAlwaysMicrophoneOnlyWithRecordAudio() {
        assertEquals(setOf(SyncService.ForegroundType.CONNECTED_DEVICE), SyncService.foregroundTypes(false))
        assertEquals(setOf(SyncService.ForegroundType.CONNECTED_DEVICE, SyncService.ForegroundType.MICROPHONE),
            SyncService.foregroundTypes(true))
    }

    @Test fun masterNoticeCountsConnectedSlaves() {
        val n = SyncService.notice(SyncRole2.SAISIE_MAITRE, 2, null)
        assertEquals("Synchro LumiSite active", n.title)
        assertEquals("2 esclave(s)", n.text)
        assertEquals("0 esclave(s)", SyncService.notice(SyncRole2.SAISIE_MAITRE, 0, "ignoré").text)
    }

    @Test fun slaveNoticeShowsMasterOrSearch() {
        val n = SyncService.notice(SyncRole2.CAPTATION_ESCLAVE, 0, "Pixel 7 (JSNK)")
        assertEquals("Synchro LumiSite active", n.title)
        assertEquals("Connecté à Pixel 7 (JSNK)", n.text)
        assertEquals("Recherche du maître…", SyncService.notice(SyncRole2.CAPTATION_ESCLAVE, 0, null).text)
    }
}
