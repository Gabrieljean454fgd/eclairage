package app.lumisite.core

import app.lumisite.core.compute.Support
import app.lumisite.core.compute.SupportFromTilt
import kotlin.test.Test
import kotlin.test.assertEquals

class SupportFromTiltTest {
    private fun c(e: Double, p: Support? = null) = SupportFromTilt.classify(e, p)

    @Test fun straightDownLevelAndUp() {
        assertEquals(Support.SOL, c(-90.0)); assertEquals(Support.MUR, c(0.0)); assertEquals(Support.PLAFOND, c(90.0))
    }

    @Test fun thresholdsAt35() {
        assertEquals(Support.MUR, c(-34.0)); assertEquals(Support.SOL, c(-36.0))
        assertEquals(Support.MUR, c(34.0)); assertEquals(Support.PLAFOND, c(36.0))
    }

    @Test fun fromWallStayWallUntil40() {
        assertEquals(Support.MUR, c(-38.0, Support.MUR)); assertEquals(Support.SOL, c(-41.0, Support.MUR))
        assertEquals(Support.MUR, c(38.0, Support.MUR)); assertEquals(Support.PLAFOND, c(41.0, Support.MUR))
    }

    @Test fun fromFloorOrCeilingStayUntil30() {
        assertEquals(Support.SOL, c(-32.0, Support.SOL)); assertEquals(Support.MUR, c(-29.0, Support.SOL))
        assertEquals(Support.PLAFOND, c(32.0, Support.PLAFOND)); assertEquals(Support.MUR, c(29.0, Support.PLAFOND))
        assertEquals(Support.PLAFOND, c(60.0, Support.SOL)); assertEquals(Support.SOL, c(-60.0, Support.PLAFOND))
    }

    @Test fun noFlickerAroundThreshold() {
        var s: Support? = null
        val seq = listOf(-33.0, -36.0, -41.0, -34.0, -31.0, -29.0, -33.0, -38.0, -41.0)
        val out = seq.map { e -> SupportFromTilt.classify(e, s).also { s = it } }
        assertEquals(listOf(Support.MUR, Support.MUR, Support.SOL, Support.SOL, Support.SOL, Support.MUR, Support.MUR, Support.MUR, Support.SOL), out)
        assertEquals("Visée : mur (auto)", SupportFromTilt.autoLine(Support.MUR))
    }
}
