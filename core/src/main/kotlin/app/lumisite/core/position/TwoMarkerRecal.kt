package app.lumisite.core.position

import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * v176f F3 — « Recaler sur 2 repères » à la reprise d'une session : l'utilisateur vise au réticule deux repères
 * (marques réelles) dont la position sur le plan est connue (points de calibrage de la session précédente, dessinés
 * « à confirmer » en pointillé). Les deux visées donnent une nouvelle transformation plan session AR → site ENU
 * (rotation + translation, échelle fixée à 1) ; un 3e repère visé sert de contrôle hors calcul.
 * Fonctions pures : testées en JVM, sans ARCore ni Android.
 */
object TwoMarkerRecal {
    /** Une visée sur un repère : [local] = point visé dans le plan de la session AR (x, -z, mètres), [target] = repère du plan (site ENU, mètres). */
    data class Observation(val local: Vec2, val target: Vec2)

    data class Estimate(
        val transform: Similarity2D,
        /** Écart (m) de chaque repère sous la transformation, dans l'ordre des visées. */
        val residualsM: List<Double>,
        val rmsM: Double,
    )

    /** Sous cette séparation les deux repères (ou les deux points visés) ne donnent pas de rotation fiable. */
    const val MIN_SEPARATION_M = 0.5

    /**
     * Transformation rigide (rotation + translation, échelle 1) estimée des deux visées : la rotation est celle qui
     * aligne la direction visée sur la direction des repères, et le pivot est le milieu des deux points — un écart
     * d'échelle AR/carte éventuel est donc partagé par moitié sur les deux repères au lieu d'écraser l'un des deux.
     * Null si les repères (ou les points visés) sont à moins de [MIN_SEPARATION_M] l'un de l'autre.
     */
    fun estimate(a: Observation, b: Observation): Estimate? {
        val dl = b.local - a.local
        val dt = b.target - a.target
        if (dl.norm() < MIN_SEPARATION_M || dt.norm() < MIN_SEPARATION_M) return null
        val rot = atan2(dt.y, dt.x) - atan2(dl.y, dl.x)
        val tr = Similarity2D.fromPointAndRotation((a.local + b.local) * 0.5, (a.target + b.target) * 0.5, rot)
        val res = listOf(a, b).map { (tr.apply(it.local) - it.target).norm() }
        return Estimate(tr, res, sqrt(res.sumOf { it * it } / res.size))
    }

    /** Écart (m) du repère de contrôle [o] sous la transformation [t] (3e repère visé, hors calcul). */
    fun controlResidualM(t: Similarity2D, o: Observation): Double = (t.apply(o.local) - o.target).norm()

    /**
     * Ordre de visée indiqué à l'utilisateur : la paire de repères la plus écartée d'abord (la rotation est d'autant
     * mieux conditionnée que les repères sont éloignés), puis les autres repères dans leur ordre — ils servent de
     * contrôle. [targets] = positions plan (site ENU) des repères de la session précédente.
     */
    fun suggestedOrder(targets: List<Vec2>): List<Int> {
        if (targets.size <= 2) return targets.indices.toList()
        var best = 0 to 1
        var bestD = -1.0
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val d = (targets[i] - targets[j]).norm()
            if (d > bestD) { bestD = d; best = i to j }
        }
        return listOf(best.first, best.second) + targets.indices.filter { it != best.first && it != best.second }
    }
}
