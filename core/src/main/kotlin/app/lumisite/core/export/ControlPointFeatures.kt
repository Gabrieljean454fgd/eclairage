package app.lumisite.core.export

import app.lumisite.core.compute.PoseGeometry
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.Angles
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Vec2
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.ControlPointRemoved
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.TimeBaseAdded
import app.lumisite.core.position.CalibrationQuality
import app.lumisite.core.position.CalibrationSolver
import app.lumisite.core.position.ControlPoint
import app.lumisite.core.position.Solution
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.math.round

/** Rounds to [d] decimals, deterministically (same convention as GeoJsonFeatures). */
internal fun rd(v: Double, d: Int): Double {
    val p = Math.pow(10.0, d.toDouble())
    return round(v * p) / p
}

/**
 * v176f F2: calibration markers (`control_point`) as named Point features of `releves.geojson`, so JOSM and QGIS
 * show them. One Feature per active control point of the session journal, in journal order.
 *
 * Residual and status mirror the table of Session > Calibrer / Recaler: only the current AR epoch (after the last
 * [TimeBaseAdded]) is solved, points of earlier epochs are exported as « session précédente » without residual —
 * same as the app. The independent control (5th calibration point) is excluded from the solve and gets its own
 * residual, like `SessionController.controlResidualM()`.
 *
 * Approximations vs the live app: the solve is replayed once from the journaled aim/phone pose (the live app also
 * re-solves with ARCore anchor poses refined since recording, which are not journaled); `nowNs` is the last
 * control-point timestamp of the epoch; the fallback rotation is the rotation of the last transform of the epoch.
 */
object ControlPointFeatures {
    data class Repere(
        val event: ControlPointEvent,
        /** |T(local) − target| in metres; null when no solve covers the point (previous epoch, pending, no pose). */
        val residualM: Double?,
        /** Same words as the app table: retenu / rejeté / ancre / contrôle / en attente / session précédente. */
        val status: String,
        /** bon / moyen / insuffisant / inconnu, aligned on [residualM] (CalibrationQuality thresholds). */
        val quality: String,
        val previousEpoch: Boolean,
    )

    /** Quality of one repère, aligned on its residual: > 1 m insuffisant, > 0,5 m moyen, else bon (CalibrationQuality). */
    fun qualityOf(residualM: Double?): String = when {
        residualM == null -> "inconnu"
        residualM > CalibrationQuality.IMPRECISE_RMS_M -> "insuffisant"
        residualM > CalibrationQuality.ALERT_M -> "moyen"
        else -> "bon"
    }

    /** ControlPointEvent → solver point, same mapping as SessionController.toControlPoint (journaled aim/phone pose). */
    private fun toControlPoint(e: ControlPointEvent): ControlPoint? {
        val pose = e.observation.pose ?: return null
        val site = e.observation.reference.site
        val local = e.aimSession?.let { Vec2(it[0], -it[2]) } ?: PoseGeometry.plane(pose)
        return ControlPoint(e.id, e.t.monotonicNs, local, Vec2(site.e, site.n), e.kind == "RELOCALIZATION",
            e.headingDeg?.let { Angles.wrapPi(Angles.headingDegToEnuRad(it) - PoseGeometry.forwardPlaneAngle(pose)) },
            forced = e.forcedCause != null)
    }

