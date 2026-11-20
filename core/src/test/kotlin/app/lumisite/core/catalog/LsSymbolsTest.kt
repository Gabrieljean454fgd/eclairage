package app.lumisite.core.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Symboles LS_* : chaque id utilisé par le catalogue 2.0 existe et tient dans le carré unitaire. */
class LsSymbolsTest {
    private val catalog = LsCatalog.parse(File("../catalog/lumisite_catalog_2.json").readText())

    private fun coords(p: LsPrim): List<Float> = when (p) {
        is LsPrim.Line -> listOf(p.x1, p.y1, p.x2, p.y2)
        is LsPrim.Circle -> listOf(p.cx - p.r, p.cy - p.r, p.cx + p.r, p.cy + p.r)
        is LsPrim.Arc -> listOf(p.cx - p.r, p.cy - p.r, p.cx + p.r, p.cy + p.r)
        is LsPrim.Rect -> listOf(p.left, p.top, p.right, p.bottom)
        is LsPrim.Poly -> p.pts
    }

    @Test fun everyCatalogSymbolExists() {
        catalog.items.forEach { assertNotNull(LsSymbols.glyphs[it.symbole], "${it.code} : symbole « ${it.symbole} » inconnu") }
    }

    @Test fun glyphsAreNonEmptyAndInsideUnitSquare() {
        LsSymbols.glyphs.forEach { (id, prims) ->
            assertTrue(prims.isNotEmpty(), "glyphe vide : $id")
            prims.forEach { p ->
                coords(p).forEach { v -> assertTrue(v in 0f..1f, "$id : coordonnée $v hors [0,1]") }
            }
        }
    }

    @Test fun arcSweepsAreConsistent() {
        LsSymbols.glyphs.forEach { (id, prims) ->
            prims.filterIsInstance<LsPrim.Arc>().forEach {
                assertTrue(it.sweepDeg != 0f && kotlin.math.abs(it.sweepDeg) <= 360f, "$id : sweep ${it.sweepDeg}")
            }
        }
    }

    @Test fun familyColorsAndSymbolsDefinedForCatalogFamilies() {
        catalog.families().forEach { f ->
            assertTrue(LsSymbols.familyColor(f) != 0L, f)
            assertNotNull(LsSymbols.glyphs[LsSymbols.familySymbol(f)], "symbole de famille $f")
        }
    }

    @Test fun symbolFallbackIsAutre() {
        assertEquals(LsSymbols.glyphs.getValue("autre"), LsSymbols.glyph("inexistant"))
        assertEquals(LsSymbols.glyphs.getValue("autre"), LsSymbols.glyph(null))
    }
}
