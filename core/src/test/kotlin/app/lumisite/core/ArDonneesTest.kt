package app.lumisite.core

import app.lumisite.core.catalog.LsCatalog
import app.lumisite.core.catalog.LsSymbols
import app.lumisite.core.compute.Aim
import app.lumisite.core.compute.ArMeasure
import app.lumisite.core.compute.ArSiteOverlays
import app.lumisite.core.compute.ArSymbols
import app.lumisite.core.compute.PlaneFilter
import app.lumisite.core.compute.V3
import app.lumisite.core.model.BBox
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.OsmFeature
import app.lumisite.core.model.OsmSnapshot
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.Site
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import app.lumisite.core.model.ZoneEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests d'acceptation verrouillés — AR conforme aux données de Sébastien (build 248).
 * Ne pas modifier ce fichier : le verdict du relecteur s'appuie sur ces assertions.
 */
class ArDonneesTest {
    private val catalog = LsCatalog.parse(File("../catalog/lumisite_catalog_2.json").readText())
    private val ltp = LocalTangentPlane(Fixtures.site.origin)
    private fun pl(y: Double, area: Double = 4.0) = PlaneFilter.Desc(V3(0.0, y, 0.0), "AR_PLANE", area, y)

    // ----- 4. Sol et table -----

    @Test fun tableAloneIsNotTheFloorWhenCameraHeightIsKnown() {
        // Appareil à 1,50 m, seul un plateau à 0,75 m (0,75 m sous l'appareil) : c'est du mobilier.
        val cameraY = 1.50
        val table = pl(0.75)
        val fy = PlaneFilter.floorLevel(listOf(table), cameraY)!!
        assertEquals(cameraY - PlaneFilter.CAMERA_FLOOR_NOM_M, fy, 1e-9)
        assertEquals(PlaneFilter.Role.FURNITURE, PlaneFilter.roleOf(table, fy, cameraY))
        assertTrue(PlaneFilter.forAim(listOf(table), cameraY).isEmpty())
    }

    @Test fun lowestPlaneInTheCameraBandIsTheFloorAndTableIsFurniture() {
        // Sol à 0,10 m (1,40 m sous une caméra à 1,50 m) et table à 0,75 m.
        val cameraY = 1.50
        val floor = pl(0.10)
        val table = pl(0.75)
        val planes = listOf(floor, table)
        val fy = PlaneFilter.floorLevel(planes, cameraY)!!
        assertEquals(0.10, fy, 1e-9)
        assertEquals(PlaneFilter.Role.FLOOR, PlaneFilter.roleOf(floor, fy, cameraY))
        assertEquals(PlaneFilter.Role.FURNITURE, PlaneFilter.roleOf(table, fy, cameraY))
        assertEquals(listOf(0.10), PlaneFilter.forAim(planes, cameraY).map { it.point.y })
    }

    @Test fun withoutCameraHeightExistingBehaviourIsUnchanged() {
        // Compatibilité : sans cameraY, une table seule reste le référentiel (tests 199).
        assertEquals(listOf(0.9), PlaneFilter.forAim(listOf(pl(0.9))).map { it.point.y })
        assertEquals(listOf(0.0), PlaneFilter.forAim(listOf(pl(0.0), pl(0.9))).map { it.point.y })
    }

    // ----- 1. Symboles catalogue -----

    @Test fun catalogHasAtLeastNinetyTypesAndEverySymbolHasGeometry() {
        assertTrue(catalog.items.size >= 90, "catalogue trop court : ${catalog.items.size}")
        val right = V3(1.0, 0.0, 0.0); val up = V3(0.0, 1.0, 0.0); val at = V3(0.0, 1.0, 0.0)
        val seen = HashSet<String>()
        for (item in catalog.items) {
            val sid = LsSymbols.symbolOf(item)
            val segs = ArSymbols.segmentsForCode(catalog, item.code, at, right, up)
            assertTrue(segs.isNotEmpty(), "aucune géométrie pour ${item.code} / $sid")
            seen += sid
        }
        assertTrue(seen.size >= 8, "trop peu de glyphes distincts : $seen")
    }

