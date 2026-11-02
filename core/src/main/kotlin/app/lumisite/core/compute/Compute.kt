package app.lumisite.core.compute

import app.lumisite.core.geo.Angles
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.model.CalibrationEvent
import app.lumisite.core.model.CalibrationMethod
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.RelocalizationEvent
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.TimeBaseAdded
import app.lumisite.core.model.TrackingState
import app.lumisite.core.time.Stamp
import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.hypot

/** Geometry of an ARCore pose (session frame: y up, camera looks along its local -Z). */
object PoseGeometry {
    /** Horizontal plane coordinates: x stays x, plane y = -z (so "forward" at start is +y). */
    fun plane(p: PoseSample): Vec2 = Vec2(p.p[0], -p.p[2])
    fun height(p: PoseSample): Double = p.p[1]

    /** Camera forward (-Z) rotated by quaternion, projected on the horizontal plane, as an angle. */
    fun forwardPlaneAngle(p: PoseSample): Double {
        val (x, y, z, w) = p.q
        // rotate v = (0, 0, -1)
        val fx = -(2 * (x * z + w * y))
        val fz = -(1 - 2 * (x * x + y * y))
        return atan2(-fz, fx)
    }
}

object Calibrator {
    /**
     * One known point + heading of the camera viewing direction (degrees from geographic north).
     * Scale is fixed to 1 (ARCore poses are metric).
     */
    fun onePointHeading(
        id: String, t: Stamp, obs: LocalObservation, cameraHeadingDeg: Double,
        headingSource: String, headingUncertaintyDeg: Double?,
    ): CalibrationEvent {
        val pose = requireNotNull(obs.pose) { "A tracked pose is required" }
        val rot = Angles.wrapPi(Angles.headingDegToEnuRad(cameraHeadingDeg) - PoseGeometry.forwardPlaneAngle(pose))
        val tr = Similarity2D.fromPointAndRotation(PoseGeometry.plane(pose), Vec2(obs.reference.site.e, obs.reference.site.n), rot)
        return CalibrationEvent(
            id = id, t = t, method = CalibrationMethod.ONE_POINT_HEADING, observations = listOf(obs),
            headingDeg = cameraHeadingDeg, headingSource = headingSource, headingUncertaintyDeg = headingUncertaintyDeg,
            transform = tr, scaleFixed = true, residualM = 0.0,
        )
    }

    /**
     * Two known points A then B visited with the device. Rotation from the A->B direction.
     * If [fixScaleToOne] the AR metric scale is kept and the map/AR distance ratio is only reported.
     * If [knownDistanceM] (tape-measured A-B) is given, it is recorded and the residual is reported against it.
     */
    fun twoPoints(
        id: String, t: Stamp, a: LocalObservation, b: LocalObservation,
        fixScaleToOne: Boolean = true, knownDistanceM: Double? = null,
    ): CalibrationEvent {
        val pa = PoseGeometry.plane(requireNotNull(a.pose)); val pb = PoseGeometry.plane(requireNotNull(b.pose))
        val ta = Vec2(a.reference.site.e, a.reference.site.n); val tb = Vec2(b.reference.site.e, b.reference.site.n)
        val arDist = (pb - pa).norm()
        require(arDist > 0.5) { "Points A and B must be at least 0.5 m apart in AR tracking (got %.2f m)".format(arDist) }
        val measuredScale = (tb - ta).norm() / arDist
        val tr = Similarity2D.fromTwoPoints(pa, pb, ta, tb, fixedScale = if (fixScaleToOne) 1.0 else null)
        val residual = if (knownDistanceM != null) arDist - knownDistanceM else (tr.apply(pb) - tb).norm()
        return CalibrationEvent(
            id = id, t = t,
            method = if (knownDistanceM != null) CalibrationMethod.TWO_POINTS_KNOWN_DISTANCE else CalibrationMethod.TWO_POINTS,
            observations = listOf(a, b), knownDistanceM = knownDistanceM, transform = tr,
            measuredScale = measuredScale, scaleFixed = fixScaleToOne, residualM = residual,
            notes = if (knownDistanceM != null) "residualM = AR distance - known distance" else "residualM = |T(B) - B_ref|",
        )
    }
}

object Relocalizer {
    /**
     * The user states that the device is now at [ref]. Builds a new transform such that the current raw pose
     * maps exactly onto [ref]; optionally applies an extra heading correction (rotation about [ref]).
     * Previous transforms/poses are untouched: this only produces an event.
     */
    fun relocalize(
        id: String, t: Stamp, pose: PoseSample, ref: ReferencePoint, current: Similarity2D,
        headingCorrectionDeg: Double? = null, notes: String? = null,
    ): RelocalizationEvent {
        val local = PoseGeometry.plane(pose)
        val before = current.apply(local)
        val target = Vec2(ref.site.e, ref.site.n)
        val rot = current.rotationRad - Math.toRadians(headingCorrectionDeg ?: 0.0)
        val after = Similarity2D.fromPointAndRotation(local, target, rot, current.scale)
        return RelocalizationEvent(
            id = id, t = t, observation = LocalObservation(pose, ref),
            estimatedBefore = Enu(before.x, before.y), correctedAfter = Enu(target.x, target.y),
            deltaM = (target - before).norm(), transformBefore = current, transformAfter = after,
            correctionModel = if (headingCorrectionDeg == null) "TRANSLATION" else "TRANSLATION_AND_HEADING",
            headingCorrectionDeg = headingCorrectionDeg, notes = notes,
        )
    }
}

