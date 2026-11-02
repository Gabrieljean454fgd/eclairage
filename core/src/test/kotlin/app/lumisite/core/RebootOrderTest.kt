package app.lumisite.core

import app.lumisite.core.compute.Calibrator
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.TimeBaseAdded
import app.lumisite.core.time.Stamp
import app.lumisite.core.time.TimeBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 1.7: a session that straddles a phone reboot (monotonic clock restarts near 0). */
class RebootOrderTest {
    private val tb1 = Fixtures.timeBase.copy(bootId = "boot-A", bootCount = 41)
    // After reboot the monotonic clock is small again (8 s since boot) although wall time is 1 h later.
    private val tb2 = TimeBase("tb-2", "dev-1", TimeBase.ANDROID_ELAPSED_REALTIME, 1_700_003_600_000, 8_000_000_000, bootId = "boot-B", bootCount = 42)
    private fun s2(mono: Long) = Stamp(tb2.monotonicToUtcMs(mono).toLong(), mono, tb2.id)
    private fun pose2(mono: Long, x: Double, y: Double) = Fixtures.pose(0, x, y).copy(t = s2(mono))

    private fun events(): List<SessionEvent> {
        val p0 = Fixtures.pose(5_000_000_000, 0.0, 0.0)
        val cal1 = Calibrator.onePointHeading("cal1", p0.t, LocalObservation(p0, ReferencePoint("A", Enu(10.0, 20.0), source = "MAP_TAP")), 0.0, "MANUAL", 5.0)
        val q0 = pose2(9_000_000_000, 0.0, 0.0)
        val cal2 = Calibrator.onePointHeading("cal2", q0.t, LocalObservation(q0, ReferencePoint("B", Enu(30.0, 40.0), source = "MAP_TAP")), 0.0, "MANUAL", 5.0)
        // Journal (append) order.
        return listOf(cal1, TimeBaseAdded("tb", s2(8_500_000_000), tb2, "PHONE_REBOOT"), cal2)
    }

    @Test fun eventsAreOrderedByTimeBaseThenMonotonicNeverByMonotonicAlone() {
        // Make the pre-reboot calibration have a monotonic value larger than everything after reboot.
        val p0 = Fixtures.pose(50_000_000_000, 0.0, 0.0)
        val cal1 = Calibrator.onePointHeading("cal1", p0.t, LocalObservation(p0, ReferencePoint("A", Enu(10.0, 20.0), source = "MAP_TAP")), 0.0, "MANUAL", 5.0)
        val ev = listOf(cal1) + events().drop(1)
        val tl = TrajectoryCorrector.timeline(ev)
        // D13: cal1 is kept for the stamps of its own boot, but the live AR frame only uses cal2. Sorting on monotonicNs alone
        // would put cal1 last and make it the live transform.
        assertEquals(listOf("cal1", "cal2"), tl.map { it.eventId })
        assertEquals("cal2", TrajectoryCorrector.current(ev)!!.eventId)
        val ranks = TrajectoryCorrector.baseRanks(ev)
        assertEquals(listOf("tb-1", "tb-2"), ranks.keys.toList())
    }

    @Test fun trajectoryAcrossRebootIsCoherentAndNeverBridged() {
        val ev = events()
        val before = (0..5).map { Fixtures.pose(5_000_000_000 + it * 1_000_000_000L, it.toDouble(), 0.0) }
        val after = (0..5).map { pose2(9_000_000_000 + it * 1_000_000_000L, it.toDouble(), 0.0) }
        val traj = TrajectoryCorrector.correct(after + before, ev, null) // input order shuffled on purpose
        // D13: pre-reboot poses keep cal1 (their own segment), post-reboot poses use cal2; never cal2 for a pre-reboot pose.
        assertEquals(12, traj.size)
        assertEquals(List(6) { "tb-1" } + List(6) { "tb-2" }, traj.map { it.timeBaseId })
        assertEquals(10.0, traj.first().site.e, 1e-6)
        assertEquals(30.0, traj[6].site.e, 1e-6)
        assertEquals((0..5).map { 9_000_000_000 + it * 1_000_000_000L }, traj.drop(6).map { it.monotonicNs })
    }

    @Test fun aStampOfAnotherBootNeverUsesThisBootTransform() {
        val tl = TrajectoryCorrector.timeline(events())
        assertEquals("cal1", TrajectoryCorrector.activeAt(tl, Fixtures.stamp(99_000_000_000))!!.eventId)
        assertEquals("cal2", TrajectoryCorrector.activeAt(tl, s2(10_000_000_000))!!.eventId)
    }

    @Test fun bootIdIsSerialized() {
        val json = app.lumisite.core.journal.LumiJson.encodeToString(TimeBase.serializer(), tb2)
        assertTrue(json.contains("\"bootId\":\"boot-B\""), json)
    }
}
