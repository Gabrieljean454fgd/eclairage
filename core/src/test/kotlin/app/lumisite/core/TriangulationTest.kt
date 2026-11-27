package app.lumisite.core

import app.lumisite.core.compute.Triangulation
import app.lumisite.core.compute.Triangulation.Result
import app.lumisite.core.compute.V3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TriangulationTest {
    private val a = V3(0.0, 1.35, 0.0)
    private val b = V3(1.1, 1.45, 0.0)
    private val ceilingLight = V3(0.4, 2.8, -2.0)

    @Test fun matchingObservationsRecoverAPointOnTheCeiling() {
        val measured = assertIs<Result.Point>(Triangulation.intersect(a, ceilingLight - a, b, ceilingLight - b))
        assertEquals(0.0, measured.position.distanceTo(ceilingLight), 1e-9)
        assertEquals(0.0, measured.rayGapM, 1e-9)
    }

    @Test fun turningInPlaceDoesNotMeasureDistance() {
        assertEquals(Result.Refused.BASELINE, Triangulation.intersect(a, ceilingLight - a, a, ceilingLight - b))
    }

    @Test fun casesWithWeakOrInconsistentGeometryAreRefused() {
        val alongZ = V3(0.0, 0.0, -1.0)
        assertEquals(Result.Refused.ANGLE, Triangulation.intersect(a, alongZ, b, alongZ))
        assertEquals(Result.Refused.OUT_OF_RANGE, Triangulation.intersect(a, a - ceilingLight, b, ceilingLight - b))
        assertEquals(Result.Refused.RAY_GAP,
            Triangulation.intersect(a, ceilingLight - a, b, ceilingLight.copy(y = 4.0) - b))
    }
}
