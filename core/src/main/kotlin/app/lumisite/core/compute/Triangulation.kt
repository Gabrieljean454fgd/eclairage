package app.lumisite.core.compute

import kotlin.math.abs
import kotlin.math.acos

/** Intersection approchée de deux visées du même point, exprimées dans le même repère AR. */
object Triangulation {
    const val SOURCE = "AR_TRIANGULATION"
    private const val MIN_BASELINE_M = 0.35
    private const val MIN_ANGLE_DEG = 12.0
    private const val MAX_RAY_GAP_M = 0.15

    sealed interface Result {
        data class Point(val position: V3, val angleDeg: Double, val rayGapM: Double) : Result
        enum class Refused : Result { BASELINE, ANGLE, OUT_OF_RANGE, RAY_GAP }
    }

    fun intersect(a: V3, dirA: V3, b: V3, dirB: V3): Result {
        val baseline = b - a
        if (baseline.norm() < MIN_BASELINE_M) return Result.Refused.BASELINE
        val u = dirA.unit()
        val v = dirB.unit()
        if (u.norm() < 0.5 || v.norm() < 0.5) return Result.Refused.ANGLE
        val dot = (u dot v).coerceIn(-1.0, 1.0)
        val angle = Math.toDegrees(acos(abs(dot)))
        if (angle < MIN_ANGLE_DEG) return Result.Refused.ANGLE

        val uOffset = baseline dot u
        val vOffset = baseline dot v
        val denom = 1 - dot * dot
        val t = (uOffset - dot * vOffset) / denom
        val s = (dot * uOffset - vOffset) / denom
        if (t !in 0.05..Aim.MAX_RANGE_M || s !in 0.05..Aim.MAX_RANGE_M) return Result.Refused.OUT_OF_RANGE
        val pa = a + u * t
        val pb = b + v * s
        val gap = pa.distanceTo(pb)
        if (gap > MAX_RAY_GAP_M) return Result.Refused.RAY_GAP
        return Result.Point((pa + pb) * 0.5, angle, gap)
    }
}
