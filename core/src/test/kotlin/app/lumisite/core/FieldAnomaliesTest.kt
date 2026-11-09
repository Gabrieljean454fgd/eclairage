package app.lumisite.core

import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.position.CalibrationSolver
import app.lumisite.core.position.ControlPoint
import app.lumisite.core.position.ControlPointCheck
import kotlin.test.assertNotNull
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reproductions (synthetic, no client data) of the Pixel 7 field anomalies of 08/11 (v161). */
class FieldAnomaliesTest {
    private val s = 1_000_000_000L
    private fun deg(r: Double) = Math.toDegrees(r)

    /** Scene 1: 4 AR points tied to the same map point, no heading → no calibration can be computed. */
    @Test fun scene1SameMapPointGivesNoCalibration() {
        val map = Vec2(10.0, 20.0)
        val pts = listOf(Vec2(0.0, 0.0), Vec2(3.0, 0.0), Vec2(3.0, 4.0), Vec2(0.0, 4.0)).mapIndexed { i, l -> ControlPoint("p$i", i * 30 * s, l, map, false) }
        val sol = CalibrationSolver.solve(pts, 4 * 30 * s)
        println("scene1: solution=$sol")
        assertNull(sol)
        // Correction: the 2nd calibration point on the same map point is refused before it enters the table.
        assertNotNull(ControlPointCheck.refusal(pts.take(1), pts[1])).also { assertTrue(!it.overridable) }
        assertNull(ControlPointCheck.refusal(pts.take(1), ControlPoint("q", 0, Vec2(3.0, 0.0), map + Vec2(3.0, 0.0), false)))
    }

    private val truth2 = Similarity2D.fromPointAndRotation(Vec2(0.0, 0.0), Vec2(5.0, 5.0), Math.toRadians(63.59))
    private fun scene2(nGood: Int, gapS: Long, reloc7: Boolean): Pair<app.lumisite.core.position.Solution, app.lumisite.core.position.Solution> {
        val layout = listOf(Vec2(0.0, 0.0), Vec2(4.0, 0.0), Vec2(4.0, 6.0), Vec2(0.0, 6.0), Vec2(2.0, 3.0)).take(nGood)
        val good = layout.mapIndexed { i, l -> ControlPoint("p${i + 1}", i * 30 * s, l, truth2.apply(l), false) }
        val t6 = (nGood * 30 + gapS) * s
        val l6 = Vec2(1.0, 1.0); val l7 = Vec2(1.04, 1.03)
        val p6 = ControlPoint("p6", t6, l6, truth2.apply(l6), false)
        val p7 = ControlPoint("p7", t6 + 10 * s, l7, truth2.apply(l6) + Vec2(9.77, 0.0), reloc7)
        return CalibrationSolver.solve(good + p6, t6)!! to CalibrationSolver.solve(good + p6 + p7, t6 + 10 * s)!!
    }

    /** Scene 2: good points (rotation +63.59°), then points 6 and 7 taken ~5 cm apart in AR but 9.77 m apart on the map. */
    @Test fun scene2ContradictoryPairFlipsRotation() {
        var reproduced = false
        for (n in listOf(2, 3, 5)) for (gap in listOf(0L, 300L, 900L)) for (r in listOf(false, true)) {
            val (b, a) = scene2(n, gap, r)
            val bad = kotlin.math.abs(deg(a.transform.rotationRad) - 63.59) > 10.0 || a.rmsM > 1.0
            println("scene2 n=$n gap=${gap}s p7reloc=$r : before %.2f° rms %.2f ; after %.2f° rms %.2f rejected=%s %s".format(
                deg(b.transform.rotationRad), b.rmsM, deg(a.transform.rotationRad), a.rmsM, a.rejected, if (bad) "ANOMALIE" else ""))
            reproduced = reproduced || bad
        }
        assertTrue(!reproduced, "rotation basculée par une correspondance contradictoire")
        // The pair is also refused at entry (phone moved 0.05 m, map points 9.77 m apart).
        val (_, a) = scene2(3, 300, true)
        assertTrue("p7" in a.rejected)
    }

    /**
     * Décision A (09/11): a contradictory pair is refused by default but can be forced (« Recaler quand même ») after a
     * real ARCore jump. The forced point is kept by the solver and the older points are not discarded automatically.
     */
    @Test fun contradictionIsOverridableAndForcedPointIsKept() {
        val truth = Similarity2D.fromPointAndRotation(Vec2(0.0, 0.0), Vec2(5.0, 5.0), Math.toRadians(30.0))
        val layout = listOf(Vec2(0.0, 0.0), Vec2(8.0, 0.0), Vec2(8.0, 6.0))
        val old = layout.mapIndexed { i, l -> ControlPoint("p$i", i * 20 * s, l, truth.apply(l), false) }
        // ARCore jump of 12 m after tracking was lost: the phone frame moved, the real map point is still the true one.
        val jumped = Vec2(0.0, 6.0) + Vec2(12.0, 0.0)
        val cand = ControlPoint("j", 70 * s, jumped, truth.apply(Vec2(0.0, 6.0)), true)
        val r = assertNotNull(ControlPointCheck.refusal(old, cand))
        assertTrue(r.overridable, r.message)
        val forced = cand.copy(forced = true)
        assertTrue(ControlPointCheck.inconsistent(old + forced).isEmpty())
        val sol = assertNotNull(CalibrationSolver.solve(old + forced, 70 * s))
        assertTrue("j" !in sol.rejected && sol.anchorId == "j", "forced relocalization kept as anchor: $sol")
        assertTrue(old.none { it.id in sol.rejected }, "older points not discarded automatically: ${sol.rejected}")
        // The newest relocalization is honoured exactly: the phone is where the user said.
        assertTrue((sol.transform.apply(jumped) - truth.apply(Vec2(0.0, 6.0))).norm() < 1e-6)
        // Without forcing, the solver removes the contradictory point (older journals).
        assertTrue("j" in CalibrationSolver.solve(old + cand, 70 * s)!!.rejected)
    }
}
