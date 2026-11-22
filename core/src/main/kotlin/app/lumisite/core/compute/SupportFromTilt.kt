package app.lumisite.core.compute

/** v176g § 2a: surface aimed at, deduced from the camera elevation (no Sol / Mur / Plafond chips any more). */
enum class Support(val label: String, val source: String) { SOL("sol", "AR_PLANE"), MUR("mur", "AR_WALL"), PLAFOND("plafond", "AR_CEILING") }

object SupportFromTilt {
    const val THRESHOLD_DEG = 35.0
    const val HYSTERESIS_DEG = 5.0

    /**
     * [elevationDeg]: camera axis above the horizon (negative = looking down, [Tilt]). Below −35° floor, above +35° ceiling,
     * wall between, with 5° of hysteresis: from MUR one stays MUR until −40° / +40°; from SOL / PLAFOND one stays until −30° / +30°.
     */
    fun classify(elevationDeg: Double, previous: Support?): Support {
        val t = THRESHOLD_DEG; val h = HYSTERESIS_DEG
        return when (previous) {
            Support.MUR -> when { elevationDeg < -(t + h) -> Support.SOL; elevationDeg > t + h -> Support.PLAFOND; else -> Support.MUR }
            Support.SOL -> when { elevationDeg < -(t - h) -> Support.SOL; elevationDeg > t + h -> Support.PLAFOND; else -> Support.MUR }
            Support.PLAFOND -> when { elevationDeg > t - h -> Support.PLAFOND; elevationDeg < -(t + h) -> Support.SOL; else -> Support.MUR }
            null -> when { elevationDeg < -t -> Support.SOL; elevationDeg > t -> Support.PLAFOND; else -> Support.MUR }
        }
    }

    fun autoLine(s: Support): String = "Visée : ${s.label} (auto)"
}
