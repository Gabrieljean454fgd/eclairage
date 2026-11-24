package app.lumisite.core

import app.lumisite.core.compute.ArPlaneDiagnose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Build 248 : diagnostic des plans ARCore, logique pure testable en JVM. */
class ArPlaneDiagnoseTest {
    private fun desc(type: String, state: String, area: Double, vertices: Int) =
        ArPlaneDiagnose.PlaneDesc(type, state, area, vertices)

    @Test fun emptyStatsAreZero() {
        val s = ArPlaneDiagnose.stats(emptyList())
        assertEquals(0, s.totalPlanes)
        assertEquals(0, s.trackingPlanes)
        assertEquals(emptyMap(), s.byType)
        assertEquals(emptyMap(), s.byState)
        assertEquals(0, s.validPolygons)
        assertEquals(0, s.invalidPolygons)
        assertEquals(0.0, s.avgAreaM2)
        assertFalse(s.rendererGotPolygons)
    }

    @Test fun countsByTypeAndState() {
        val s = ArPlaneDiagnose.stats(listOf(
            desc("HORIZONTAL_UPWARD_FACING", "TRACKING", 1.0, 4),
            desc("HORIZONTAL_UPWARD_FACING", "PAUSED", 0.5, 4),
            desc("VERTICAL", "TRACKING", 2.0, 5),
            desc("HORIZONTAL_DOWNWARD_FACING", "TRACKING", 0.8, 3),
        ))
        assertEquals(4, s.totalPlanes)
        assertEquals(3, s.trackingPlanes)
        assertEquals(mapOf("HORIZONTAL_UPWARD_FACING" to 2, "VERTICAL" to 1, "HORIZONTAL_DOWNWARD_FACING" to 1), s.byType)
        assertEquals(mapOf("TRACKING" to 3, "PAUSED" to 1), s.byState)
    }

    @Test fun polygonValidityAndAreaStats() {
        val s = ArPlaneDiagnose.stats(listOf(
            desc("HORIZONTAL_UPWARD_FACING", "TRACKING", 4.0, 4),
            desc("VERTICAL", "TRACKING", -0.1, 4), // invalid area
            desc("HORIZONTAL_DOWNWARD_FACING", "TRACKING", 1.0, 2), // invalid vertex count
        ))
        assertEquals(1, s.validPolygons)
        assertEquals(2, s.invalidPolygons)
        assertEquals(4.0, s.minAreaM2, 1e-9)
        assertEquals(4.0, s.maxAreaM2, 1e-9)
        assertEquals(4.0, s.avgAreaM2, 1e-9)
        assertTrue(s.rendererGotPolygons)
    }

    @Test fun rendererGotPolygonsOnlyWhenTrackingAndValid() {
        val paused = ArPlaneDiagnose.stats(listOf(
            desc("HORIZONTAL_UPWARD_FACING", "PAUSED", 4.0, 4),
        ))
        assertFalse(paused.rendererGotPolygons)
        val invalid = ArPlaneDiagnose.stats(listOf(
            desc("VERTICAL", "TRACKING", 4.0, 2),
        ))
        assertFalse(invalid.rendererGotPolygons)
    }
}
