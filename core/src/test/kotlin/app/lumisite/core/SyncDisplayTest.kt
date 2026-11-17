package app.lumisite.core

import app.lumisite.core.sync.Link
import app.lumisite.core.sync.Peer
import app.lumisite.core.sync.SyncDisplay
import app.lumisite.core.sync.SyncDisplay.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Retour terrain build 199, points 1-2 : nom de l'esclave visible sur le maître, connexion en vert, décalage lisible. */
class SyncDisplayTest {
    @Test fun xiaomiModelCodeIsReplacedByDeviceName() {
        assertEquals("Xiaomi 13T Pro (6GES)", SyncDisplay.deviceLabel(null, "Xiaomi 13T Pro", "Xiaomi", "23078PND5G", "abcd6GES"))
        assertEquals("Xiaomi 23078PND5G (6GES)", SyncDisplay.deviceLabel(null, null, "Xiaomi", "23078PND5G", "6GES"))
        assertEquals("Chantier A (JSNK)", SyncDisplay.deviceLabel("Chantier A", "Pixel de Seb", "Google", "Pixel 7", "JSNK"))
        assertEquals("Google Pixel 7 (JSNK)", SyncDisplay.deviceLabel(" ", "", "google", "Pixel 7", "JSNK"))
    }

    @Test fun masterIsGreenAsSoonAsOneSlaveIsConnected() {
        val b = SyncDisplay.masterBanner(listOf(Peer("6GES", "Xiaomi 13T Pro (6GES)", Link.CONNECTED)))
        assertEquals(Level.OK, b.level)
        assertTrue(b.text.startsWith("Connecté : Xiaomi 13T Pro (6GES)"), b.text)
    }

    @Test fun masterStates() {
        assertEquals(Level.WAIT, SyncDisplay.masterBanner(emptyList()).level)
        assertEquals(Level.WAIT, SyncDisplay.masterBanner(listOf(Peer("A", "X (A)", Link.CONNECTING))).level)
        val lost = SyncDisplay.masterBanner(listOf(Peer("A", "X (A)", Link.LOST)))
        assertEquals(Level.ERROR, lost.level); assertTrue(lost.text.contains("X (A)"))
        val mixed = SyncDisplay.masterBanner(listOf(Peer("A", "X (A)", Link.LOST), Peer("B", "Y (B)", Link.CONNECTED)))
        assertEquals(Level.OK, mixed.level); assertTrue(mixed.text.contains("1 perdu"))
    }

    @Test fun slaveBanner() {
        assertEquals(Level.WAIT, SyncDisplay.slaveBanner(null, null).level)
        assertEquals(Level.OK, SyncDisplay.slaveBanner("Pixel 7 (JSNK)", 3).level)
        assertEquals(Level.ERROR, SyncDisplay.slaveBanner("Pixel 7 (JSNK)", 16).level)
    }

    @Test fun hugeMonotonicOffsetIsNotShown() {
        val t = SyncDisplay.alignmentText(1_001_033_936_000_000L, 33_900_000L)
        assertEquals("aligné à ± 33,9 ms", t)
        assertEquals("non aligné", SyncDisplay.alignmentText(null, null))
    }
}
