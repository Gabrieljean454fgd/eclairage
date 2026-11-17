package app.lumisite.core.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Tests JVM du catalogue LS_* isolé (préparation v176e). Pas de Gradle lancé le jour même. */
class LsCatalogTest {
    private val fixtureFile = File("../catalog/lumisite_catalog_2.fixture.json")
    private val catalog = LsCatalog.parse(fixtureFile.readText())

    @Test fun fixtureFileIsValid() {
        assertEquals("fixture-v176e", catalog.version)
        assertEquals(10, catalog.items.size)
        assertTrue(catalog.errors().isEmpty())
    }

    @Test fun codesAreUniqueAsciiPrefixedAndShort() {
        val codes = catalog.items.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        catalog.items.forEach {
            assertTrue(it.code.all { c -> c.code <= 0x7F }, "non ASCII : ${it.code}")
            assertTrue(it.code.startsWith("LS_"), it.code)
            assertTrue(it.code.length <= 31, it.code)
            assertTrue(LsCatalog.isValidCode(it.code), it.code)
        }
    }

    @Test fun searchEncastreWithoutAccentFindsDownlight() {
        val found = catalog.search("encastre")
        assertTrue(found.any { it.code == "LS_ECL_ENC_DOWNLIGHT" }, found.map { it.code }.toString())
    }

    @Test fun searchSynonymAndEmptyQuery() {
        assertTrue(catalog.search("projo").any { it.code == "LS_ECL_PROJ_LED" })
        assertEquals(catalog.items, catalog.search("   "))
    }

    @Test fun othersFamilyIsLast() {
        val families = catalog.families()
        assertEquals("Autres", families.last())
        assertTrue("Éclairage" in families && "Son" in families)
    }

    @Test fun navigationAndBreadcrumb() {
        assertEquals(listOf("Encastré", "Projecteur", "Suspendu"), catalog.sousFamilles("Éclairage"))
        val types = catalog.types("Éclairage", "Encastré")
        assertEquals(listOf("LS_ECL_ENC_DOWNLIGHT", "LS_ECL_ENC_DALLE"), types.map { it.code })
        assertEquals("Éclairage › Encastré › Downlight", catalog.breadcrumb(types.first()))
        assertEquals("Éclairage › Encastré › Downlight", LsCatalog.breadcrumb("Éclairage", "Encastré", "Downlight"))
    }

    @Test fun parseRejectsInvalidCodes() {
        fun wrap(code: String, extra: String = "") =
            """{"version":"t","items":[{"code":"$code","label":"x","famille":"F","type":"T"$extra}]}"""
        assertFailsWith<LsCatalogException> { LsCatalog.parse(wrap("ECLAIRAGE_DOWNLIGHT")) }
        assertFailsWith<LsCatalogException> { LsCatalog.parse(wrap("LS_ECLAIRAGE_DOWNLIGHT_TROP_LONG")) } // 32
        assertFailsWith<LsCatalogException> { LsCatalog.parse(wrap("LS_ECL_ACCENTUÉ")) }
        val dup = """{"version":"t","items":[
            {"code":"LS_ECL_A","label":"a","famille":"F","type":"T"},
            {"code":"LS_ECL_A","label":"b","famille":"F","type":"T"}
        ]}"""
        assertFailsWith<LsCatalogException> { LsCatalog.parse(dup) }
    }

    @Test fun recentsCapAndFavorites() {
        val many = (1..15).map { "LS_ECL_%02d".format(it) }
        val pushed = many.fold(emptyList<String>()) { acc, c -> LsRecents.push(acc, c, 12) }
        assertEquals(12, pushed.size)
        assertEquals("LS_ECL_15", pushed.first())
        assertEquals("LS_ECL_04", pushed.last())
        val fav = LsRecents.toggleFavorite(listOf("LS_ECL_01"), "LS_ECL_02")
        assertEquals(listOf("LS_ECL_01", "LS_ECL_02"), fav)
        assertEquals(listOf("LS_ECL_01"), LsRecents.toggleFavorite(fav, "LS_ECL_02"))
    }
}
