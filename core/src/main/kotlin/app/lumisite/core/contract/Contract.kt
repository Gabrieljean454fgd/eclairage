package app.lumisite.core.contract

import app.lumisite.core.compute.SurveyPositions
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.Vec2
import app.lumisite.core.model.SurveyEvent

/**
 * Data contract (docs/CONTRAT_DONNEES.md). Units: metres, radians, nanoseconds (monotonic clock, t_mono_ns).
 * Two frames never mixed silently:
 * - site frame: E (east) / N (north) in metres from the site origin, height above a reference floor;
 * - AR frame: x/y/z metres of one ARCore session (y up), valid within one time base and one epoch (AR frame
 *   opened at session start or at each resume, see [TrajectoryCorrector.Segment]).
 * The only conversion is [SiteLink.toSite], explicit and checked against the frame of the point.
 */

/** One AR frame: time base (one per process start) + epoch (number of resumes before it). */
data class ArFrameRef(val timeBaseId: String?, val epoch: Int)

/** A point in metres in one AR frame (ARCore world: y up, -z forward). */
data class ArPoint(val xM: Double, val yM: Double, val zM: Double, val frame: ArFrameRef)

/** Height above the reference floor: in metres only when a floor is known, otherwise "hauteur non qualifiée". */
sealed interface SiteHeight {
    data class AboveFloor(val metres: Double, val floorRef: String) : SiteHeight
    data object Unqualified : SiteHeight

    companion object {
        const val UNQUALIFIED_LABEL = "hauteur non qualifiée"
    }
}

fun SiteHeight.label(): String = when (this) {
    is SiteHeight.AboveFloor -> "%.2f m".format(java.util.Locale.FRANCE, metres)
    SiteHeight.Unqualified -> SiteHeight.UNQUALIFIED_LABEL
}

/** A point of the site frame: E/N metres and a qualified (or not) height. */
data class SitePoint(val eM: Double, val nM: Double, val height: SiteHeight = SiteHeight.Unqualified) {
    /** For the existing ENU types: u is kept only when the height is qualified (0 otherwise, never an AR y). */
    fun toEnu() = Enu(eM, nM, (height as? SiteHeight.AboveFloor)?.metres ?: 0.0)
}

enum class PositionMethod { AR_PLANE, AR_LAST_PLANE, AR_FLOOR_ESTIMATE, AR_CAMERA, ESTIMATED, MAP_TAP, UNKNOWN;
    companion object {
        /** Mapping of the journal's positionSource strings (unchanged, append-only). */
        fun of(positionSource: String): PositionMethod = when (positionSource) {
            "AR_PLANE" -> AR_PLANE; "AR_LAST_PLANE" -> AR_LAST_PLANE; "AR_FLOOR_ESTIMATE" -> AR_FLOOR_ESTIMATE
            "AR_POSE" -> AR_CAMERA; "ESTIMATED" -> ESTIMATED
            else -> if (positionSource.startsWith("MANUAL")) MAP_TAP else UNKNOWN
        }
    }
}

enum class PositionQuality(val label: String) { GOOD("bonne"), MEDIUM("moyenne"), LOW("faible"), MANUAL("manuelle"), NONE("aucune") }

/** Provenance of every position: method, time (monotonic ns + time base, UTC ms for display), frame, quality. */
data class PositionProvenance(
    val method: PositionMethod,
    val tMonoNs: Long,
    val timeBaseId: String?,
    val utcEpochMs: Long,
    /** "site:map" for a map tap, "site:<transform event id>" for an AR position projected by its segment, "none". */
    val frame: String,
    val quality: PositionQuality,
)

object Provenance {
    fun of(r: SurveyPositions.Resolved): PositionProvenance {
        val s: SurveyEvent = r.event
        val method = PositionMethod.of(s.positionSource)
        val manual = SurveyPositions.isManual(s)
        val frame = when { manual -> "site:map"; r.transformEventId != null -> "site:${r.transformEventId}"; else -> "none" }
        val quality = when {
            r.site == null -> PositionQuality.NONE
            manual -> PositionQuality.MANUAL
            method == PositionMethod.AR_PLANE -> PositionQuality.GOOD
            method == PositionMethod.AR_LAST_PLANE || method == PositionMethod.AR_FLOOR_ESTIMATE -> PositionQuality.MEDIUM
            else -> PositionQuality.LOW
        }
        return PositionProvenance(method, s.t.monotonicNs, s.t.timeBaseId, s.t.utcEpochMs, frame, quality)
    }
}

/** Explicit link between one AR frame and the site frame (the transform of one segment). */
class SiteLink(val segment: TrajectoryCorrector.Segment) {
    val frame = ArFrameRef(segment.timeBaseId, segment.epoch)

    /** Refuses a point of another AR frame: an AR coordinate is meaningless outside its own session/epoch. */
    fun toSite(p: ArPoint, height: SiteHeight = SiteHeight.Unqualified): SitePoint {
        require(p.frame.epoch == frame.epoch && (frame.timeBaseId == null || p.frame.timeBaseId == frame.timeBaseId)) {
            "Point du repère AR ${p.frame} converti avec le lien du repère $frame"
        }
        val v = segment.transform.apply(Vec2(p.xM, -p.zM))
        return SitePoint(v.x, v.y, height)
    }
}
