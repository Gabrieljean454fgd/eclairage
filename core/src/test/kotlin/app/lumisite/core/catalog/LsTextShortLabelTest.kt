package app.lumisite.core.catalog

import kotlin.test.Test
import kotlin.test.assertEquals

class LsTextShortLabelTest {
    @Test fun encastreAuPlafondBecomesEclEncPlaf() {
        assertEquals("Écl. enc. plaf.", LsText.shortLabel("Encastré au plafond", "Éclairage"))
    }

    @Test fun keepsUnknownWordsAndStripsArticles() {
        assertEquals("spot mur", LsText.shortLabel("Spot mural", "AUTRE"))
    }

    @Test fun blankFallsBackToOriginal() {
        assertEquals("  ", LsText.shortLabel("  ", null))
    }
}
