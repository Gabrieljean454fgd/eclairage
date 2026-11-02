package app.lumisite.core

import app.lumisite.core.compute.Calibrator
import app.lumisite.core.compute.SurveyPositions
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TimeBaseAdded
import app.lumisite.core.model.TrackingState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D13: closing then resuming a session (new AR frame) keeps the plan coordinates of everything recorded before. */
class ResumeSessionTest {
    private val s = 1_000_000_000L

    @Test fun calibrationSurveysAndTraceKeepTheirPlanCoordinatesAfterResume() {
        val dir = Files.createTempDirectory("lumi").toFile()
        val ref = ReferencePoint("A", Enu(10.0, 20.0), source = "MAP_TAP")
        SessionJournal(dir, Fixtures.header).use { j ->
            j.event(SessionStarted("e0", Fixtures.stamp(5 * s)))
            val poses = (0..20).map { Fixtures.pose(5 * s + it * 100_000_000L, it * 0.2, it * 0.5) }
            poses.forEach(j::pose)
            j.event(ControlPointEvent("cp1", poses[0].t, "CALIBRATION", LocalObservation(poses[0], ref), 30.0, "MANUAL"))
            j.event(Calibrator.onePointHeading("cal", poses[0].t, LocalObservation(poses[0], ref), 30.0, "MANUAL", 5.0))
            listOf(5, 10, 15).forEachIndexed { i, k ->
                j.event(SurveyEvent("s$i", poses[k].t, "LUMINAIRE", null, poses[k], TrackingState.TRACKING, null, "AR_POSE"))
            }
        }
        val before = SessionReader.read(dir, includeImu = false)
        val surveysBefore = SurveyPositions.resolveAll(before.events).associate { it.event.id to it.site }
        val traceBefore = TrajectoryCorrector.correct(before.poses, before.events, null)
        assertEquals(3, surveysBefore.values.count { it != null })
        assertEquals(21, traceBefore.size)

        // Resume: the journal is reopened and a new AR frame starts (same boot, later monotonic time).
        SessionJournal(dir).use { j ->
            j.event(TimeBaseAdded("tb-r", Fixtures.stamp(60 * s), Fixtures.timeBase, "session resumed"))
            (0..5).forEach { j.pose(Fixtures.pose(61 * s + it * 100_000_000L, -3.0 + it, 7.0)) }
        }
        val after = SessionReader.read(dir, includeImu = false)
        assertTrue(after.events.any { it is ControlPointEvent && it.id == "cp1" }, "the calibration point is read back")
        val surveysAfter = SurveyPositions.resolveAll(after.events).associate { it.event.id to it.site }
        for ((id, p) in surveysBefore) {
            val q = assertNotNull(surveysAfter[id], "survey $id still has a plan position after resume")
            assertEquals(p!!.e, q.e, 1e-9); assertEquals(p.n, q.n, 1e-9); assertEquals(p.u, q.u, 1e-9)
        }
        val traceAfter = TrajectoryCorrector.correct(after.poses, after.events, null)
        assertEquals(traceBefore.map { it.site }, traceAfter.filter { it.monotonicNs < 60 * s }.map { it.site })
        assertTrue(traceAfter.none { it.monotonicNs > 60 * s }, "poses of the new AR frame are not projected with the old transform")
        assertNull(TrajectoryCorrector.current(after.events), "the new AR frame is not tied to the site yet")

        // Repositioning in the new frame: new transform for new poses, old objects unchanged.
        val q0 = Fixtures.pose(61 * s, -3.0, 7.0)
        val cal2 = Calibrator.onePointHeading("cal2", q0.t, LocalObservation(q0, ReferencePoint("B", Enu(0.0, 0.0), source = "MAP_TAP")), 0.0, "MANUAL", 5.0)
        val ev2 = after.events + cal2
        assertEquals("cal2", TrajectoryCorrector.current(ev2)!!.eventId)
        val surveys2 = SurveyPositions.resolveAll(ev2).associate { it.event.id to it.site }
        for ((id, p) in surveysBefore) { assertEquals(p!!.e, surveys2[id]!!.e, 1e-9); assertEquals(p.n, surveys2[id]!!.n, 1e-9) }
        val newPts = TrajectoryCorrector.correct(after.poses, ev2, null).filter { it.monotonicNs > 60 * s }
        assertEquals(6, newPts.size)
        assertEquals(0.0, newPts.first().site.e, 1e-6); assertEquals(0.0, newPts.first().site.n, 1e-6)
    }
}
