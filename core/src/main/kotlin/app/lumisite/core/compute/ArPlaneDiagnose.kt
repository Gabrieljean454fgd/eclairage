package app.lumisite.core.compute

/**
 * Build 248 : statistiques pures (JVM-testables) sur les plans ARCore, pour distinguer
 * "aucun plan détecté", "plans non tracking", "polygones invalides" et "renderer alimenté".
 */
object ArPlaneDiagnose {
    /** Description d'un plan ARCore réduite aux champs utiles au diagnostic. */
    data class PlaneDesc(
        val type: String,
        val trackingState: String,
        val areaM2: Double,
        val vertexCount: Int,
    )

    data class Stats(
        val totalPlanes: Int,
        val trackingPlanes: Int,
        val byType: Map<String, Int>,
        val byState: Map<String, Int>,
        val validPolygons: Int,
        val invalidPolygons: Int,
        val minAreaM2: Double,
        val maxAreaM2: Double,
        val avgAreaM2: Double,
        val rendererGotPolygons: Boolean,
    )

    fun stats(descs: List<PlaneDesc>): Stats {
        val valid = descs.filter { it.vertexCount >= 3 && it.areaM2 >= 0.0 && it.areaM2.isFinite() }
        val areas = valid.map { it.areaM2 }
        return Stats(
            totalPlanes = descs.size,
            trackingPlanes = descs.count { it.trackingState == "TRACKING" },
            byType = descs.groupingBy { it.type }.eachCount(),
            byState = descs.groupingBy { it.trackingState }.eachCount(),
            validPolygons = valid.size,
            invalidPolygons = descs.size - valid.size,
            minAreaM2 = areas.minOrNull() ?: 0.0,
            maxAreaM2 = areas.maxOrNull() ?: 0.0,
            avgAreaM2 = if (areas.isEmpty()) 0.0 else areas.average(),
            rendererGotPolygons = valid.any { it.trackingState == "TRACKING" },
        )
    }
}
