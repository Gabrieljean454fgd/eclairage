package app.lumisite.core.compute

/**
 * Retour terrain build 199 (17/11), point 2e : Aim.resolve prend le plan le plus proche du rayon, donc une
 * table (plan horizontal au-dessus du sol) gagnait contre le sol. Filtre pur branché avant Aim.resolve dans
 * ArTracker.updateAim :
 *  - plan de moins de [MIN_AREA_M2] m² : poussière de détection, ignoré (tous types) ;
 *  - sol = plan horizontal (« AR_PLANE ») le plus bas, les plans à ± [FLOOR_BAND_M] m de lui sont le même sol ;
 *  - plan horizontal entre [FURNITURE_MIN_M] m et [FURNITURE_MAX_M] m au-dessus du sol : mobilier, ignoré.
 * Les murs et plafonds (hors taille minimale) passent tels quels ; un horizontal sous +30 cm ou au-dessus de
 * +2 m (estrade, mezzanine) n'est pas du mobilier et reste visé.
 */
object PlaneFilter {
    const val MIN_AREA_M2 = 0.25
    const val FLOOR_BAND_M = 0.10
    const val FURNITURE_MIN_M = 0.30
    const val FURNITURE_MAX_M = 2.0

    /**
     * Plan détecté à un instant : position du centre, source Aim (« AR_PLANE » horizontal montant,
     * « AR_WALL », « AR_CEILING ») et aire du polygone ARCore. [payload] = donnée portée (plan ARCore,
     * Aim.Plane, indice…) jamais lue par le filtre.
     */
    data class Desc<out T>(val point: V3, val source: String, val areaM2: Double, val payload: T)

    enum class Role { FLOOR, FURNITURE, KEPT, TOO_SMALL }

    /** Hauteur du sol : le plan horizontal assez grand le plus bas ; null quand aucun n'est détecté. */
    fun <T> floorLevel(planes: List<Desc<T>>): Double? =
        planes.filter { it.source == "AR_PLANE" && it.areaM2 >= MIN_AREA_M2 }.minOfOrNull { it.point.y }

    fun <T> roleOf(d: Desc<T>, floorY: Double?): Role = when {
        d.areaM2 < MIN_AREA_M2 -> Role.TOO_SMALL
        d.source != "AR_PLANE" || floorY == null -> Role.KEPT
        d.point.y <= floorY + FLOOR_BAND_M -> Role.FLOOR
        d.point.y - floorY in FURNITURE_MIN_M..FURNITURE_MAX_M -> Role.FURNITURE
        else -> Role.KEPT
    }

    /** Plans proposés à la visée : tout sauf la poussière et le mobilier. Le sol le plus bas gagne toujours. */
    fun <T> forAim(planes: List<Desc<T>>): List<Desc<T>> {
        val fy = floorLevel(planes)
        return planes.filter { roleOf(it, fy).let { r -> r != Role.TOO_SMALL && r != Role.FURNITURE } }
    }
}
