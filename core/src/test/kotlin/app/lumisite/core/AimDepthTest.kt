package app.lumisite.core

import app.lumisite.core.compute.Aim
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Build 259 : un repère visé sur une estimation n'est jamais une mesure ; la profondeur et les plans détectés le sont. */
class AimDepthTest {
    @Test fun estimatesAreRecognised() {
        listOf("ESTIMATED", "AR_FLOOR_ESTIMATE", Aim.SRC_CEILING_ESTIMATED, Aim.SRC_WALL_ESTIMATE).forEach { assertTrue(Aim.isEstimate(it), it) }
    }

    @Test fun measurementsAreNotEstimates() {
        listOf("AR_PLANE", "AR_WALL", "AR_CEILING", Aim.SRC_DEPTH, null).forEach { assertFalse(Aim.isEstimate(it), it.toString()) }
        assertEquals("AR_DEPTH", Aim.SRC_DEPTH)
    }
}
