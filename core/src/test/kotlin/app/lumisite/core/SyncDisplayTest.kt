package app.lumisite.core

import app.lumisite.core.sync.ClockSample
import app.lumisite.core.sync.Link
import app.lumisite.core.sync.Peer
import app.lumisite.core.sync.SyncDisplay
import app.lumisite.core.sync.SyncDisplay.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncDisplayTest {
    @Test fun nameFallsBackFromUserToSystemToHardware() {
        assertEquals("Chantier B (6GES)", SyncDisplay.deviceLabel("Chantier B", "Xiaomi 13T Pro", "Xiaomi", "23078PND5G", "ab6ges"))
        assertEquals("Xiaomi 13T Pro (6GES)", SyncDisplay.deviceLabel("  ", "Xiaomi 13T Pro", "Xiaomi", "23078PND5G", "6ges"))
        assertEquals("Xiaomi 23078PND5G (6GES)", SyncDisplay.deviceLabel(null, null, "Xiaomi", "23078PND5G", "6GES"))
        assertEquals("Pixel 7 (JSNK)", SyncDisplay.deviceLabel(null, "Pixel 7", "Google", "Pixel 7", "JSNK"))
        assertEquals("Google Pixel 7 (JSNK)", SyncDisplay.deviceLabel(null, "", "google", "Pixel 7", "JSNK"))
        assertEquals("Samsung SM-S911B (0001)", SyncDisplay.deviceLabel(null, null, "samsung", "SM-S911B", "0001"))
    }

    @Test fun nameIsAtMost40Characters() {
        val n = SyncDisplay.deviceLabel("x".repeat(80), null, null, null, "ABCD")
        assertEquals(40, n.length)
        assertTrue(n.endsWith(" (ABCD)"))
    }

    @Test fun alignmentShowsTheUncertaintyNotTheRawOffset() {
        val t = SyncDisplay.alignmentText(1_001_033_936_000_000L, 33_900_000L)
        assertEquals("aligné à ± 33,9 ms", t)
        assertFalse("1001033936" in t)
        assertEquals("non aligné", SyncDisplay.alignmentText(null, null))
    }

    private fun aligned(id: String, name: String) =
        Peer(id, name, Link.CONNECTED, samples = List(3) { ClockSample(it.toLong(), 5_000_000, 10_000_000) })

    @Test fun masterBannerIsGreenAsSoonAsOneSlaveIsConnected() {
        val b = SyncDisplay.masterBanner(listOf(Peer("1", "Xiaomi 13T Pro (6GES)", Link.CONNECTED)))
        assertEquals(Tone.VERT, b.tone)
        assertEquals("Connecté : Xiaomi 13T Pro (6GES) · alignement en cours", b.text)
        assertEquals("Connecté : Xiaomi 13T Pro (6GES) · synchronisé", SyncDisplay.masterBanner(listOf(aligned("1", "Xiaomi 13T Pro (6GES)"))).text)
        // One lost peer does not hide a connected one.
        assertEquals(Tone.VERT, SyncDisplay.masterBanner(listOf(Peer("2", "B", Link.LOST), aligned("1", "A"))).tone)
    }

    @Test fun masterBannerOrangeWhileSearchingRedWhenAllLost() {
        assertEquals(Tone.ORANGE, SyncDisplay.masterBanner(emptyList()).tone)
        assertTrue("0 appareil" in SyncDisplay.masterBanner(emptyList()).text)
        assertEquals(Tone.ORANGE, SyncDisplay.masterBanner(listOf(Peer("1", "A", Link.CONNECTING))).tone)
        val lost = SyncDisplay.masterBanner(listOf(Peer("1", "A", Link.LOST), Peer("2", "B", Link.LOST)))
        assertEquals(Tone.ROUGE, lost.tone)
        assertTrue(lost.text.startsWith("Perdu : A, B"))
    }

    @Test fun slaveBannerTurnsRedAfter10SecondsWithoutPing() {
        assertEquals(Tone.ORANGE, SyncDisplay.slaveBanner(null, null).tone)
        val ok = SyncDisplay.slaveBanner("Pixel 7 (JSNK)", 3)
        assertEquals("Connecté à Pixel 7 (JSNK) · dernier ping il y a 3 s", ok.text)
        assertEquals(Tone.VERT, ok.tone)
        assertEquals(Tone.VERT, SyncDisplay.slaveBanner("P", 9).tone)
        assertEquals(Tone.ROUGE, SyncDisplay.slaveBanner("P", 10).tone)
    }

    @Test fun homeChip() = assertEquals("Sync maître active · 2 esclave(s)", SyncDisplay.homeChip(2))
}
