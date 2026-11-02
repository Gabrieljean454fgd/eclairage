package app.lumisite.core

import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.position.AnchorGroups
import app.lumisite.core.position.CalibrationSolver
import app.lumisite.core.position.ControlPoint
import app.lumisite.core.position.DisplayGlide
import app.lumisite.core.position.GnssClassifier
import app.lumisite.core.position.GnssObservation
import app.lumisite.core.position.GnssQuality
import app.lumisite.core.position.Tilt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PositioningTest {
    private val s = 1_000_000_000L
    private fun near(a: Vec2, b: Vec2, tol: Double) = assertTrue((a - b).norm() <= tol, "$a vs $b")
    private fun rot(v: Vec2, r: Double) = Vec2(cos(r) * v.x - sin(r) * v.y, sin(r) * v.x + cos(r) * v.y)
    private val truth = Similarity2D(1.0, 0.7, 12.0, -5.0)
    private fun pt(id: String, local: Vec2, t: Long = 0, reloc: Boolean = false, noise: Vec2 = Vec2(0.0, 0.0)) =
        ControlPoint(id, t, local, truth.apply(local) + noise, reloc)

    @Test fun onePointUsesItsHeading() {
        val sol = assertNotNull(CalibrationSolver.solve(listOf(pt("a", Vec2(1.0, 2.0)).copy(rotationRad = 0.7)), 0))
        assertEquals("ONE_POINT_HEADING", sol.method)
        near(sol.transform.apply(Vec2(5.0, -3.0)), truth.apply(Vec2(5.0, -3.0)), 1e-9)
    }

    @Test fun onePointWithoutHeadingIsRefused() { assertEquals(null, CalibrationSolver.solve(listOf(pt("a", Vec2(1.0, 2.0))), 0)) }

    @Test fun twoPointsRotation90() {
        val t90 = Similarity2D(1.0, PI / 2, 3.0, 4.0)
        val pts = listOf(ControlPoint("a", 0, Vec2(0.0, 0.0), t90.apply(Vec2(0.0, 0.0))), ControlPoint("b", 0, Vec2(10.0, 0.0), t90.apply(Vec2(10.0, 0.0))))
        val sol = assertNotNull(CalibrationSolver.solve(pts, 0))
        assertEquals(PI / 2, sol.transform.rotationRad, 1e-9); assertEquals(1.0, sol.transform.scale)
        near(sol.transform.apply(Vec2(0.0, 5.0)), Vec2(-2.0, 4.0), 1e-9)
        assertTrue(sol.residualsM.values.all { it < 1e-9 })
    }

    @Test fun threePlusPointsWithNoise() {
        val r = Random(1)
        val pts = (0 until 6).map { pt("p$it", Vec2(r.nextDouble(-15.0, 15.0), r.nextDouble(-15.0, 15.0)), noise = Vec2(r.nextDouble(-0.1, 0.1), r.nextDouble(-0.1, 0.1))) }
        val sol = assertNotNull(CalibrationSolver.solve(pts, 0))
        assertEquals("LEAST_SQUARES", sol.method); assertTrue(sol.rejected.isEmpty())
        assertTrue(abs(sol.transform.rotationRad - 0.7) < Math.toRadians(1.0)); assertTrue(sol.rmsM < 0.15)
        assertEquals(6, sol.residualsM.size)
    }

    @Test fun outlierRejectedButKeptInTable() {
        val pts = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(0.0, 10.0), Vec2(10.0, 10.0), Vec2(5.0, 5.0)).mapIndexed { i, v ->
            pt("p$i", v, noise = if (i == 4) Vec2(5.0, 0.0) else Vec2(0.02 * i, -0.01 * i))
        }
        val sol = assertNotNull(CalibrationSolver.solve(pts, 0))
        assertEquals(setOf("p4"), sol.rejected)
        assertTrue(sol.residualsM.getValue("p4") > 4.0)
        near(sol.transform.apply(Vec2(5.0, 5.0)), truth.apply(Vec2(5.0, 5.0)), 0.1)
    }

    @Test fun neverFewerThanTwoPoints() {
        val pts = listOf(pt("a", Vec2(0.0, 0.0)), pt("b", Vec2(10.0, 0.0), noise = Vec2(0.0, 6.0)), pt("c", Vec2(0.0, 10.0), noise = Vec2(-7.0, 0.0)))
        val sol = assertNotNull(CalibrationSolver.solve(pts, 0))
        assertTrue(pts.size - sol.rejected.size >= 2)
    }

    @Test fun relocalizationMovesThePositionByTheExpectedOffset() {
        // D2: calibration (1 point + heading), then « Je suis ici » 3 m east of where the drifted estimate puts the user.
        val cal = pt("cal", Vec2(0.0, 0.0)).copy(rotationRad = 0.7)
        val t0 = assertNotNull(CalibrationSolver.solve(listOf(cal), 0)).transform
        val here = Vec2(4.0, 1.0); val before = t0.apply(here); val target = before + Vec2(3.0, 0.0)
        val sol = assertNotNull(CalibrationSolver.solve(listOf(cal, ControlPoint("rel", 60 * s, here, target, relocalization = true)), 60 * s))
        near(sol.transform.apply(here), target, 1e-9)
        near(sol.transform.apply(here) - before, Vec2(3.0, 0.0), 1e-9)
        assertEquals("rel", sol.anchorId)
    }

    @Test fun cumulatedRelocalizationsReduceDriftError() {
        // D5: true path = 40 × 20 m rectangle walked at 1 m/s for 10 min; the AR frame drifts (1°/min + 1 cm/s).
        fun truePos(ts: Double): Vec2 { val d = ts % 120.0
            return when { d < 40 -> Vec2(d, 0.0); d < 60 -> Vec2(40.0, d - 40); d < 100 -> Vec2(40 - (d - 60), 20.0); else -> Vec2(0.0, 20 - (d - 100)) } }
        val omega = Math.toRadians(1.0) / 60.0
        fun localOf(ts: Double) = rot(truePos(ts), -omega * ts) + Vec2(0.01 * ts, 0.005 * ts)
        val cal = listOf(0.0, 10.0).map { ControlPoint("c$it", (it * s).toLong(), localOf(it), truePos(it)) }
        val initial = assertNotNull(CalibrationSolver.solve(cal, 10 * s)).transform
        val relocTimes = (1..9).map { it * 60.0 + 7 }
        val pts = cal.toMutableList()
        val segments = ArrayList<Pair<Double, Similarity2D>>(); segments += 10.0 to initial
        for (t in relocTimes) {
            pts += ControlPoint("r$t", (t * s).toLong(), localOf(t), truePos(t), relocalization = true)
            segments += t to assertNotNull(CalibrationSolver.solve(pts, (t * s).toLong())).transform
        }
        var errInitial = 0.0; var errSeg = 0.0; var n = 0
        var ts = 10.0
        while (ts < 600.0) {
            val seg = segments.last { it.first <= ts }.second
            errInitial += (initial.apply(localOf(ts)) - truePos(ts)).norm(); errSeg += (seg.apply(localOf(ts)) - truePos(ts)).norm(); n++
            ts += 5.0
        }
        assertTrue(errSeg / n < 0.5 * errInitial / n, "mean error with relocalizations ${errSeg / n} m vs ${errInitial / n} m")
    }

    @Test fun weightDecaysWithAge() {
        assertEquals(1.0, CalibrationSolver.weight(0)); assertEquals(Math.exp(-1.0), CalibrationSolver.weight(300 * s), 1e-12)
    }

    @Test fun gnssIndoorJumpingAccuracyBecomesUnusable() {
        val c = GnssClassifier()
        repeat(12) { c.update(it * 1000L, GnssObservation(8, listOf(40.0, 38.0, 36.0, 35.0), 5.0, 500)) }
        assertEquals(GnssQuality.FIABLE, c.quality)
        c.update(13_000, GnssObservation(8, listOf(30.0, 28.0, 27.0, 26.0), 40.0, 500)); assertEquals(GnssQuality.DEGRADE, c.quality)
        c.update(14_000, GnssObservation(8, listOf(30.0, 28.0, 27.0, 26.0), 12.0, 500)) // one good sample does not restore « fiable »
        assertEquals(GnssQuality.DEGRADE, c.quality)
        listOf(15_000L to 40.0, 16_000L to 55.0, 17_000L to 38.0, 18_000L to 47.0).forEach { (t, acc) -> c.update(t, GnssObservation(8, listOf(30.0, 28.0, 27.0, 26.0), acc, 500)) }
        assertEquals(GnssQuality.INUTILISABLE, c.quality); assertTrue(c.reasons.single().startsWith("précision"))
    }

    @Test fun gnssCriteria() {
        val c = GnssClassifier()
        assertEquals(listOf("3 satellite(s) utilisé(s)"), c.failures(GnssObservation(3, emptyList(), 5.0, 100)))
        assertEquals(listOf("C/N0 moyen 21 dB-Hz"), c.failures(GnssObservation(6, listOf(22.0, 21.0, 20.0, 21.0, 10.0), 5.0, 100)))
        assertEquals(listOf("fix âgé de 6 s"), c.failures(GnssObservation(6, listOf(40.0, 40.0, 40.0, 40.0), 5.0, 6_000)))
        assertTrue(c.failures(GnssObservation(null, emptyList(), 5.0, 100)).isEmpty())
        c.update(0, GnssObservation(null, emptyList(), 5.0, 100)); assertEquals(GnssQuality.DEGRADE, c.quality)
        c.update(9_000, GnssObservation(null, emptyList(), 5.0, 100)); assertEquals(GnssQuality.DEGRADE, c.quality)
        c.update(10_000, GnssObservation(null, emptyList(), 5.0, 100)); assertEquals(GnssQuality.FIABLE, c.quality)
    }

    @Test fun tiltFromSensors() {
        val h = Math.toRadians(-30.0) / 2
        assertEquals(-30.0, Tilt.fromArQuaternion(doubleArrayOf(sin(h), 0.0, 0.0, cos(h))), 1e-9)
        assertEquals(0.0, Tilt.fromArQuaternion(doubleArrayOf(0.0, sin(0.4), 0.0, cos(0.4))), 1e-9)
        assertEquals(-90.0, Tilt.fromRotationMatrix(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)), 1e-6)
    }

    @Test fun displayGlideIsHalfwayAfter250ms() {
        val g = DisplayGlide(); g.target(Vec2(0.0, 0.0), 0); g.target(Vec2(10.0, 0.0), 1000)
        near(assertNotNull(g.at(1250)), Vec2(5.0, 0.0), 1e-9); near(assertNotNull(g.at(2000)), Vec2(10.0, 0.0), 1e-9)
    }

    @Test fun objectsShareAnAnchorWithin8m() {
        val g = listOf(AnchorGroups.Group("a", Vec2(0.0, 0.0)), AnchorGroups.Group("b", Vec2(20.0, 0.0)))
        assertEquals("a", AnchorGroups.assign(g, Vec2(5.0, 3.0))?.id)
        assertEquals("b", AnchorGroups.assign(g, Vec2(14.0, 0.0))?.id)
        assertNull(AnchorGroups.assign(g, Vec2(10.0, 0.0)))
    }
}
