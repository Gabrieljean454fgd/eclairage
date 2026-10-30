package app.lumisite.core.compute

import app.lumisite.core.geo.Enu
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SurveyEvent

/**
 * Single position function for survey records, shared by the live screen, the site page and the export
 * (derived/survey_points.ndjson). A relocalization only applies to records made after it.
 */
object SurveyPositions {
    data class Resolved(val event: SurveyEvent, val site: Enu?, val transformEventId: String?)

    fun isManual(s: SurveyEvent) = s.manualSite != null && s.positionSource.startsWith("MANUAL")

    fun resolve(s: SurveyEvent, timeline: List<TrajectoryCorrector.Segment>): Resolved {
        if (isManual(s)) return Resolved(s, s.manualSite, null)
        val seg = TrajectoryCorrector.activeAt(timeline, (s.poseT ?: s.t).monotonicNs)
        if (s.pose != null && seg != null) {
            val v = seg.transform.apply(PoseGeometry.plane(s.pose))
            return Resolved(s, Enu(v.x, v.y, PoseGeometry.height(s.pose)), seg.eventId)
        }
        // v0.1 records without a pose: keep the historical value written at record time.
        return Resolved(s, s.estimatedSite, null)
    }

    /**
     * Current state of the records after edits: an edit is a new survey event with `supersedes` = id of the record it
     * replaces (the journal is append-only); `deleted = true` removes it. Order of first creation is kept.
     */
    fun current(events: List<SessionEvent>): List<SurveyEvent> {
        val surveys = events.filterIsInstance<SurveyEvent>()
        val replaced = surveys.mapNotNull { it.supersedes }.toSet()
        return surveys.filter { it.id !in replaced && it.deleted != true }
    }

    fun resolveAll(events: List<SessionEvent>): List<Resolved> {
        val tl = TrajectoryCorrector.timeline(events)
        return current(events).map { resolve(it, tl) }
    }

    /** Id of the first record of an edit chain (stable identity of an object across edits). */
    fun rootId(events: List<SessionEvent>, id: String): String {
        val byId = events.filterIsInstance<SurveyEvent>().associateBy { it.id }
        var cur = byId[id] ?: return id
        while (true) cur = byId[cur.supersedes ?: return cur.id] ?: return cur.id
    }
}
