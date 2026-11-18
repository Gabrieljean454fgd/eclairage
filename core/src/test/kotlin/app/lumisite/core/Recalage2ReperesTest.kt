package app.lumisite.core

import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.position.TwoMarkerRecal
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v176f F3 — « Recaler sur 2 repères » à la reprise d'une session. Vérité connue (rotation 30°, translation) :
 * les deux visées retrouvent la transformation, le 3e repère (hors calcul) mesure l'écart résiduel.
 * Cas couverts : visées alignées avec le plan (rotation nulle), cas nominal, résidu de contrôle > 0,5 m,
 * repères trop proches, ordre de visée conseillé.
 */
class Recalage2ReperesTest {
    private val truth = Similarity2D.fromPointAndRotation(Vec2(0.0, 0.0), Vec2(100.0, 50.0), Math.toRadians(30.0))
    private val inv = Similarity2D.fromPointAndRotation(Vec2(100.0, 50.0), Vec2(0.0, 0.0), Math.toRadians(-30.0))

    private fun obs(site: Vec2): TwoMarkerRecal.Observation = TwoMarkerRecal.Observation(inv.apply(site), site)

    @Test fun deuxViseesRetrouventLaTransformation() {
        val r1 = obs(Vec2(2.0, 1.0)); val r2 = obs(Vec2(28.0, 11.0))
        val est = TwoMarkerRecal.estimate(r1, r2)!!
        assertTrue(abs(Math.toDegrees(est.transform.rotationRad) - 30.0) < 1e-6, "rotation ${est.transform.rotationRad}")
        est.residualsM.forEach { assertTrue(it < 1e-9, "résidu $it") }
        // Un point arbitraire du bureau est remis à sa place plan.
        val p = Vec2(15.0, 6.0)
        assertTrue((est.transform.apply(inv.apply(p)) - p).norm() < 1e-9)
    }

    @Test fun viseesAligneesAvecLePlanDonnentUneTranslationPure() {
        // Cas « alignés » : la direction entre les deux points visés est déjà celle des repères (rotation ≈ 0).
        val r1 = TwoMarkerRecal.Observation(Vec2(0.0, 0.0), Vec2(10.0, 4.0))
        val r2 = TwoMarkerRecal.Observation(Vec2(6.0, 0.0), Vec2(16.0, 4.0))
        val est = TwoMarkerRecal.estimate(r1, r2)!!
        assertTrue(abs(Math.toDegrees(est.transform.rotationRad)) < 1e-9, "rotation ${est.transform.rotationRad}")
        assertTrue((est.transform.apply(Vec2(0.0, 0.0)) - Vec2(10.0, 4.0)).norm() < 1e-9)
        est.residualsM.forEach { assertTrue(it < 1e-9) }
    }

    @Test fun controleAuDelaDe50cmEstMesure() {
        val r1 = obs(Vec2(2.0, 1.0)); val r2 = obs(Vec2(28.0, 11.0))
        val est = TwoMarkerRecal.estimate(r1, r2)!!
        // 3e repère visé : l'utilisateur vise 0,8 m à côté de la marque réelle → écart détecté.
        val controle = TwoMarkerRecal.Observation(inv.apply(Vec2(15.0, 9.0)) + Vec2(0.8, 0.0), Vec2(15.0, 9.0))
        val d = TwoMarkerRecal.controlResidualM(est.transform, controle)
        assertTrue(d > 0.5, "écart du 3e repère = $d m")
        // Le même repère bien visé reste sous le seuil d'acceptation (0,5 m, bureau éclairé).
        assertTrue(TwoMarkerRecal.controlResidualM(est.transform, obs(Vec2(15.0, 9.0))) < 0.5)
    }

    @Test fun reperesTropProchesNeDonnentPasDeTransformation() {
        val r1 = obs(Vec2(2.0, 1.0)); val r2 = obs(Vec2(2.3, 1.0))
        assertNull(TwoMarkerRecal.estimate(r1, r2))
    }

    @Test fun ordreConseilleViseLaPaireLaPlusEcartee() {
        val targets = listOf(Vec2(0.0, 0.0), Vec2(3.0, 0.5), Vec2(30.0, 12.0), Vec2(4.0, 1.0))
        val ordre = TwoMarkerRecal.suggestedOrder(targets)
        assertEquals(listOf(0, 2, 1, 3), ordre)
        assertEquals(listOf(0, 1), TwoMarkerRecal.suggestedOrder(targets.take(2)))
    }
}
