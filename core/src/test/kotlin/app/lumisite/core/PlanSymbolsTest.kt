package app.lumisite.core

import app.lumisite.core.catalog.EquipmentCatalog
import app.lumisite.core.catalog.PlanSymbols
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlanSymbolsTest {
    private val catalog = EquipmentCatalog.parse(File("../catalog/" + EquipmentCatalog.ASSET).readText())

    @Test fun everyTypeHasAKnownSymbolAndNoneUsesTheFlag() {
        catalog.entries.forEach { e -> assertTrue(e.symbole in PlanSymbols.all, "${e.code} → ${e.symbole}"); assertNotEquals(PlanSymbols.FLAG, e.symbole) }
        catalog.familles.forEach { assertTrue(it.symbol in PlanSymbols.all, it.code) }
    }

    @Test fun eachFamilyHasADistinctShapeAndColour() {
        val frames = catalog.familles.map { PlanSymbols.all.getValue(it.symbol).frame }
        assertEquals(frames.size, frames.toSet().size, frames.toString())
        val colours = catalog.familles.map { it.color.uppercase() }
        assertEquals(colours.size, colours.toSet().size)
        assertTrue("#FF8C00" !in colours) // orange: calibration flags only
    }

    @Test fun primitivesStayInTheUnitBox() {
        PlanSymbols.all.keys.forEach { id -> PlanSymbols.prims(id).forEach { p ->
            val xs = when (p) { is PlanSymbols.Circle -> listOf(p.cx - p.r, p.cx + p.r, p.cy - p.r, p.cy + p.r); is PlanSymbols.Line -> listOf(p.x1, p.x2, p.y1, p.y2)
                is PlanSymbols.Poly -> p.pts; is PlanSymbols.Arc -> listOf(p.cx - p.r, p.cx + p.r, p.cy - p.r, p.cy + p.r) }
            xs.forEach { assertTrue(it in -1.0001f..1.0001f, "$id $p") }
        } }
    }

    @Test fun customTypeIsLocalAndNumbered() {
        val a = EquipmentCatalog.customType(catalog, "ECLAIRAGE", "Guirlande LED")
        assertEquals("LS_PERSO_1", a.code); assertEquals(false, a.isBlock); assertEquals("ECLAIRAGE", a.famille)
        val c2 = catalog.withCustom(listOf(a))
        assertEquals("LS_PERSO_2", EquipmentCatalog.customType(c2, "SON", "Boucle magnétique").code)
        assertTrue(a in c2.types("ECLAIRAGE", EquipmentCatalog.CUSTOM_SUB))
        assertTrue(c2.search("guirlande").any { it.code == "LS_PERSO_1" })
        assertFailsWith<IllegalArgumentException> { EquipmentCatalog.customType(c2, "ECLAIRAGE", "guirlande led") }
        assertFailsWith<IllegalArgumentException> { EquipmentCatalog.customType(c2, "ECLAIRAGE", "  ") }
        assertFailsWith<IllegalArgumentException> { EquipmentCatalog.customType(c2, "XYZ", "Truc") }
    }

    @Test fun familiesOrderEclairageFirstAutreLast() {
        assertEquals(listOf("ECLAIRAGE", "ELEC", "SECOURS", "SECURITE", "INCENDIE", "CAMERA", "SON", "AUTRE"), catalog.familles.map { it.code })
        assertEquals("ECLAIRAGE", catalog.entries.first().famille)
        assertTrue(catalog.entries.filter { it.isBlock && it.code != "LS_AUTRE_A_CLASSER" }.all { it.calque?.startsWith("LUMI-") == true })
    }
}
