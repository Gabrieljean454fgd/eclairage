package app.lumisite.core

import app.lumisite.core.compute.Aim
import app.lumisite.core.compute.AimResult
import app.lumisite.core.compute.V3
import app.lumisite.core.journal.LumiJson
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SurveyEvent
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AimTest {
    private val cam = V3(0.0, 1.5, 0.0)
    private val floor = Aim.Plane(V3(0.0, 0.0, 0.0), Aim.UP, "AR_PLANE")

    @Test fun analyticIntersectionWithFloor() {
        val d = V3(0.0, -1.0, -1.0) // 45° down, forward
        val r = Aim.resolve(cam, d, listOf(floor), null, tracking = true)
        assertIs<AimResult.Hit>(r)
        assertEquals("AR_PLANE", r.source)
        assertEquals(-1.5, r.point.z, 1e-9); assertEquals(0.0, r.point.y, 1e-9)
    }

    @Test fun polygonMissFallsBackToLastObjectThenFloorEstimate() {
        val outside = floor.copy(contains = { false })
        val d = V3(0.0, -1.0, -2.0)
        val r1 = Aim.resolve(cam, d, listOf(outside), lastObjectY = 0.5, tracking = true) as AimResult.Hit
        assertEquals("AR_LAST_PLANE", r1.source); assertEquals(0.5, r1.point.y, 1e-9)
        val r2 = Aim.resolve(cam, d, emptyList(), null, tracking = true) as AimResult.Hit
        assertEquals("AR_FLOOR_ESTIMATE", r2.source); assertEquals(1.5 - Aim.DEFAULT_CAMERA_HEIGHT_M, r2.point.y, 1e-9)
    }

    @Test fun grazingAimIsRefused() {
        val d = V3(0.0, -0.02, -1.0) // |n·d| ≈ 0.02 < 0.03
        assertEquals(AimResult.Grazing, Aim.resolve(cam, d, listOf(floor), 1.0, tracking = true))
    }

    @Test fun upwardAimGivesEstimatedPointAt50cm() {
        val d = V3(0.0, 1.0, -1.0)
        val r = Aim.resolve(cam, d, listOf(floor), null, tracking = true) as AimResult.Hit
        assertEquals("ESTIMATED", r.source)
        assertEquals(0.5, r.point.distanceTo(cam), 1e-9)
        assertEquals(1.5 + 0.5 / sqrt(2.0), r.point.y, 1e-9)
    }

    @Test fun noTrackingNoPoint() {
        assertEquals(AimResult.NoTarget, Aim.resolve(cam, V3(0.0, -1.0, 0.0), listOf(floor), null, tracking = false))
    }

    @Test fun identityQuaternionLooksAlongMinusZ() {
        assertEquals(V3(0.0, 0.0, -1.0), Aim.forward(listOf(0.0, 0.0, 0.0, 1.0)).let { V3(it.x + 0.0, it.y + 0.0, it.z) })
    }

    @Test fun estimatedSourceIsAFreeStringAndOldFilesStillRead() {
        val old = """{"type":"survey","id":"srv-1","t":{"utcEpochMs":1,"monotonicNs":2,"timeBaseId":"tb"},"kind":"spot","tracking":"TRACKING","positionSource":"AR_POSE"}"""
        val e = runCatching { LumiJson.decodeFromString(SessionEvent.serializer(), old) as SurveyEvent }
        if (e.isSuccess) {
            val est = e.getOrThrow().copy(positionSource = "ESTIMATED")
            assertEquals("ESTIMATED", (LumiJson.decodeFromString(SessionEvent.serializer(), LumiJson.encodeToString(SessionEvent.serializer(), est)) as SurveyEvent).positionSource)
        } else throw AssertionError("0.1 survey not readable: ${e.exceptionOrNull()?.message}")
    }
}