    @Test fun luminaireUsesCatalogGlyphAndShortLabelNotTheGenericCircle() {
        val item = catalog.byCode("LS_ECL_ENC_PLAF") ?: catalog.items.first { it.famille == "ECLAIRAGE" && it.symbole != null }
        val at = V3(1.0, 0.5, -2.0)
        val right = V3(1.0, 0.0, 0.0); val up = V3(0.0, 1.0, 0.0)
        val catalogSegs = ArSymbols.segmentsForCode(catalog, item.code, at, right, up)
        val generic = app.lumisite.core.compute.ArGeometry.luminaire(at)
        assertTrue(catalogSegs.isNotEmpty())
        // Le glyphe catalogue n'est pas le cercle horizontal générique (plan XZ).
        val catalogYs = catalogSegs.flatMap { listOf(it.first.y, it.second.y) }
        assertTrue(catalogYs.maxOrNull()!! - catalogYs.minOrNull()!! > 0.05, "le symbole doit être un billboard vertical, pas un cercle à plat")
        assertTrue(generic.all { it.first.y == at.y && it.second.y == at.y })
        val label = ArSymbols.shortLabel(catalog, item.code)
        assertTrue(label.isNotBlank())
        assertTrue(label.length < item.label.length || label.contains("Écl") || label.contains("enc"), "libellé court attendu, obtenu « $label » pour « ${item.label} »")
    }

    @Test fun billboardFacesTheCamera() {
        val at = V3(0.0, 1.0, 0.0)
        val forward = V3(0.0, 0.0, -1.0) // caméra regardant −Z
        val (right, up) = ArSymbols.billboardAxes(forward)
        assertEquals(0.0, up.x, 1e-9); assertEquals(1.0, up.y, 1e-9)
        assertTrue(right.norm() > 0.9)
        val segs = ArSymbols.segments(LsSymbols.glyph("point_lumineux"), at, right, up)
        assertTrue(segs.isNotEmpty())
        // Tous les points restent dans un plan vertical passant par l'ancre.
        segs.forEach { (a, b) ->
            assertEquals(at.z, a.z, 1e-6); assertEquals(at.z, b.z, 1e-6)
        }
    }

    // ----- 2. Ancres de toutes les visites -----

    @Test fun anchorsReloadFlagsAndLuminairesFromEveryVisit() {
        val tr = Similarity2D.IDENTITY
        val floorY = 0.15
        val item = catalog.items.first { it.code.startsWith("LS_") }
        val visit1 = listOf(
            SessionStarted("st1", Fixtures.stamp(100)),
            flag("flag-v1", 1.0, 2.0),
            luminaire("lum-v1", 3.0, 4.0, item),
        )
        val v2events = listOf(
            SessionStarted("st2", Fixtures.stamp(200)),
            flag("flag-v2", 5.0, 6.0),
            luminaire("lum-v2", 7.0, 8.0, item),
            luminaire("lum-gone", 9.0, 9.0, item),
            luminaire("lum-gone", 9.0, 9.0, item).copy(id = "lum-gone-x", t = Fixtures.stamp(250), supersedes = "lum-gone", deleted = true),
        )
        val anchors = ArSiteOverlays.anchors(
            listOf(ArSiteOverlays.VisitEvents("ses_matin", visit1), ArSiteOverlays.VisitEvents("ses_soir", v2events)),
            tr, floorY, catalog,
        )
        val ids = anchors.map { it.id }.toSet()
        assertTrue("flag-v1" in ids && "flag-v2" in ids, ids.toString())
        assertTrue("lum-v1" in ids && "lum-v2" in ids, ids.toString())
        assertFalse(anchors.any { it.id == "lum-gone" || it.id == "lum-gone-x" }, ids.toString())
        val flag = anchors.first { it.id == "flag-v1" }
        assertEquals(ArSiteOverlays.Kind.FLAG, flag.kind)
        assertEquals(floorY, flag.session.y, 1e-9)
        // IDENTITY : toSession(Enu(e,n,u)) = V3(e, u, -n), puis y forcé au sol.
        assertEquals(1.0, flag.session.x, 1e-9)
        assertEquals(-2.0, flag.session.z, 1e-9)
        val lum = anchors.first { it.id == "lum-v1" }
        assertEquals(ArSiteOverlays.Kind.LUMINAIRE, lum.kind)
        assertEquals(item.code, lum.lsCode)
        assertEquals(LsSymbols.symbolOf(item), lum.symbolId)
        assertEquals(ArSymbols.shortLabel(catalog, item.code), lum.shortLabel)
        assertEquals("ses_soir", anchors.first { it.id == "flag-v2" }.visitId)
    }

