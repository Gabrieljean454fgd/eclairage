package app.lumisite.core

import app.lumisite.core.map.Basemap
import app.lumisite.core.map.TileMath
import app.lumisite.core.model.BBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BasemapsTest {
    @Test fun ignWmtsUrl() {
        assertEquals(
            "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2&STYLE=normal" +
                "&TILEMATRIXSET=PM_0_19&TILEMATRIX=18&TILEROW=90195&TILECOL=132877&FORMAT=image/png",
            Basemap.ignTileUrl(Basemap.IGN_PLAN, 18, 132877, 90195),
        )
        assertTrue("LAYER=ORTHOIMAGERY.ORTHOPHOTOS" in Basemap.ignTileUrl(Basemap.IGN_ORTHO, 1, 0, 0))
        assertTrue("FORMAT=image/jpeg" in Basemap.ignTileUrl(Basemap.IGN_ORTHO, 1, 0, 0))
    }

    @Test fun esriIsNeverCachedOrDownloaded() {
        assertFalse(Basemap.ESRI.cacheAllowed); assertFalse(Basemap.ESRI.bulkAllowed)
        assertFalse(Basemap.OSM.bulkAllowed) // OSM tile policy: no bulk download
        Basemap.entries.forEach { assertTrue(it.nativeMaxZoom <= 19) }
    }

    @Test fun tileCountAndCap() {
        // (2.3376 + 180) / 360 × 2^18 = 132 774.6
        assertEquals(132_774, TileMath.x(2.3376, 18))
        val site = BBox(48.8600, 2.3350, 48.8620, 2.3390)
        val n16 = TileMath.count(site, 16, 16)
        assertTrue(n16 in 1..4)
        assertTrue(TileMath.count(site, 16, 19) > TileMath.count(site, 16, 18))
        val z = TileMath.maxZoomUnder(site, 16, 19, 600)!!
        assertTrue(TileMath.count(site, 16, z) <= 600)
        assertNull(TileMath.maxZoomUnder(BBox(48.0, 2.0, 49.0, 3.0), 16, 19, 600))
    }

    @Test fun visibleWidth() {
        assertEquals(111_320.0, TileMath.widthM(0.0, 1.0), 1e-6)
        assertEquals(4.0, TileMath.widthM(48.86, 4.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(48.86)))), 1e-9)
    }
}
