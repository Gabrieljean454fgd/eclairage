package app.lumisite.core

import app.lumisite.core.catalog.EquipmentCatalog
import app.lumisite.core.catalog.LegacyKinds
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    private val catalog = EquipmentCatalog.parse(File("../catalog/equipment_catalog.json").readText())

    private val dwgNames = listOf(
        "Amplificateur", "Camera", "Diffuseur Sonore Radio", "Déclencheur Manuel", "Lanterne EP moderne", "Lustre", "PDL",
        "bloc secour", "bougeoir électrifié", "boîte de dérivation", "enceinte", "extincteur", "haut-parleur", "hublot ecl",
        "interrupteur", "lampe", "lineaire fluo", "micro col", "prise xlr", "prise électrique", "projecteur",
        "projecteur thorn contrast", "spot", "tableau divisionaire", "tgbt",
    )

    @Test fun versionAndLayer() {
        assertEquals("lumisite-catalog-1.0", catalog.version)
        assertTrue(catalog.entries.all { it.defaultLayer == "RES - Elec" })
    }

    @Test fun exactly25BlocksWithOnlyTheTwoValidatedRenamesPlusUnclassified() {
        val expected = dwgNames.map { mapOf("bloc secour" to "bloc secours", "tableau divisionaire" to "tableau divisionnaire")[it] ?: it }.toSet()
        val blocks = catalog.entries.filter { it.isBlock }.map { it.blockName }
        assertEquals(25, blocks.size)
        assertEquals(expected, blocks.toSet())
        assertEquals(listOf("autre (à classer)"), catalog.entries.filter { !it.isBlock }.map { it.blockName })
        listOf("prise électrique", "boîte de dérivation", "micro col", "Camera", "hublot ecl", "lineaire fluo", "tgbt").forEach { assertTrue(it in blocks, it) }
    }

    @Test fun aliasesResolveToCanonicalNames() {
        assertEquals("bloc secours", catalog.resolve("bloc secour").blockName)
        assertEquals("tableau divisionnaire", catalog.resolve("tableau divisionaire").blockName)
        assertEquals("bloc secours", catalog.resolve("bloc secours").blockName)
        assertEquals("spot", catalog.resolve(" Spot ").blockName)
        dwgNames.forEach { assertTrue(catalog.find(it) != null, it) }
    }

    @Test fun pendingRenamesAreNotAccepted() {
        assertNull(catalog.find("caméra"))
        assertNull(catalog.find("TGBT ")?.takeIf { it.blockName != "tgbt" })
        assertEquals("tgbt", catalog.resolve("TGBT").blockName) // AutoCAD block names are case-insensitive
    }

    @Test fun unknownFamilyIsRefused() {
        assertFailsWith<IllegalArgumentException> { catalog.resolve("LUMINAIRE") }
        assertFailsWith<IllegalArgumentException> { catalog.resolve("LUMI_LUMINAIRE_GENERIC") }
    }

    @Test fun orientableFamilies() {
        val orientable = catalog.entries.filter { it.orientable }.map { it.blockName }.toSet()
        assertEquals(setOf("spot", "projecteur", "projecteur thorn contrast", "Camera", "haut-parleur", "lineaire fluo", "prise électrique", "bloc secours"), orientable)
    }

    @Test fun duplicateAliasIsRejected() {
        val bad = """{"catalog_version":"x","entries":[
            {"block_name":"a","aliases":["b"],"label":"a","famille":"f","default_layer":"L"},
            {"block_name":"b","label":"b","famille":"f","default_layer":"L"}]}"""
        assertFailsWith<IllegalArgumentException> { EquipmentCatalog.parse(bad) }
    }

    @Test fun legacyKindsMapping() {
        assertEquals(LegacyKinds.Target.UNCLASSIFIED, LegacyKinds.target("LUMINAIRE"))
        assertEquals(LegacyKinds.Target.UNCLASSIFIED, LegacyKinds.target("AUTRE"))
        assertEquals(LegacyKinds.Target.TEXT_ANNOTATION, LegacyKinds.target("OBSERVATION"))
        assertEquals(LegacyKinds.Target.HEIGHT_OR_TEXT, LegacyKinds.target("MESURE"))
        assertTrue(LegacyKinds.isLegacyBlock("LUMI_MESURE_GENERIC"))
    }
}
