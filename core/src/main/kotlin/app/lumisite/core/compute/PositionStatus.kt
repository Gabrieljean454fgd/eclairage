package app.lumisite.core.compute

import app.lumisite.core.model.TrackingState

/**
 * What Session > Carte must ALWAYS show about the live position (stage 2.8): its source, the calibration state,
 * whether tracking is lost (grey arrow) and the age of the last valid position. Never invents a position.
 */
data class PositionStatus(
    val source: Source,
    val calibrated: Boolean,
    val lost: Boolean,
    val ageMs: Long?,
) {
    enum class Source(val label: String) { AR("AR"), GPS("GPS"), MANUAL("manuelle"), NONE("aucune") }

    fun text(): String = buildString {
        append("Source : ").append(source.label)
        append(" · ").append(if (calibrated) "calibré" else "non calibré")
        if (lost) append(" · suivi perdu")
        ageMs?.let { append(" · dernière position valide il y a ").append(age(it)) }
    }

    companion object {
        /** Lost when no valid AR position since this long. */
        const val STALE_MS = 2_000L

        fun age(ms: Long): String = when {
            ms < 1_000 -> "< 1 s"
            ms < 60_000 -> "${ms / 1000} s"
            ms < 3_600_000 -> "${ms / 60_000} min ${(ms / 1000) % 60} s"
            else -> "${ms / 3_600_000} h ${(ms / 60_000) % 60} min"
        }

        /**
         * @param lastArValidMs wall time of the last TRACKING pose with a calibration (null: never)
         * @param lastGpsMs wall time of the last GPS fix (null: none)
         * @param manual true when the user picked a point on the map
         */
        fun of(nowMs: Long, calibrated: Boolean, tracking: TrackingState, lastArValidMs: Long?, lastGpsMs: Long?, manual: Boolean): PositionStatus {
            val arFresh = calibrated && tracking == TrackingState.TRACKING && lastArValidMs != null && nowMs - lastArValidMs <= STALE_MS
            val source = when {
                arFresh || (calibrated && lastArValidMs != null) -> Source.AR
                lastGpsMs != null -> Source.GPS
                manual -> Source.MANUAL
                else -> Source.NONE
            }
            val last = when (source) { Source.AR -> lastArValidMs; Source.GPS -> lastGpsMs; else -> null }
            return PositionStatus(source, calibrated, lost = source == Source.AR && !arFresh, ageMs = last?.let { (nowMs - it).coerceAtLeast(0) })
        }
    }
}
