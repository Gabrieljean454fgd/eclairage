package app.lumisite.core

import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.position.CalibrationQuality
import app.lumisite.core.position.CalibrationSolver
import app.lumisite.core.position.ControlPoint
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paquet v6, anomalies 5 et 6 : recalage sur 2, 3, 4 et 6 points d'un bâtiment de 30 × 12 m, dont un point éloigné et
 * opposé. Vérité connue (rotation 30°, translation), AR bruitée (0,1 m) avec 1 % d'erreur d'échelle ARCore.
 */
class RecalageMultiPointTest {
    private val s = 1_000_000_000L
    private val truth = Similarity2D.fromPointAndRotation(Vec2(0.0, 0.0), Vec2(100.0, 50.0), Math.toRadians(30.0))
    private val inv = Similarity2D.fromPointAndRotation(Vec2(100.0, 50.0), Vec2(0.0, 0.0), Math.toRadians(-30.0))
    private val rnd = Random(7)
    /** Map points (site E/N) in the order they are taken: two close ones, the far opposite corner, then the others. */
    private val sites = listOf(Vec2(0.0, 0.0), Vec2(5.0, 0.0), Vec2(30.0, 12.0), Vec2(30.0, 0.0), Vec2(0.0, 12.0), Vec2(15.0, 6.0))
        .map { truth.apply(it) }
    private fun ar(site: Vec2): Vec2 { val l = inv.apply(site); return Vec2(l.x * 1.01 + rnd.nextDouble(-0.1, 0.1), l.y * 1.01 + rnd.nextDouble(-0.1, 0.1)) }
    private val points = sites.mapIndexed { i, t -> ControlPoint("p${i + 1}", i * 60 * s, ar(t), t, false) }
    /** Independent check points (not in the computation) spread over the building. */
    private val checks = listOf(Vec2(10.0, 3.0), Vec2(25.0, 9.0), Vec2(28.0, 2.0), Vec2(3.0, 10.0)).map { truth.apply(it) }.map { it to ar(it) }

    private fun errorWith(n: Int): Pair<Double, Double> {
        val sol = CalibrationSolver.solve(points.take(n), (n - 1) * 60 * s, fallbackRotationRad = null)!!
        val err = checks.map { (site, l) -> (sol.transform.apply(l) - site).norm() }.average()
        return err to Math.toDegrees(sol.transform.rotationRad)
    }

    @Test fun errorDecreasesAndDirectionIsRightWith2346Points() {
        val r = listOf(2, 3, 4, 6).associateWith(::errorWith)
        r.forEach { (n, v) -> println("recalage $n points : erreur moyenne aux points de contrôle %.2f m, rotation %.2f°".format(v.first, v.second)) }
        r.values.forEach { (_, rot) -> assertTrue(abs(rot - 30.0) < 3.0, "sens / rotation incorrects : $rot°") }
        assertTrue(r.getValue(3).first < r.getValue(2).first, "le point éloigné et opposé doit réduire l'erreur")
        assertTrue(r.getValue(4).first <= r.getValue(3).first + 0.05)
        assertTrue(r.getValue(6).first <= r.getValue(4).first + 0.05)
        assertTrue(r.getValue(6).first < 0.35, "6 points bien répartis : erreur ${r.getValue(6).first}")
    }

    @Test fun farOppositePointMovesTheCursorTheRightWay() {
        // Walking from the first corner towards the far opposite one: the computed site position must move towards it.
        val sol = CalibrationSolver.solve(points.take(3), 120 * s)!!
        val start = sol.transform.apply(points[0].local); val far = sol.transform.apply(points[2].local)
        val dirTruth = sites[2] - sites[0]; val dirComp = far - start
        assertTrue(dirTruth.x * dirComp.x + dirTruth.y * dirComp.y > 0.99 * dirTruth.norm() * dirComp.norm(), "cap inversé ou tourné")
    }

    @Test fun alignedPointsAreGradedInsufficient() {
        val line = listOf(0.0, 8.0, 16.0, 24.0).mapIndexed { i, x -> val t = truth.apply(Vec2(x, 0.05 * i)); ControlPoint("a$i", 0, inv.apply(t), t, false) }
        val sol = CalibrationSolver.solve(line, 0, fallbackRotationRad = Math.toRadians(30.0))
        assertEquals(CalibrationQuality.Level.INSUFFISANT, CalibrationQuality.grade(line, sol).level)
        val good = CalibrationSolver.solve(points, 5 * 60 * s)
        val g = CalibrationQuality.grade(points, good)
        println("qualité 6 points : $g rms=${good?.rmsM}")
        assertEquals(CalibrationQuality.Level.BON, g.level)
        val two = CalibrationSolver.solve(points.take(2), 60 * s)
        assertEquals(CalibrationQuality.Level.INSUFFISANT, CalibrationQuality.grade(points.take(2), two).level)
    }

    @Test fun residualAboveOneMetreIsNeverGood() {
        val noisy = points.mapIndexed { i, p -> if (i % 2 == 0) p.copy(target = p.target + Vec2(1.3, -1.3)) else p.copy(target = p.target - Vec2(1.3, -1.3)) }
        val sol = CalibrationSolver.solve(noisy, 5 * 60 * s)!!
        val g = CalibrationQuality.grade(noisy, sol)
        println("résidu ${sol.rmsM} → $g")
        assertTrue(sol.rmsM > 1.0)
        assertEquals(CalibrationQuality.Level.INSUFFISANT, g.level)
        assertTrue(g.reasons.any { it.startsWith("imprécis") })
    }
}
