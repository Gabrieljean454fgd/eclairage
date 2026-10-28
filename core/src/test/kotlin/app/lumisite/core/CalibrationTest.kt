package app.lumisite.core

import app.lumisite.core.compute.Calibrator
import app.lumisite.core.compute.PoseGeometry
import app.lumisite.core.compute.Relocalizer
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Vec2
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.TrackingState
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalibrationTest {
    private fun ref(e: Double, n: Double) = ReferencePoint("P", Enu(e, n), source = "MAP_TAP")

    @Test fun forwardAngleOfIdentityPoseIsPlusY() {
        assertEquals(PI / 2, PoseGeometry.forwardPlaneAngle(Fixtures.pose(0, 0.0, 0.0)), 1e-12)
        // yaw +90° about +Y turns the camera from -Z towards -X
        assertEquals(PI, PoseGeometry.forwardPlaneAngle(Fixtures.pose(0, 0.0, 0.0, yaw = PI / 2)), 1e-12)
    }

    @Test fun onePointHeadingMapsPointAndDirection() {
        // Camera looks along session +y; user says it is looking east (90°) and stands at (10, 5)
        val pose = Fixtures.pose(1_000, 2.0, 3.0)
        val cal = Calibrator.onePointHeading("c1", pose.t, LocalObservation(pose, ref(10.0, 5.0)), 90.0, "MANUAL", 10.0)
        val at = cal.transform.apply(Vec2(2.0, 3.0))
        assertEquals(10.0, at.x, 1e-9); assertEquals(5.0, at.y, 1e-9)
        // walking 1 m forward in the session must go 1 m east
        val ahead = cal.transform.apply(Vec2(2.0, 4.0))
        assertEquals(11.0, ahead.x, 1e-9); assertEquals(5.0, ahead.y, 1e-9)
        assertTrue(cal.scaleFixed)
    }

    @Test fun twoPointsWithFixedScaleReportsMeasuredScale() {
        val a = Fixtures.pose(1_000, 0.0, 0.0); val b = Fixtures.pose(2_000, 0.0, 10.0)
        val cal = Calibrator.twoPoints("c", b.t, LocalObservation(a, ref(0.0, 0.0)), LocalObservation(b, ref(10.2, 0.0)))
        assertEquals(1.0, cal.transform.scale); assertEquals(1.02, cal.measuredScale!!, 1e-9)
        assertEquals(0.2, cal.residualM!!, 1e-9)
        assertEquals(-PI / 2, cal.transform.rotationRad, 1e-9)
    }

    @Test fun twoPointsTooCloseIsRejected() {
        val a = Fixtures.pose(1_000, 0.0, 0.0); val b = Fixtures.pose(2_000, 0.1, 0.0)
        assertFailsWith<IllegalArgumentException> {
            Calibrator.twoPoints("c", b.t, LocalObservation(a, ref(0.0, 0.0)), LocalObservation(b, ref(1.0, 0.0)))
        }
    }

    @Test fun relocalizationKeepsHistoryAndReportsDelta() {
        val p0 = Fixtures.pose(1_000, 0.0, 0.0)
        val cal = Calibrator.onePointHeading("cal", p0.t, LocalObservation(p0, ref(0.0, 0.0)), 0.0, "MANUAL", null)
        // drifted: session says (0, 10) but user confirms real position (0.5, 10.3) in site frame
        val p1 = Fixtures.pose(5_000, 0.0, 10.0)
        val rel = Relocalizer.relocalize("rel", p1.t, p1, ref(0.5, 10.3), cal.transform)
        assertEquals(0.0, rel.estimatedBefore.e, 1e-9); assertEquals(10.0, rel.estimatedBefore.n, 1e-9)
        assertEquals(Math.hypot(0.5, 0.3), rel.deltaM, 1e-9)
        assertEquals(cal.transform, rel.transformBefore)

        val poses = listOf(p0, Fixtures.pose(3_000, 0.0, 5.0), p1, Fixtures.pose(6_000, 0.0, 11.0),
            Fixtures.pose(7_000, 0.0, 12.0, state = TrackingState.LOST), Fixtures.pose(8_000, 0.0, 13.0, state = TrackingState.DEGRADED))
        val traj = TrajectoryCorrector.correct(poses, listOf(cal, rel), LocalTangentPlane(Fixtures.origin))
        assertEquals(4, traj.size, "LOST/DEGRADED poses excluded from derived trajectory")
        assertEquals(listOf("cal", "cal", "rel", "rel"), traj.map { it.transformEventId })
        assertEquals(5.0, traj[1].site.n, 1e-9) // before relocalization: unchanged
        assertEquals(11.3, traj[3].site.n, 1e-9); assertEquals(0.5, traj[3].site.e, 1e-9)
    }

    @Test fun headingCorrectionRotatesAboutReference() {
        val p0 = Fixtures.pose(1_000, 0.0, 0.0)
        val cal = Calibrator.onePointHeading("cal", p0.t, LocalObservation(p0, ref(0.0, 0.0)), 0.0, "MANUAL", null)
        val p1 = Fixtures.pose(2_000, 0.0, 10.0)
        val rel = Relocalizer.relocalize("rel", p1.t, p1, ref(0.0, 10.0), cal.transform, headingCorrectionDeg = 90.0)
        val ahead = rel.transformAfter.apply(Vec2(0.0, 11.0))
        assertEquals(1.0, ahead.x, 1e-9); assertEquals(10.0, ahead.y, 1e-9) // forward now points east
    }
}