    /** One Repere per active control point (removed points are not exported, like the app table). */
    fun resolve(events: List<SessionEvent>): List<Repere> {
        val lastTba = events.indexOfLast { it is TimeBaseAdded }
        val out = ArrayList<Repere>()
        // Earlier AR frames: « session précédente », no residual (the app never re-solves them either).
        if (lastTba >= 0) {
            val prev = events.subList(0, lastTba)
            val prevRemoved = prev.filterIsInstance<ControlPointRemoved>().map { it.pointId }.toSet()
            prev.filterIsInstance<ControlPointEvent>().filter { it.id !in prevRemoved }
                .forEach { out += Repere(it, null, "session précédente", qualityOf(null), true) }
        }
        val current = events.subList(lastTba + 1, events.size)
        val removed = current.filterIsInstance<ControlPointRemoved>().map { it.pointId }.toSet()
        val active = current.filterIsInstance<ControlPointEvent>().filter { it.id !in removed }
        if (active.isEmpty()) return out
        // Same solve as SessionController.resolve: non-control points, SolverSettings defaults.
        val pts = active.filter { !it.independentControl }.mapNotNull(::toControlPoint)
        val nowNs = current.filter { it is ControlPointEvent || it is ControlPointRemoved }.maxOfOrNull { it.t.monotonicNs } ?: 0L
        val fallbackRot = TrajectoryCorrector.timeline(events).lastOrNull { it.epoch == TrajectoryCorrector.currentEpoch(events) }
            ?.transform?.rotationRad
        val sol: Solution? = if (pts.isEmpty()) null else CalibrationSolver.solve(pts, nowNs, fallbackRot)
        for (e in active) {
            val cp = toControlPoint(e)
            val res = if (e.independentControl) CalibrationQuality.controlResidual(sol, cp) else sol?.residualsM?.get(e.id)
            val status = (if (e.independentControl) "contrôle"
                else sol?.let { s -> if (e.id in s.rejected) "rejeté" else if (e.id == s.anchorId) "ancre" else "retenu" }
                    ?: "en attente") + if (e.forcedCause != null) " · forcé" else ""
            out += Repere(e, res, status, qualityOf(res), false)
        }
        return out
    }

    /** One Point Feature per repère: WGS84 geometry, flat name + lumisite:* properties for JOSM/QGIS. */
    fun features(reperes: List<Repere>, ltp: LocalTangentPlane, deviceId: String, sessionId: String): List<JsonObject> =
        reperes.map { r ->
            val e = r.event
            val site = e.observation.reference.site
            val ll = ltp.toLatLon(site.copy(u = 0.0))
            buildJsonObject {
                put("type", "Feature")
                put("id", "cp-" + e.id)
                put("geometry", buildJsonObject {
                    put("type", "Point")
                    put("coordinates", buildJsonArray { add(JsonPrimitive(rd(ll.lon, 8))); add(JsonPrimitive(rd(ll.lat, 8))) })
                })
                put("properties", buildJsonObject {
                    put("name", e.observation.reference.label.ifBlank { "Repère de calibrage" })
                    put("lumisite:kind", "control_point")
                    put("lumisite:role", e.kind)
                    put("lumisite:statut", r.status)
                    put("lumisite:qualite", r.quality)
                    put("lumisite:methode", e.observation.reference.source)
                    r.event.aimSource?.let { put("lumisite:visée", it) }
                    e.forcedCause?.let { put("lumisite:forcé", it) }
                    r.residualM?.let { put("lumisite:residu_m", rd(it, 3)) }
                    put("local", buildJsonObject { put("frame_id", "site"); put("x", rd(site.e, 3)); put("y", rd(site.n, 3)); put("z", rd(site.u, 3)) })
                    // sigma_xy_m = résidu du repère (écart transformé ↔ point de la carte), jamais une incertitude capteur.
                    put("accuracy", buildJsonObject {
                        put("sigma_xy_m", r.residualM?.let { JsonPrimitive(rd(it, 3)) } ?: JsonNull)
                        put("sigma_z_m", JsonNull); put("method", "control_point")
                    })
                    put("time", buildJsonObject {
                        put("captured_utc", Instant.ofEpochMilli(e.t.utcEpochMs).toString())
                        put("device_monotonic_ns", e.t.monotonicNs)
                    })
                    put("device_id", deviceId); put("session_id", sessionId); put("event_id", e.id)
                    // JOSM simplestyle: orange flag = repère de calibrage (E4).
                    put("marker-symbol", "flag"); put("marker-color", "#FF8C00")
                })
            }
        }
}
