package app.lumisite.core

import app.lumisite.core.compute.SupportFromTilt
import app.lumisite.core.compute.SupportFromTilt.Support
import app.lumisite.core.compute.V3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Retour terrain build 199, point 6 : support déduit de l'inclinaison (vers le bas = sol, ~horizontal = mur, vers le haut = plafond). */
class SupportFromTiltTest {
    @Test fun gravityFlatScreenUpIsFloor() {
        val e = SupportFromTilt.elevationFromGravity(0.0, 0.0, 9.81)!!
        assertEquals(-90.0, e, 1e-6)
        assertEquals(Support.SOL, SupportFromTilt.classify(e))
    }

    @Test fun gravityUprightPortraitIsWall() {
        val e = SupportFromTilt.elevationFromGravity(0.0, 9.81, 0.0)!!
        assertEquals(0.0, e, 1e-6)
        assertEquals(Support.MUR, SupportFromTilt.classify(e))
    }

    @Test fun gravityScreenDownIsCeiling() {
        val e = SupportFromTilt.elevationFromGravity(0.0, 0.0, -9.81)!!
        assertEquals(90.0, e, 1e-6)
        assertEquals(Support.PLAFOND, SupportFromTilt.classify(e))
    }

    @Test fun landscapeUprightIsWallToo() {
        assertEquals(Support.MUR, SupportFromTilt.classify(SupportFromTilt.elevationFromGravity(9.81, 0.0, 0.0)!!))
    }

    @Test fun zeroGravityGivesNull() { assertNull(SupportFromTilt.elevationFromGravity(0.0, 0.0, 0.0)) }

    @Test fun arcoreForwardMatchesGravity() {
        assertEquals(-45.0, SupportFromTilt.elevationFromForward(V3(0.0, -1.0, -1.0))!!, 1e-6)
        assertEquals(60.0, SupportFromTilt.elevationFromForward(V3(0.0, kotlin.math.sqrt(3.0), -1.0))!!, 1e-6)
        assertNull(SupportFromTilt.elevationFromForward(V3(0.0, 0.0, 0.0)))
    }

    @Test fun thresholdsWithoutHistory() {
        assertEquals(Support.SOL, SupportFromTilt.classify(-36.0))
        assertEquals(Support.MUR, SupportFromTilt.classify(-34.0))
        assertEquals(Support.MUR, SupportFromTilt.classify(34.0))
        assertEquals(Support.PLAFOND, SupportFromTilt.classify(36.0))
    }

    @Test fun hysteresisAvoidsFlicker() {
        // Venant du mur, on reste mur jusqu'à −40° / +40°.
        assertEquals(Support.MUR, SupportFromTilt.classify(-38.0, Support.MUR))
        assertEquals(Support.SOL, SupportFromTilt.classify(-41.0, Support.MUR))
        assertEquals(Support.MUR, SupportFromTilt.classify(38.0, Support.MUR))
        assertEquals(Support.PLAFOND, SupportFromTilt.classify(41.0, Support.MUR))
        // Venant du sol / plafond, on y reste jusqu'à −30° / +30°.
        assertEquals(Support.SOL, SupportFromTilt.classify(-32.0, Support.SOL))
        assertEquals(Support.MUR, SupportFromTilt.classify(-29.0, Support.SOL))
        assertEquals(Support.PLAFOND, SupportFromTilt.classify(32.0, Support.PLAFOND))
        assertEquals(Support.MUR, SupportFromTilt.classify(29.0, Support.PLAFOND))
    }

    @Test fun planeSourcesMatchAimConvention() {
        assertEquals("AR_PLANE", SupportFromTilt.planeSource(Support.SOL))
        assertEquals("AR_WALL", SupportFromTilt.planeSource(Support.MUR))
        assertEquals("AR_CEILING", SupportFromTilt.planeSource(Support.PLAFOND))
    }
}
