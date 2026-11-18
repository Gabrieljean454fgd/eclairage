package app.lumisite.core

import app.lumisite.core.compute.PlaneFilter
import app.lumisite.core.compute.V3
import kotlin.test.Test
import kotlin.test.assertEquals

/** build 199, point 2e : le mobilier ne gagne plus contre le sol dans la visée ; les petits plans sont ignorés. */
class PlaneFilterTest {
    private fun pl(y: Double, source: String = "AR_PLANE", area: Double = 4.0) =
        PlaneFilter.Desc(V3(0.0, y, 0.0), source, area, "$source@$y")

    @Test fun furnitureAboveTheFloorIsIgnored() {
        // Floor at 0 + table top at 0,9 m : seul le sol reste proposé à la visée.
        val out = PlaneFilter.forAim(listOf(pl(0.0), pl(0.9)))
        assertEquals(listOf(0.0), out.map { it.point.y })
        assertEquals(PlaneFilter.Role.FURNITURE, PlaneFilter.roleOf(pl(0.9), PlaneFilter.floorLevel(listOf(pl(0.0), pl(0.9)))))
    }

    @Test fun lowestPlaneIsTheFloorWithinTenCentimetres() {
        // Deux fragments du même sol (−2 cm et +8 cm) : tous deux gardés.
        val out = PlaneFilter.forAim(listOf(pl(-0.02), pl(0.08), pl(0.9)))
        assertEquals(listOf(-0.02, 0.08), out.map { it.point.y })
    }

    @Test fun furnitureBandIsInclusiveAndBounded() {
        // 0,30 m et 2,0 m inclus = mobilier ; estrade à +0,2 m et mezzanine à +2,5 m restent visées.
        val planes = listOf(pl(0.0), pl(0.2), pl(0.3), pl(2.0), pl(2.5))
        assertEquals(listOf(0.0, 0.2, 2.5), PlaneFilter.forAim(planes).map { it.point.y })
        val fy = PlaneFilter.floorLevel(planes)
        assertEquals(0.0, fy!!, 1e-9)
        assertEquals(PlaneFilter.Role.FURNITURE, PlaneFilter.roleOf(pl(0.3), fy))
        assertEquals(PlaneFilter.Role.FURNITURE, PlaneFilter.roleOf(pl(2.0), fy))
        assertEquals(PlaneFilter.Role.KEPT, PlaneFilter.roleOf(pl(2.5), fy))
    }

    @Test fun aLoneHorizontalPlaneIsTheFloorWhateverItsHeight() {
        // Table seule détectée : c'est le référentiel sol (on ne peut pas faire mieux).
        assertEquals(listOf(0.9), PlaneFilter.forAim(listOf(pl(0.9))).map { it.point.y })
    }

    @Test fun tinyPlanesAreDroppedWhateverTheType() {
        val out = PlaneFilter.forAim(listOf(
            pl(0.0, area = 0.10),                    // poussière de sol
            pl(0.0, "AR_WALL", 0.20),                // fragment de mur < 0,25 m²
            pl(1.2, "AR_WALL", 1.5),                 // vrai mur
            pl(2.7, "AR_CEILING", 3.0),              // plafond
        ))
        assertEquals(listOf("AR_WALL@1.2", "AR_CEILING@2.7"), out.map { it.payload })
        assertEquals(PlaneFilter.Role.TOO_SMALL, PlaneFilter.roleOf(pl(0.0, area = 0.10), 0.0))
    }

    @Test fun wallsAndCeilingsAreNotFurniture() {
        // Un mur ou un plafond dans la bande 0,3–2,0 m au-dessus du sol n'est jamais filtré.
        val out = PlaneFilter.forAim(listOf(pl(0.0), pl(1.4, "AR_WALL"), pl(1.4, "AR_CEILING")))
        assertEquals(3, out.size)
    }

    @Test fun noFloorDetectedMeansNoFurnitureFilter() {
        // Sans plan horizontal : rien à retrancher, les murs passent.
        val out = PlaneFilter.forAim(listOf(pl(1.0, "AR_WALL"), pl(0.9, "AR_WALL")))
        assertEquals(2, out.size)
        assertEquals(null, PlaneFilter.floorLevel(out))
    }
}
