package app.lumisite.core

import app.lumisite.core.catalog.EquipmentCatalog
import app.lumisite.core.catalog.LegacyKinds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** v176e (DS2): catalog 2 with LS_* codes; DWG correspondence read at the office only. */
class CatalogTest {
    private val catalog = EquipmentCatalog.parse(File("../catalog/" + EquipmentCatalog.ASSET).readText())
    private val corr = Json.parseToJsonElement(File("../catalog/correspondance_dwg.json").readText()).jsonObject

    private val dwgNames = listOf(
        "Amplificateur", "Camera", "Diffuseur Sonore Radio", "Déclencheur Manuel", "Lanterne EP moderne", "Lustre", "PDL",
        "bloc secours", "bougeoir électrifié", "boîte de dérivation", "enceinte", "extincteur", "haut-parleur", "hublot ecl",
        "interrupteur", "lampe", "lineaire fluo", "micro col", "prise xlr", "prise électrique", "projecteur",
        "projecteur thorn contrast", "spot", "tableau divisionnaire", "tgbt",
    )

    @Test fun codesUniqueAsciiAtMost31() {
        val codes = catalog.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { assertTrue(it.length <= 31 && Regex("LS_[A-Z0-9_]+").matches(it), it) }
        assertEquals("lumisite-catalog-2.0", catalog.version)
    }

    @Test fun annexB78CodesPlusSonAndNewCodes() {
        listOf("ELEC", "ECLAIRAGE", "CAMERA", "SECOURS", "SECURITE", "INCENDIE").forEach { f -> assertTrue(catalog.entries.any { it.famille == f }, f) }
        assertEquals(78, corr["codes"]!!.jsonArray.count { it.jsonObject["calque_bureau"]?.jsonPrimitive?.content?.startsWith("LUMI-") == true && it.jsonObject["norme"]?.jsonPrimitive?.content != "null" } -
            corr["codes"]!!.jsonArray.count { (it.jsonObject["code"]!!.jsonPrimitive.content).let { c -> c.startsWith("LS_SON_") || c in setOf("LS_ECLAIRAGE_POINT_LUMINEUX", "LS_ECLAIRAGE_BOUGEOIR", "LS_ECLAIRAGE_LANTERNE_EP", "LS_ECLAIRAGE_HUBLOT", "LS_ELEC_PDL") } && it.jsonObject["norme"]?.jsonPrimitive?.content != "null" })
        assertEquals(6, catalog.entries.count { it.famille == "SON" })
        assertEquals("AUTRE", catalog.familles.last().code)
        assertEquals(catalog.familles.map { it.symbol }.size, catalog.familles.map { it.symbol }.toSet().size)
    }

    @Test fun correspondenceCovers25DwgBlocks() {
        val blocs = corr["blocs"]!!.jsonArray.map { it.jsonObject }
        val names = blocs.mapNotNull { it["block_name"]?.jsonPrimitive?.takeIf { p -> p.isString }?.content }
        assertEquals(dwgNames.toSet(), names.toSet())
        blocs.forEach { b -> assertTrue(catalog.find(b["code"]!!.jsonPrimitive.content) != null, b.toString()) }
        val radio = blocs.first { it["block_name"]?.jsonPrimitive?.content == "Diffuseur Sonore Radio" }
        assertEquals("LS_SON_DIFFUSEUR_SONORE_RADIO", radio["code"]!!.jsonPrimitive.content)
        assertTrue(radio["_commentaire"]!!.jsonPrimitive.content.contains("PROVISOIRE"))
    }

    @Test fun formerBlockNamesAreReadAsAliases() {
        assertEquals("LS_ECLAIRAGE_DOWNLIGHT", catalog.resolve(" Spot ").code)
        assertEquals("LS_SECOURS_BAES", catalog.resolve("bloc secour").code)
        assertEquals("LS_ELEC_TABLEAU_DIVISIONNAIRE", catalog.resolve("tableau divisionaire").code)
        assertEquals("LS_ELEC_TABLEAU_GENERAL", catalog.resolve("TGBT").code)
        assertEquals("LS_ECLAIRAGE_PROJECTEUR", catalog.resolve("projecteur thorn contrast").code)
        assertEquals("LS_AUTRE_A_CLASSER", catalog.resolve("autre (à classer)").code)
        dwgNames.forEach { assertTrue(catalog.find(it) != null, it) }
        assertNull(catalog.find("caméra"))
    }

    @Test fun hierarchyAndBreadcrumb() {
        val d = catalog.resolve("LS_ECLAIRAGE_DOWNLIGHT")
        assertEquals("Éclairage › Encastré › Downlight encastre au plafond", catalog.breadcrumb(d))
        assertTrue(d in catalog.types("ECLAIRAGE", "Encastré"))
        assertTrue("Encastré" in catalog.sousFamilles("ECLAIRAGE"))
        assertEquals("amplification_et_prises", EquipmentCatalog.slug("Amplification et prises"))
    }

    @Test fun searchIsAccentInsensitive() {
        assertEquals("LS_ECLAIRAGE_DOWNLIGHT", catalog.search("encastré").first().code)
        assertTrue(catalog.search("ENCASTRE").any { it.code == "LS_ECLAIRAGE_DOWNLIGHT" })
        assertTrue(catalog.search("tgbt").any { it.code == "LS_ELEC_TABLEAU_GENERAL" })
        assertTrue(catalog.search("réglette").any { it.code == "LS_ECLAIRAGE_REGLE_IMMERSION" })
        assertTrue(catalog.search("   ").isEmpty())
    }

    @Test fun unknownTypeIsRefused() {
        assertFailsWith<IllegalArgumentException> { catalog.resolve("LUMINAIRE") }
        assertFailsWith<IllegalArgumentException> { catalog.resolve("LUMI_LUMINAIRE_GENERIC") }
    }

    @Test fun duplicateAliasIsRejected() {
        val bad = """{"catalog_version":"x","entries":[
            {"code":"LS_A","aliases":["LS_B"],"label":"a","famille":"f"},
            {"code":"LS_B","label":"b","famille":"f"}]}"""
        assertFailsWith<IllegalArgumentException> { EquipmentCatalog.parse(bad) }
    }

    @Test fun legacyKindsMapping() {
        assertEquals(LegacyKinds.Target.UNCLASSIFIED, LegacyKinds.target("LUMINAIRE"))
        assertEquals(LegacyKinds.Target.TEXT_ANNOTATION, LegacyKinds.target("OBSERVATION"))
        assertTrue(LegacyKinds.isLegacyBlock("LUMI_MESURE_GENERIC"))
    }
}
