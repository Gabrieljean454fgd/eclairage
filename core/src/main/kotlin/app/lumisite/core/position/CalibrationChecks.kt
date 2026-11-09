package app.lumisite.core.position

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Paquet v6 (anomalies du 08/11) : correspondances téléphone ↔ carte refusées avant d'entrer dans le calcul.
 * The transform has a fixed scale (1), so the AR distance between two points must match their map distance.
 */
object ControlPointCheck {
    /** Two calibration map points closer than this are the same point (scene 1: 4 AR points on one map point). */
    const val MIN_MAP_SEPARATION_M = 1.0
    /** A pair is contradictory when |map distance − phone distance| > max(this, [RELATIVE_TOLERANCE] × distance, drift). */
    /** 3 m: a finger on the map at building zoom (~234 m on a Pixel 7) is about ±2 m; scene 2 (5 cm vs 9,77 m) stays far above. */
    const val MIN_CONTRADICTION_M = 3.0
    const val RELATIVE_TOLERANCE = 0.3
    /** AR drift allowed between two points taken |Δt| apart (relocalizations exist to absorb it): 5 cm/s. */
    const val DRIFT_M_PER_S = 0.05

    fun contradiction(a: ControlPoint, b: ControlPoint): Double? {
        val dAr = (a.local - b.local).norm(); val dMap = (a.target - b.target).norm()
        val gap = abs(dMap - dAr)
        val drift = MIN_CONTRADICTION_M + DRIFT_M_PER_S * abs(a.tNs - b.tNs) / 1e9
        return gap.takeIf { it > maxOf(MIN_CONTRADICTION_M, RELATIVE_TOLERANCE * max(dAr, dMap), drift) }
    }

    /**
     * Why [candidate] is refused. [overridable]: a contradiction may be a real ARCore jump (tracking lost, e.g.
     * INSUFFICIENT_FEATURES), so the user can force it (« Recaler quand même », journaled with its cause); a reused map
     * point (scene 1) can never be forced.
     */
    data class Refusal(val message: String, val overridable: Boolean, val conflictId: String? = null)

    /** Refusal of [candidate] against the active points, or null if it can be added. */
    fun refusal(existing: List<ControlPoint>, candidate: ControlPoint, labelOf: (ControlPoint) -> String = { it.id }): Refusal? {
        sameMapPoint(existing, candidate, labelOf)?.let { return it }
        existing.firstOrNull { contradiction(it, candidate) != null }?.let {
            return Refusal(("Correspondance contradictoire avec le point %s : téléphone déplacé de %.1f m, points carte séparés de %.1f m. " +
                "Vérifiez le point touché sur la carte.").format(java.util.Locale.FRANCE, labelOf(it), (it.local - candidate.local).norm(), (it.target - candidate.target).norm()),
                overridable = true, conflictId = it.id)
        }
        return null
    }

    /** Scene 1: a calibration map point closer than [MIN_MAP_SEPARATION_M] to an active one (never overridable). */
    fun sameMapPoint(existing: List<ControlPoint>, candidate: ControlPoint, labelOf: (ControlPoint) -> String = { it.id }): Refusal? {
        if (candidate.relocalization) return null
        val it = existing.filter { !it.relocalization }.firstOrNull { (it.target - candidate.target).norm() < MIN_MAP_SEPARATION_M } ?: return null
        return Refusal("Point carte déjà utilisé (à %.1f m du point %s). Choisissez un autre point de la carte, à plus de %.0f m."
            .format(java.util.Locale.FRANCE, (it.target - candidate.target).norm(), labelOf(it), MIN_MAP_SEPARATION_M), overridable = false, conflictId = it.id)
    }

    /**
     * Points of an already recorded table that contradict the others (journals written before the check): the point with
     * the most contradictions is removed first, the most recent one on a tie, until the rest is consistent. Never below
     * 2 points: between two contradictory points nothing says which one is wrong (the entry check refuses the 2nd).
     * A contradiction with a forced point (« Recaler quand même ») is the user's decision: neither point is removed.
     */
    fun inconsistent(points: List<ControlPoint>): Set<String> {
        val out = LinkedHashSet<String>()
        var kept = points
        while (kept.size >= 3) {
            val bad = kept.associateWith { p -> if (p.forced) 0 else kept.count { q -> q !== p && !q.forced && contradiction(p, q) != null } }
            val worst = bad.entries.filter { it.value > 0 }.maxWithOrNull(compareBy<Map.Entry<ControlPoint, Int>>({ it.value }, { it.key.tNs })) ?: break
            out += worst.key.id; kept = kept - worst.key
        }
        return out
    }
}

/** Paquet v6 : qualité affichée en Session > Calibrer. Au-delà de 1 m de résidu : « imprécis », jamais « précis ». */
object CalibrationQuality {
    enum class Level(val label: String) { BON("bon"), MOYEN("moyen"), INSUFFISANT("insuffisant") }
    data class Grade(val level: Level, val reasons: List<String>)

    const val GOOD_RMS_M = 0.5
    const val IMPRECISE_RMS_M = 1.0
    /** Ratio of the small to the large spread of the map points: below = (quasi) aligned. */
    const val ALIGNED_RATIO = 0.05
    const val WEAK_RATIO = 0.15

    /** sqrt(λmin/λmax) of the covariance of the map points (0 = aligned, 1 = evenly spread). */
    fun spreadRatio(pts: List<ControlPoint>): Double {
        if (pts.size < 3) return 0.0
        val mx = pts.sumOf { it.target.x } / pts.size; val my = pts.sumOf { it.target.y } / pts.size
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (p in pts) { val dx = p.target.x - mx; val dy = p.target.y - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy }
        val tr = sxx + syy; val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc; val l2 = tr / 2 - disc
        return if (l1 <= 1e-9) 0.0 else sqrt(max(0.0, l2) / l1)
    }

    fun grade(points: List<ControlPoint>, sol: Solution?): Grade {
        val calib = points.filter { !it.relocalization }
        if (sol == null) return Grade(Level.INSUFFISANT, listOf("aucune transformation calculée"))
        val kept = calib.filter { it.id !in sol.rejected }
        val reasons = ArrayList<String>()
        var level = Level.BON
        fun atMost(l: Level, why: String) { reasons += why; if (l.ordinal > level.ordinal) level = l }
        if (kept.size < 3) atMost(Level.INSUFFISANT, "${kept.size} point(s) retenu(s), au moins 3 conseillés")
        else if (kept.size == 3) atMost(Level.MOYEN, "3 points : 4 à 6 conseillés")
        val ratio = spreadRatio(kept)
        if (kept.size >= 3 && ratio < ALIGNED_RATIO) atMost(Level.INSUFFISANT, "points alignés")
        else if (kept.size >= 3 && ratio < WEAK_RATIO) atMost(Level.MOYEN, "points presque alignés")
        if (sol.rmsM > IMPRECISE_RMS_M) atMost(Level.INSUFFISANT, "imprécis : résidu %.2f m".format(java.util.Locale.FRANCE, sol.rmsM))
        else if (sol.rmsM > GOOD_RMS_M) atMost(Level.MOYEN, "résidu %.2f m".format(java.util.Locale.FRANCE, sol.rmsM))
        if (sol.rejected.isNotEmpty()) atMost(Level.MOYEN, "${sol.rejected.size} point(s) écarté(s)")
        return Grade(level, reasons)
    }
}
