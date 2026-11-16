package app.lumisite.core.map

import app.lumisite.core.compute.CorrectedPoint

/**
 * v176b: map display of long trajectories. Douglas–Peucker (Douglas & Peucker 1973, « Algorithms for the reduction of the
 * number of points required to represent a digitized line ») on the site plane (E, N), iterative to avoid deep recursion.
 * Display only: the journal and the exports always keep every pose.
 */
object Simplify {
    fun douglasPeucker(pts: List<CorrectedPoint>, tolM: Double): List<CorrectedPoint> {
        if (pts.size <= 2) return pts
        val keep = BooleanArray(pts.size); keep[0] = true; keep[pts.size - 1] = true
        val stack = ArrayDeque<IntArray>(); stack.addLast(intArrayOf(0, pts.size - 1))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast().let { it[0] to it[1] }
            var far = -1; var best = tolM
            for (i in a + 1 until b) { val d = dist(pts[i], pts[a], pts[b]); if (d > best) { best = d; far = i } }
            if (far >= 0) { keep[far] = true; stack.addLast(intArrayOf(a, far)); stack.addLast(intArrayOf(far, b)) }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    private fun dist(p: CorrectedPoint, a: CorrectedPoint, b: CorrectedPoint): Double {
        val dx = b.site.e - a.site.e; val dy = b.site.n - a.site.n
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) 0.0 else (((p.site.e - a.site.e) * dx + (p.site.n - a.site.n) * dy) / l2).coerceIn(0.0, 1.0)
        return kotlin.math.hypot(p.site.e - (a.site.e + t * dx), p.site.n - (a.site.n + t * dy))
    }
}

/**
 * Incremental display of a growing trajectory segment: the first points are simplified once per block of [chunk] points and
 * cached; the recent tail (< [chunk] points) is drawn raw. Each new pose costs O(tail), never a pass over the whole walk.
 */
class GrowingSegmentSimplifier(private val chunk: Int = 500, private val tolM: Double = 0.05) {
    private var firstNs: Long? = null
    private var frozen = 0
    private var simplified: List<CorrectedPoint> = emptyList()

    fun render(seg: List<CorrectedPoint>): List<CorrectedPoint> {
        if (seg.isEmpty()) return seg
        if (seg[0].monotonicNs != firstNs || seg.size < frozen) { firstNs = seg[0].monotonicNs; frozen = 0; simplified = emptyList() }
        val target = (seg.size - 1) / chunk * chunk
        if (target > frozen) {
            // The block boundary point is kept in both parts so that the drawn line stays continuous.
            simplified = simplified.dropLast(if (frozen > 0) 1 else 0) + Simplify.douglasPeucker(seg.subList(frozen, target + 1), tolM)
            frozen = target
        }
        return if (frozen == 0) seg else simplified + seg.subList(frozen + 1, seg.size)
    }
}
