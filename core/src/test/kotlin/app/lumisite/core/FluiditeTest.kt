package app.lumisite.core

import app.lumisite.core.compute.CorrectedPoint
import app.lumisite.core.geo.Enu
import app.lumisite.core.journal.MirrorLedger
import app.lumisite.core.map.GrowingSegmentSimplifier
import app.lumisite.core.map.Simplify
import app.lumisite.core.model.TrackingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v176b: display-only simplification of long trajectories and the « hash once per closed segment » ledger. */
class FluiditeTest {
    private fun pt(i: Int, e: Double, n: Double) = CorrectedPoint(i * 100_000_000L, 0, "tb", Enu(e, n, 0.0), null, TrackingState.TRACKING, "cal")

    @Test fun straightLineKeepsOnlyItsEnds() {
        val line = (0..1000).map { pt(it, it * 0.06, 0.0) }
        assertEquals(listOf(line.first(), line.last()), Simplify.douglasPeucker(line, 0.05))
    }

    @Test fun cornerIsKeptAndNoPointIsFurtherThanTolerance() {
        val l = (0..100).map { pt(it, it * 0.1, 0.0) } + (1..100).map { pt(100 + it, 10.0, it * 0.1) }
        val s = Simplify.douglasPeucker(l, 0.05)
        assertEquals(listOf(l.first(), l[100], l.last()), s)
    }

    @Test fun growingSegmentIsSimplifiedOncePerBlockAndStaysContinuous() {
        val walk = (0 until 2600).map { i -> pt(i, 6 * kotlin.math.cos(i * 0.01), 6 * kotlin.math.sin(i * 0.01)) }
        val simp = GrowingSegmentSimplifier(chunk = 500, tolM = 0.05)
        var out = emptyList<CorrectedPoint>()
        for (k in 1..walk.size) out = simp.render(walk.subList(0, k))
        assertTrue(out.size < walk.size / 2, "rendered ${out.size} of ${walk.size}")
        assertEquals(walk.first(), out.first()); assertEquals(walk.last(), out.last())
        // Monotonic in time: no duplicated or reordered point at the block boundaries.
        assertTrue(out.zipWithNext().all { (a, b) -> a.monotonicNs < b.monotonicNs })
        // The raw tail since the last block boundary is drawn as is.
        assertEquals(walk.subList(2501, 2600), out.takeLast(99))
        // A new segment (other first point) resets the cache.
        val other = (0 until 10).map { pt(10_000 + it, 0.0, it.toDouble()) }
        assertEquals(other, simp.render(other))
    }

    @Test fun closedSegmentIsHashedOnceThenSkippedUntilItChanges() {
        val l = MirrorLedger()
        assertFalse(l.unchanged("seg/poses.0001.ndjson", 1200, 1000))
        l.record("seg/poses.0001.ndjson", "abc", 1200, 1000)
        assertTrue(l.unchanged("seg/poses.0001.ndjson", 1200, 1000))
        assertFalse(l.unchanged("seg/poses.0001.ndjson", 1300, 1000))
        assertFalse(l.unchanged("seg/poses.0001.ndjson", 1200, 2000))
        // Reloaded from mirror_state.json + mirror_stamps.json after a restart: still skipped.
        val again = MirrorLedger(l.doneSnapshot(), l.stampsSnapshot())
        assertTrue(again.unchanged("seg/poses.0001.ndjson", 1200, 1000))
        // A file copied by an older version (no stamp) is hashed once more, never trusted blindly.
        assertFalse(MirrorLedger(mapOf("site.json" to "x")).unchanged("site.json", 10, 10))
    }
}
