package app.lumisite.core.compute

/**
 * Retour terrain build 199 (point 2e) et 22/11 (table vs sol) : filtre pur branché avant Aim.resolve.
 *  - plan de moins de [MIN_AREA_M2] m² : poussière de détection, ignoré (tous types) ;
 *  - sans hauteur de caméra : sol = plan horizontal (« AR_PLANE ») le plus bas, bande [FLOOR_BAND_M] ;
 *  - avec [cameraY] : sol = plan le plus bas situé à [CAMERA_FLOOR_MIN_M]–[CAMERA_FLOOR_MAX_M] m
 *    sous l'appareil (défaut 1,2–1,6 m, nominal 1,4 m). Un plan plus haut (ex. un plateau de table)
 *    est du mobilier, même s'il est le seul plan détecté ; le sol est alors estimé à cameraY − 1,4 m ;
 *  - plan horizontal entre [FURNITURE_MIN_M] et [FURNITURE_MAX_M] m au-dessus du sol : mobilier, ignoré.
 * Les murs et plafonds (hors taille minimale) passent tels quels.
 */
object PlaneFilter {
    const val MIN_AREA_M2 = 0.25
    const val FLOOR_BAND_M = 0.10
    const val FURNITURE_MIN_M = 0.30
    const val FURNITURE_MAX_M = 2.0
    /** Hauteur typique de l'appareil au-dessus du sol (même valeur que Aim.DEFAULT_CAMERA_HEIGHT_M). */
    const val CAMERA_FLOOR_NOM_M = 1.4
    const val CAMERA_FLOOR_MIN_M = 1.2
    const val CAMERA_FLOOR_MAX_M = 1.6

    /**
     * Plan détecté à un instant : position du centre, source Aim (« AR_PLANE » horizontal montant,
     * « AR_WALL », « AR_CEILING ») et aire du polygone ARCore. [payload] = donnée portée (plan ARCore,
     * Aim.Plane, indice…) jamais lue par le filtre.
     */
    data class Desc<out T>(val point: V3, val source: String, val areaM2: Double, val payload: T)

    enum class Role { FLOOR, FURNITURE, KEPT, TOO_SMALL }

    /**
     * Hauteur du sol. Sans [cameraY] : le plan horizontal assez grand le plus bas (comportement 199).
     * Avec [cameraY] : le plus bas dans la bande 1,2–1,6 m sous l'appareil, sinon le plus bas s'il est
     * au moins 1,2 m sous l'appareil, sinon l'estimation cameraY − 1,4 m (une table seule n'est pas le sol).
     */
    fun <T> floorLevel(planes: List<Desc<T>>, cameraY: Double? = null): Double? {
        val cands = planes.filter { it.source == "AR_PLANE" && it.areaM2 >= MIN_AREA_M2 }
        if (cands.isEmpty()) return cameraY?.minus(CAMERA_FLOOR_NOM_M)
        val lowest = cands.minOf { it.point.y }
        if (cameraY == null) return lowest
        val inBand = cands.filter { (cameraY - it.point.y) in CAMERA_FLOOR_MIN_M..CAMERA_FLOOR_MAX_M }
        if (inBand.isNotEmpty()) return inBand.minOf { it.point.y }
        return if (cameraY - lowest >= CAMERA_FLOOR_MIN_M) lowest else cameraY - CAMERA_FLOOR_NOM_M
    }

    fun <T> roleOf(d: Desc<T>, floorY: Double?, cameraY: Double? = null): Role = when {
        d.areaM2 < MIN_AREA_M2 -> Role.TOO_SMALL
        d.source != "AR_PLANE" || floorY == null -> Role.KEPT
        d.point.y <= floorY + FLOOR_BAND_M -> Role.FLOOR
        d.point.y - floorY in FURNITURE_MIN_M..FURNITURE_MAX_M -> Role.FURNITURE
        cameraY != null && (cameraY - d.point.y) < CAMERA_FLOOR_MIN_M && d.point.y > floorY + FLOOR_BAND_M -> Role.FURNITURE
        else -> Role.KEPT
    }

    /** Plans proposés à la visée : tout sauf la poussière et le mobilier. */
    fun <T> forAim(planes: List<Desc<T>>, cameraY: Double? = null): List<Desc<T>> {
        val fy = floorLevel(planes, cameraY)
        return planes.filter { roleOf(it, fy, cameraY).let { r -> r != Role.TOO_SMALL && r != Role.FURNITURE } }
    }
}