    // ----- 3. Contours du site -----

    @Test fun siteContoursUseSelectedBuildingAndRectsInSessionFrame() {
        val origin = Fixtures.site.origin
        val building = listOf(
            LatLon(origin.lat, origin.lon),
            LatLon(origin.lat, origin.lon + 0.0002),
            LatLon(origin.lat + 0.0002, origin.lon + 0.0002),
            LatLon(origin.lat + 0.0002, origin.lon),
        )
        val feat = OsmFeature("way", 42L, mapOf("building" to "yes"), building, "building")
        val box = BBox(origin.lat - 0.0003, origin.lon - 0.0003, origin.lat + 0.0003, origin.lon + 0.0003)
        val site = Site(
            id = "site-1", name = "Test", origin = origin, originSource = "COORDINATES",
            createdUtcMs = 1_700_000_000_000, crs = listOf(ltp.crsInfo()),
            osm = OsmSnapshot("ign", "https://example", "q", 1_700_000_000_000, bbox = box, features = listOf(feat)),
            selectedBuildingOsmRef = feat.ref, selectedBuildingOsmRefs = listOf(feat.ref),
            bbox = box, rects = listOf(box),
        )
        val floorY = 0.20
        val rings = ArSiteOverlays.siteContours(site, Similarity2D.IDENTITY, floorY, ltp)
        assertTrue(rings.size >= 2, "bâtiment + rectangle attendus : ${rings.size}")
        rings.forEach { ring ->
            assertTrue(ring.size >= 4)
            assertTrue(ring.first().distanceTo(ring.last()) < 1e-6, "anneau non fermé")
            ring.forEach { p -> assertEquals(floorY, p.y, 1e-9) }
        }
        val zone = ZoneEvent("z1", Fixtures.stamp(1), listOf(Enu(0.0, 0.0, 0.0), Enu(2.0, 0.0, 0.0), Enu(2.0, 3.0, 0.0), Enu(0.0, 3.0, 0.0)))
        val withZone = ArSiteOverlays.siteContours(
            site, Similarity2D.IDENTITY, floorY, ltp,
            listOf(ArSiteOverlays.VisitEvents("ses_a", listOf(zone))),
        )
        assertTrue(withZone.size > rings.size, "la zone du relevé doit s'ajouter aux contours du site")
    }

    // ----- 5. Mesure A–B -----

    @Test fun measureLineJoinsAnchorsAndLabelShowsLength() {
        val a = V3(0.0, 0.2, 0.0)
        val b = V3(3.0, 0.2, 4.0) // 5 m
        val segs = ArMeasure.line(a, b)
        assertTrue(segs.any { (p, q) -> p.distanceTo(a) < 1e-9 && q.distanceTo(b) < 1e-9 || p.distanceTo(b) < 1e-9 && q.distanceTo(a) < 1e-9 })
        val (at, text) = ArMeasure.label(a, b)
        assertEquals("5,00 m", text)
        assertEquals(1.50, at.x, 1e-9)
        assertEquals(2.00, at.z, 1e-9)
        assertTrue(at.y > 0.2)
    }

    private fun flag(id: String, e: Double, n: Double) = ControlPointEvent(
        id, Fixtures.stamp(id.hashCode().toLong() and 0xffff), "CALIBRATION",
        LocalObservation(Fixtures.pose(1_000, e, n), ReferencePoint(id, Enu(e, n, 0.0), source = "MAP_TAP")),
        0.0, "MANUAL",
    )

    private fun luminaire(id: String, e: Double, n: Double, item: app.lumisite.core.catalog.LsItem) = SurveyEvent(
        id = id, t = Fixtures.stamp(2_000), kind = item.famille, tracking = TrackingState.TRACKING,
        positionSource = "MANUAL_MAP", manualSite = Enu(e, n, 1.2), payloadSchema = "lumisite-ls/2",
        payload = buildJsonObject { put("ls_code", item.code); put("ls_label", item.label); put("block_name", item.blocDwg ?: item.code) },
    )
}
