package app.lumisite.core.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Catalogue 2.0 complet (menu-bloc) : 78 codes annexe B + 5 codes table LS→DWG + 6 SON + LS_AUTRE_A_CLASSER.
 * La table CSV est copiée dans catalog/table_ls_dwg.csv ; le catalogue 1.0 sert à vérifier la relecture
 * des anciens block_name / alias.
 */
class LsCatalog2Test {
    private val catalog = LsCatalog.parse(File("../catalog/lumisite_catalog_2.json").readText())
    private val old = EquipmentCatalog.parse(File("../catalog/equipment_catalog.json").readText())

    private fun csvRows(): List<Map<String, String>> {
        val lines = File("../catalog/table_ls_dwg.csv").readText().lines().filter { it.isNotBlank() }
        val header = lines.first().split(',')
        return lines.drop(1).map { l -> header.zip(l.split(',')).toMap() }
    }

    @Test fun atLeast78CodesAndStructurallyValid() {
        assertEquals("lumisite-catalog-2.0", catalog.version)
        assertTrue(catalog.items.size >= 78, "${catalog.items.size} items")
        assertEquals(emptyList(), catalog.errors())
    }

    @Test fun codesUniqueAsciiShort() {
        val codes = catalog.items.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { c ->
            assertTrue(c.all { it.code <= 0x7F }, "non ASCII : $c")
            assertTrue(c.startsWith("LS_"), c)
            assertTrue(c.length <= LsCatalog.MAX_CODE_LENGTH, c)
            assertTrue(LsCatalog.isValidCode(c), c)
        }
    }

    @Test fun allAnnexeBCodesPresent() {
        // 78 codes de l'annexe B + 5 codes table LS→DWG hors annexe (PDL, 4 ÉCLAIRAGE) + 6 SON + 1 AUTRE = 90.
        val want = mapOf(
            "ELEC" to 14, "ECLAIRAGE" to 19, "CAMERA" to 12, "SECOURS" to 12, "SECURITE" to 11, "INCENDIE" to 15,
            "SON" to 6, "AUTRE" to 1,
        )
        want.forEach { (f, n) ->
            assertEquals(n, catalog.items.count { it.famille == f }, "famille $f")
        }
        assertEquals(90, catalog.items.size)
    }

    @Test fun familyOrderEclairageFirstAutreLast() {
        assertEquals(listOf("ECLAIRAGE", "ELEC", "SECOURS", "SECURITE", "INCENDIE", "CAMERA", "SON", "AUTRE"), catalog.families())
        assertEquals("AUTRE", catalog.families().last())
        assertEquals("Éclairage", LsCatalog.familyLabel("ECLAIRAGE"))
        assertEquals("Sonorisation", LsCatalog.familyLabel("SON"))
    }

    @Test fun everyCsvDwgBlockIsReferenced() {
        val dwgNames = csvRows().mapNotNull { it["nom_bloc_DWG"] }.toSet()
        val known = catalog.items.mapNotNull { it.blocDwg }.toSet()
        dwgNames.forEach { assertTrue(it in known, "bloc DWG introuvable : « $it »") }
    }

    @Test fun everyCsvCodeIsAnItem() {
        csvRows().mapNotNull { it["code"] }.forEach { assertNotNull(catalog.byCode(it), "code CSV absent : $it") }
    }

    @Test fun everyOldBlockNameAndAliasFindsACode() {
        old.entries.forEach { e ->
            (listOf(e.blockName) + e.aliases).forEach { n ->
                assertNotNull(catalog.byLegacyName(n), "ancien nom sans code LS_* : « $n »")
            }
        }
        assertEquals("LS_SECOURS_BAES", catalog.byLegacyName("bloc secour")!!.code)
        assertEquals("LS_ELEC_TABLEAU_DIVISIONNAIRE", catalog.byLegacyName("tableau divisionaire")!!.code)
        assertEquals("LS_ECLAIRAGE_PROJECTEUR", catalog.byLegacyName("projecteur thorn contrast")!!.code)
        assertEquals("LS_SON_AMPLIFICATEUR", catalog.byCodeOrLegacy("Amplificateur")!!.code)
        assertNull(catalog.byCodeOrLegacy("n'importe quoi"))
    }

    @Test fun layersAreLumiAndIsBlockFlags() {
        catalog.items.forEach { assertTrue(it.calque?.startsWith("LUMI-") == true, "${it.code} : ${it.calque}") }
        assertEquals(listOf("LS_AUTRE_A_CLASSER"), catalog.items.filter { !it.isBlock }.map { it.code })
    }

    @Test fun searchAccentFreeAndLegacyAlias() {
        assertTrue(catalog.search("encastre").any { it.code == "LS_ECLAIRAGE_DOWNLIGHT" })
        assertTrue(catalog.search("projo").any { it.code == "LS_ECLAIRAGE_PROJECTEUR" })
        // Un ancien nom trouve aussi son item (recherche sur les alias).
        assertTrue(catalog.search("bloc secour").any { it.code == "LS_SECOURS_BAES" })
        assertEquals(catalog.items, catalog.search("  "))
    }

    @Test fun navigationFamilyToTypes() {
        val types = catalog.types("ECLAIRAGE", null)
        assertEquals(19, types.size)
        assertEquals("LS_ECLAIRAGE_DOWNLIGHT", types.first().code)
        assertEquals("Éclairage › Downlight encastré au plafond",
            LsCatalog.breadcrumb(LsCatalog.familyLabel("ECLAIRAGE"), null, types.first().type))
    }

    @Test fun nextPersonalCode() {
        assertEquals("LS_PERSO_1", LsCatalog.nextPersonalCode(catalog.items))
        val withPerso = catalog.items + catalog.items.first().copy(code = "LS_PERSO_2")
        assertEquals("LS_PERSO_3", LsCatalog.nextPersonalCode(withPerso))
        assertTrue(LsCatalog.isValidCode("LS_PERSO_7"))
    }
}
