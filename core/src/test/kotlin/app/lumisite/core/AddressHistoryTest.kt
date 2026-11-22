package app.lumisite.core

import app.lumisite.core.osm.AddressHistory
import app.lumisite.core.osm.AddressHistory.Entry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressHistoryTest {
    @Test fun normalizeDropsAccentsCaseAndPunctuation() {
        assertEquals("39 rue de l eglise", AddressHistory.normalize("  39, Rue de l'Église "))
    }

    @Test fun recordDeduplicatesKeepsCoordinatesAndCaps() {
        var h = AddressHistory.record(emptyList(), "39 rue Dupont", 43.6, 1.44, 1)
        h = AddressHistory.record(h, "Place du Capitole", null, null, 2)
        h = AddressHistory.record(h, "39 RUE DUPONT", null, null, 3)
        assertEquals(listOf("39 RUE DUPONT", "Place du Capitole"), h.map { it.text })
        assertEquals(43.6, h[0].lat)
        assertEquals(h, AddressHistory.record(h, "  ", null, null, 4))
        repeat(250) { h = AddressHistory.record(h, "Adresse $it", null, null, 10L + it) }
        assertEquals(AddressHistory.MAX, h.size)
        assertEquals("Adresse 249", h.first().text)
    }

    @Test fun everyTypedWordStartsAWord() {
        val e = Entry("39 rue Dupont, Toulouse")
        assertTrue(AddressHistory.matches(e, "39 rue D"))
        assertTrue(AddressHistory.matches(e, "t"))
        assertTrue(AddressHistory.matches(e, "dup 39"))
        assertFalse(AddressHistory.matches(e, "pont"))
        assertFalse(AddressHistory.matches(e, ""))
        assertTrue(AddressHistory.matches(Entry("Rue de l'Église"), "egl"))
    }

    @Test fun nearestBandFirstThenMostRecent() {
        val here = 43.6045 to 1.4440
        val near = Entry("Rue A proche", 43.6050, 1.4445, usedMs = 1)
        val nearNewer = Entry("Rue B proche", 43.6046, 1.4441, usedMs = 5)
        val far = Entry("Rue C loin", 43.70, 1.50, usedMs = 9)
        val unknown = Entry("Rue D sans position", usedMs = 10)
        val s = AddressHistory.suggest(listOf(far, unknown, near, nearNewer), "rue", here.first, here.second)
        assertEquals(listOf("Rue B proche", "Rue A proche", "Rue C loin", "Rue D sans position"), s.map { it.text })
        // No position: most recent first.
        assertEquals(listOf("Rue D sans position", "Rue C loin", "Rue B proche", "Rue A proche"),
            AddressHistory.suggest(listOf(far, unknown, near, nearNewer), "r", null, null).map { it.text })
    }

    @Test fun onlineBiasParameters() {
        assertEquals("&lat=43.60450&lon=1.44400", AddressHistory.ignProximity(43.6045, 1.444))
        assertEquals("&viewbox=1.34400,43.70450,1.54400,43.50450", AddressHistory.nominatimViewbox(43.6045, 1.444))
        assertFalse(AddressHistory.nominatimViewbox(43.6, 1.4).contains("bounded"))
        assertEquals("", AddressHistory.ignProximity(null, 1.0))
    }
}
