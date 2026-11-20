package app.lumisite.core

import app.lumisite.core.compute.Aim
import app.lumisite.core.compute.AimResult
import app.lumisite.core.compute.V3
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Retour terrain build 199, points 2c et 2f (« ar-drapeaux-plafond »).
 * 2c : transformation inverse carte → AR pour recréer les ancres (repères, luminaires) à la reprise d'une session.
 * 2f : intersection rayon / plafond horizontal virtuel (sol détecté + hauteur sous plafond saisie), source
 * AR_CEILING_ESTIMATED — jamais de repli silencieux.
 */
class ArDrapeauxPlafondTest {
    private val cam = V3(0.0, 1.5, 0.0)

    // ------------------------------------------------------------------ 2c : transformation inverse carte -> AR

    @Test fun invertIsTheExactInverseOfApply() {
        // Calibration réaliste : rotation ~37°, échelle fixée à 1, translation de l'ordre de la centaine de mètres.
        val tr = Similarity2D(1.0, Math.toRadians(37.0), 123.45, -87.6)
        for (p in listOf(Vec2(0.0, 0.0), Vec2(1.2, -3.4), Vec2(-50.0, 250.0))) {
            val back = tr.invert(tr.apply(p))
            assertEquals(p.x, back.x, 1e-9); assertEquals(p.y, back.y, 1e-9)
        }
        // Sens inverse : apply(invert(cible)) = cible, aussi pour une échelle mesurée ≠ 1.
        val tr2 = Similarity2D(1.013, Math.toRadians(-121.0), -40.0, 512.8)
        for (s in listOf(Vec2(4.0, 9.0), Vec2(-17.5, 0.3))) {
            val fwd = tr2.apply(tr2.invert(s))
            assertEquals(s.x, fwd.x, 1e-9); assertEquals(s.y, fwd.y, 1e-9)
        }
    }

    @Test fun mapToArRoundTripStaysUnder1cm() {
        val tr = Similarity2D(1.0, Math.toRadians(37.0), 123.45, -87.6)
        // Repère sur le sol (u = 0) et luminaire au plafond (u = 2.6) : aller-retour carte -> AR -> carte < 1 cm.
        for (site in listOf(Enu(10.0, 20.0, 0.0), Enu(-3.5, 41.2, 2.6), Enu(0.0, 0.0, 0.0))) {
            val back = Aim.toSite(Aim.toSession(site, tr), tr)
            val d = sqrt((back.e - site.e) * (back.e - site.e) + (back.n - site.n) * (back.n - site.n) + (back.u - site.u) * (back.u - site.u))
            assertEquals(true, d < 0.01, "aller-retour carte→AR→carte à $d m (> 1 cm) pour $site")
        }
        // Et AR -> carte -> AR : le point de session retrouvé est le point de départ (u <-> y).
        val p = V3(2.5, 2.6, -7.0)
        val back = Aim.toSession(Aim.toSite(p, tr), tr)
        assertEquals(0.0, p.distanceTo(back), 1e-9)
    }

    @Test fun toSessionMatchesTheSessionPlaneConvention() {
        // Transform identité : plan session (x, -z) = site (e, n) ; la hauteur u devient y.
        val v = Aim.toSession(Enu(4.0, 9.0, 2.6), Similarity2D.IDENTITY)
        assertEquals(V3(4.0, 2.6, -9.0), v)
    }

    // ------------------------------------------------------------------ 2f : plafond virtuel (sol + hauteur saisie)

    @Test fun virtualCeilingHitsAtFloorPlusHeight() {
        // Sol détecté à y = 0, hauteur sous plafond saisie 2,60 m -> plan virtuel à y = 2,6.
        val floor = Aim.Plane(V3(0.0, 0.0, 0.0), Aim.UP, "AR_PLANE")
        val d = V3(0.0, 1.0, -1.0) // 45° vers le haut et l'avant
        val r = Aim.resolve(cam, d, listOf(floor), null, tracking = true, fallbacks = false, virtualCeilingY = 2.6)
        val hit = assertIs<AimResult.Hit>(r)
        assertEquals(Aim.SRC_CEILING_ESTIMATED, hit.source)
        assertEquals(2.6, hit.point.y, 1e-9)
        assertEquals(-1.1, hit.point.z, 1e-9) // cam.y 1.5 + 1.1 = 2.6 -> z = -1.1
    }

    @Test fun detectedCeilingWinsOverTheVirtualOne() {
        val ceiling = Aim.Plane(V3(0.0, 2.8, 0.0), V3(0.0, -1.0, 0.0), "AR_CEILING")
        val r = Aim.resolve(cam, V3(0.0, 1.0, -0.5), listOf(ceiling), null, tracking = true,
            fallbacks = false, virtualCeilingY = 2.6)
        val hit = assertIs<AimResult.Hit>(r)
        assertEquals("AR_CEILING", hit.source); assertEquals(2.8, hit.point.y, 1e-9)
    }

    @Test fun noVirtualCeilingMeansToCompleteNeverSilent() {
        // Sans hauteur saisie : pas de plafond virtuel, la visée reste « position à compléter ».
        val r = Aim.resolve(cam, V3(0.0, 1.0, -1.0), emptyList(), null, tracking = true, fallbacks = false)
        assertEquals(AimResult.ToComplete, r)
    }

    @Test fun virtualCeilingNeverAnswersADownwardAim() {
        // Visée vers le bas avec un plafond virtuel : le rayon part à l'opposé du plan -> à compléter (pas de point faux).
        val r = Aim.resolve(cam, V3(0.0, -1.0, -1.0), emptyList(), null, tracking = true,
            fallbacks = false, virtualCeilingY = 2.6)
        assertEquals(AimResult.ToComplete, r)
    }

    @Test fun virtualCeilingGrazingIsRefused() {
        // |n·d| < 0.03 par rapport au plan horizontal virtuel : refus rasante comme pour les plans détectés.
        val r = Aim.resolve(cam, V3(0.0, 0.02, -1.0), emptyList(), null, tracking = true,
            fallbacks = false, virtualCeilingY = 2.6)
        assertEquals(AimResult.Grazing, r)
    }

    @Test fun virtualCeilingAboveMaxRangeIsMiss() {
        // Plafond virtuel à 25 m au-dessus de la caméra, visée presque horizontale vers le haut mais non rasante :
        // l'intersection dépasse MAX_RANGE_M -> pas de point.
        val r = Aim.resolve(cam, V3(0.0, 0.5, -1.0), emptyList(), null, tracking = true,
            fallbacks = false, virtualCeilingY = cam.y + 25.0)
        assertEquals(AimResult.ToComplete, r)
    }

    // ------------------------------------------------------------------ modèle : hauteur sous plafond par site

    @Test fun siteWithoutCeilingHeightStillReads() {
        // Les site.json écrits avant la 2f n'ont pas le champ : la valeur par défaut les garde lisibles.
        val s = app.lumisite.core.journal.LumiJson.encodeToString(
            app.lumisite.core.model.Site.serializer(), Fixtures.site.copy(ceilingHeightM = 2.6))
        val back = app.lumisite.core.journal.LumiJson.decodeFromString(app.lumisite.core.model.Site.serializer(), s)
        assertEquals(2.6, back.ceilingHeightM)
        val old = """{"id":"site-1","name":"Test","origin":{"lat":48.8566,"lon":2.3522},"originSource":"COORDINATES","createdUtcMs":1700000000000,"crs":[]}"""
        val backOld = app.lumisite.core.journal.LumiJson.decodeFromString(app.lumisite.core.model.Site.serializer(), old)
        assertNull(backOld.ceilingHeightM)
    }
}
