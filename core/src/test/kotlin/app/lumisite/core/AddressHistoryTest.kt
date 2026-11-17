package app.lumisite.core

import app.lumisite.core.osm.AddressHistory
import app.lumisite.core.osm.SavedAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Retour terrain build 199, point 3 : adresses saisies gardées localement, proposées tout de suite, les plus proches d'abord. */
class AddressHistoryTest {
    private val toulouse = 43.6045 to 1.4440
    private val lyon = 45.7640 to 4.8357

    @Test fun normalizeDropsAccentsAndPunctuation() {
        assertEquals("39 rue d alsace lorraine", AddressHistory.normalize("39, Rue d'Alsace-Lorraine "))
        assertEquals("ecole", AddressHistory.normalize("École"))
    }

    @Test fun recordDeduplicatesAndKeepsCoordinates() {
        var l = AddressHistory.record(emptyList(), "39 rue Dupont, Toulouse", toulouse.first, toulouse.second, 1_000)
        l = AddressHistory.record(l, "39 RUE DUPONT toulouse", null, null, 2_000)
        assertEquals(1, l.size)
        assertEquals(2, l[0].uses)
        assertEquals(toulouse.first, l[0].lat)
        assertEquals(2_000, l[0].lastUsedMs)
    }

    @Test fun blankIsIgnored() { assertTrue(AddressHistory.record(emptyList(), "  ,, ", null, null, 1).isEmpty()) }

    @Test fun prefixOfEachWordMatches() {
        val l = listOf(SavedAddress("39 rue Dupont, Toulouse"), SavedAddress("12 avenue Daumesnil, Paris"))
        assertEquals(listOf("39 rue Dupont, Toulouse"), AddressHistory.suggest(l, "39 rue D").map { it.text })
        assertEquals(2, AddressHistory.suggest(l, "").size)
        assertTrue(AddressHistory.suggest(l, "39 rue X").isEmpty())
    }

    @Test fun nearestFirstThenMostRecent() {
        val l = listOf(
            SavedAddress("39 rue Duguesclin, Lyon", lyon.first, lyon.second, lastUsedMs = 3_000),
            SavedAddress("39 rue Dupont, Toulouse", toulouse.first, toulouse.second, lastUsedMs = 1_000),
            SavedAddress("39 rue Dalayrac, Toulouse", 43.6050, 1.4450, lastUsedMs = 2_000),
        )
        val s = AddressHistory.suggest(l, "39 rue d", toulouse.first, toulouse.second)
        assertEquals(listOf("39 rue Dalayrac, Toulouse", "39 rue Dupont, Toulouse", "39 rue Duguesclin, Lyon"), s.map { it.text })
        // Sans position : la plus récente d'abord.
        assertEquals("39 rue Duguesclin, Lyon", AddressHistory.suggest(l, "39 rue d").first().text)
    }

    @Test fun historyIsCapped() {
        var l = emptyList<SavedAddress>()
        repeat(AddressHistory.MAX_ENTRIES + 10) { l = AddressHistory.record(l, "$it rue A", null, null, it.toLong()) }
        assertEquals(AddressHistory.MAX_ENTRIES, l.size)
        assertEquals("${AddressHistory.MAX_ENTRIES + 9} rue A", l.first().text)
    }

    @Test fun proximityParameters() {
        assertEquals("&lat=43.604500&lon=1.444000", AddressHistory.ignProximity(43.6045, 1.444))
        assertEquals("&viewbox=1.344000,43.704500,1.544000,43.504500", AddressHistory.nominatimViewbox(43.6045, 1.444))
    }
}
