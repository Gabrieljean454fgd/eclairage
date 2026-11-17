package app.lumisite.core

import app.lumisite.core.geo.Vec2
import app.lumisite.core.position.CalibrationQuality
import app.lumisite.core.position.CalibrationQuality.Level
import app.lumisite.core.position.CalibrationSolver
import app.lumisite.core.position.ControlPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v176c: 5th point = independent control, quality follows the residual of each point and of the control. */
class CalibrationControlTest {
    private fun cp(id: String, x: Double, y: Double, dx: Double = 0.0) = ControlPoint(id, 0L, Vec2(x + dx, y), Vec2(x, y))
    private val square = listOf(cp("a", 0.0, 0.0), cp("b", 6.0, 0.0), cp("c", 6.0, 6.0), cp("d", 0.0, 6.0))

    @Test fun fifthPointIsTheControl() {
        assertFalse(CalibrationQuality.isControl(3)); assertTrue(CalibrationQuality.isControl(4)); assertFalse(CalibrationQuality.isControl(5))
    }

    @Test fun controlResidualGradesTheCalibration() {
        val sol = CalibrationSolver.solve(square, 0L)!!
        assertEquals(Level.BON, CalibrationQuality.grade(square, sol, CalibrationQuality.controlResidual(sol, cp("e", 3.0, 3.0, 0.2))).level)
        val off = CalibrationQuality.controlResidual(sol, cp("e", 3.0, 3.0, 0.7))!!
        assertEquals(0.7, off, 1e-6)
        val g = CalibrationQuality.grade(square, sol, off)
        assertEquals(Level.MOYEN, g.level); assertTrue(g.reasons.any { it.startsWith("contrôle") })
        assertEquals(Level.INSUFFISANT, CalibrationQuality.grade(square, sol, 1.3).level)
    }

    @Test fun oneBadPointRefusesGoodEvenWithALowRms() {
        val pts = square + listOf(cp("e", 3.0, 0.0), cp("f", 3.0, 6.0), cp("g", 0.0, 3.0), cp("h", 6.0, 3.0), cp("i", 3.0, 3.0, 1.4))
        val sol = CalibrationSolver.solve(pts, 0L)!!
        assertTrue(sol.rmsM <= CalibrationQuality.GOOD_RMS_M)
        assertTrue(sol.residualsM.getValue("i") > CalibrationQuality.ALERT_M)
        assertEquals(Level.MOYEN, CalibrationQuality.grade(pts, sol).level)
    }
}
