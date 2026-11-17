package app.lumisite.core.compute

import kotlin.math.asin
import kotlin.math.sqrt

/**
 * Retour terrain build 199 (17/11), point 6 : le type de support visé (sol / mur / plafond) se déduit de l'inclinaison
 * du téléphone, sans sélecteur à l'écran. Fonctions pures, testées sur JVM (SupportFromTiltTest).
 *
 * Élévation de l'axe de la caméra arrière, en degrés : −90 = vers le bas (sol), 0 = horizontal (mur), +90 = vers le haut (plafond).
 * Seuils par défaut : < −35° sol, > +35° plafond, sinon mur ; hystérésis de 5° autour de chaque seuil pour éviter
 * le clignotement quand on vise un angle de pièce.
 * En production, l'angle vient de l'existant : Tilt.fromArQuaternion (pose caméra ARCore, même convention, négatif = vers le bas)
 * ou Tilt.fromRotationMatrix (capteurs, si ARCore ne suit pas). Les deux aides ci-dessous servent aux tests et au repli capteur gravité.
 */
object SupportFromTilt {
    enum class Support { SOL, MUR, PLAFOND }

    const val FLOOR_BELOW_DEG = -35.0
    const val CEILING_ABOVE_DEG = 35.0
    const val HYSTERESIS_DEG = 5.0

    /**
     * Depuis le capteur gravité (Sensor.TYPE_GRAVITY, repère de l'appareil, m/s²) : à plat écran vers le haut, la mesure vaut
     * (0, 0, +9,81). La caméra arrière regarde selon −Z de l'appareil ; la verticale montante vaut g/|g|.
     * Renvoie null si le vecteur est nul (capteur absent).
     */
    fun elevationFromGravity(gx: Double, gy: Double, gz: Double): Double? {
        val n = sqrt(gx * gx + gy * gy + gz * gz)
        if (n < 1e-6) return null
        return Math.toDegrees(asin((-gz / n).coerceIn(-1.0, 1.0)))
    }

    /** Depuis la direction de visée ARCore (repère monde, y vers le haut), p. ex. Aim.forward(q). */
    fun elevationFromForward(forward: V3): Double? {
        val n = sqrt(forward.x * forward.x + forward.y * forward.y + forward.z * forward.z)
        if (n < 1e-9) return null
        return Math.toDegrees(asin((forward.y / n).coerceIn(-1.0, 1.0)))
    }

    /**
     * Classement avec hystérésis : tant que l'angle reste à moins de [hysteresis] degrés du seuil franchi, on garde [previous].
     */
    fun classify(
        elevationDeg: Double,
        previous: Support? = null,
        floorBelow: Double = FLOOR_BELOW_DEG,
        ceilingAbove: Double = CEILING_ABOVE_DEG,
        hysteresis: Double = HYSTERESIS_DEG,
    ): Support {
        val raw = when {
            elevationDeg < floorBelow -> Support.SOL
            elevationDeg > ceilingAbove -> Support.PLAFOND
            else -> Support.MUR
        }
        if (previous == null || previous == raw) return raw
        val keep = when (previous) {
            Support.SOL -> elevationDeg < floorBelow + hysteresis
            Support.PLAFOND -> elevationDeg > ceilingAbove - hysteresis
            Support.MUR -> elevationDeg >= floorBelow - hysteresis && elevationDeg <= ceilingAbove + hysteresis
        }
        return if (keep) previous else raw
    }

    /** Source de plan ARCore correspondante, même convention que Aim.sourceOf (AR_PLANE / AR_WALL / AR_CEILING). */
    fun planeSource(s: Support): String = when (s) { Support.SOL -> "AR_PLANE"; Support.MUR -> "AR_WALL"; Support.PLAFOND -> "AR_CEILING" }

    /** Libellé court affiché sous la caméra (remplace les puces Tous / Sol / Mur / Plafond). */
    fun label(s: Support): String = when (s) { Support.SOL -> "Visée : sol (auto)"; Support.MUR -> "Visée : mur (auto)"; Support.PLAFOND -> "Visée : plafond (auto)" }
}