/** A derived (computed) trajectory point. */
@Serializable
data class CorrectedPoint(
    val monotonicNs: Long,
    val utcEpochMs: Long,
    val timeBaseId: String,
    val site: Enu,
    val latLon: LatLon?,
    val tracking: TrackingState,
    /** Id of the calibration/relocalization event whose transform was applied. */
    val transformEventId: String,
)

/**
 * Piecewise application of transforms: each pose uses the latest calibration/relocalization that happened
 * at or before it (monotonic clock of the same time base). Poses before the first calibration have no site
 * position and are omitted from the derived trajectory (they remain in the raw stream). Only TRACKING poses
 * are used: ARCore does not provide a valid pose while tracking is paused.
 */
object TrajectoryCorrector {
    data class Segment(val fromNs: Long, val transform: Similarity2D, val eventId: String, val timeBaseId: String? = null)

    /**
     * Stage 1.7: order of the time bases of a session = order of their first appearance in the journal (append order).
     * Monotonic clocks restart at each phone boot, so stamps are ordered by (time base rank, monotonicNs), never by
     * monotonicNs alone.
     */
    fun baseRanks(events: List<SessionEvent>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (e in events) {
            out.getOrPut(e.t.timeBaseId) { out.size }
            if (e is TimeBaseAdded) out.getOrPut(e.timeBase.id) { out.size }
        }
        return out
    }

    fun order(ranks: Map<String, Int>): Comparator<Stamp> =
        compareBy<Stamp>({ ranks[it.timeBaseId] ?: Int.MAX_VALUE }, { it.monotonicNs })

    /**
     * Transform timeline. A [TimeBaseAdded] (session resumed: new AR frame) ends the validity of earlier
     * transforms until a new calibration is made.
     */
    fun timeline(events: List<SessionEvent>): List<Segment> {
        val cmp = order(baseRanks(events))
        val sorted = events.sortedWith { a, b -> cmp.compare(a.t, b.t) }
        val out = ArrayList<Segment>()
        for (e in sorted) when (e) {
            is CalibrationEvent -> out += Segment(e.t.monotonicNs, e.transform, e.id, e.t.timeBaseId)
            is RelocalizationEvent -> if (out.isNotEmpty()) out += Segment(e.t.monotonicNs, e.transformAfter, e.id, e.t.timeBaseId)
            is TimeBaseAdded -> out.clear()
            else -> {}
        }
        return out
    }

    /** Latest segment at or before [monotonicNs] (same time base as the timeline, i.e. the current one). */
    fun activeAt(timeline: List<Segment>, monotonicNs: Long): Segment? = timeline.lastOrNull { it.fromNs <= monotonicNs }

    /** Latest segment of the same time base at or before [t]; a stamp of another base (other boot) gets none. */
    fun activeAt(timeline: List<Segment>, t: Stamp): Segment? =
        timeline.lastOrNull { (it.timeBaseId == null || it.timeBaseId == t.timeBaseId) && it.fromNs <= t.monotonicNs }

    fun correct(poses: List<PoseSample>, events: List<SessionEvent>, ltp: LocalTangentPlane?): List<CorrectedPoint> {
        val out = ArrayList<CorrectedPoint>()
        correctEach(poses.asSequence(), events, ltp) { out += it }
        val cmp = order(baseRanks(events))
        return out.sortedWith { a, b -> cmp.compare(Stamp(a.utcEpochMs, a.monotonicNs, a.timeBaseId), Stamp(b.utcEpochMs, b.monotonicNs, b.timeBaseId)) }
    }

    /** Streaming form (export): each TRACKING pose with an active transform of its own time base gives one point. */
    fun correctEach(poses: Sequence<PoseSample>, events: List<SessionEvent>, ltp: LocalTangentPlane?, emit: (CorrectedPoint) -> Unit) {
        val tl = timeline(events)
        if (tl.isEmpty()) return
        for (p in poses) {
            if (p.tracking != TrackingState.TRACKING) continue
            val seg = activeAt(tl, p.t) ?: continue
            val v = seg.transform.apply(PoseGeometry.plane(p))
            val enu = Enu(v.x, v.y, PoseGeometry.height(p))
            emit(CorrectedPoint(p.t.monotonicNs, p.t.utcEpochMs, p.t.timeBaseId, enu, ltp?.toLatLon(enu.copy(u = 0.0)), p.tracking, seg.eventId))
        }
    }

    fun pathLengthM(points: List<CorrectedPoint>): Double =
        points.zipWithNext { a, b -> hypot(b.site.e - a.site.e, b.site.n - a.site.n) }.sum()
}
