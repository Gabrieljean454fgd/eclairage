package app.lumisite.core

import app.lumisite.core.compute.Aim
import app.lumisite.core.compute.AimResult
import app.lumisite.core.compute.ArPlanes
import app.lumisite.core.compute.ArPlanes.Info
import app.lumisite.core.compute.ArPlanes.Kind
import app.lumisite.core.compute.Support
import app.lumisite.core.compute.V3
import app.lumisite.core.contract.PositionQuality
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArPlanesTest {
    private val up = V3(0.0, 1.0, 0.0)
    private fun horiz(id: String, y: Double, x0: Double, x1: Double, z0: Double, z1: Double, kind: Kind = Kind.FLOOR) =
        Info(id, kind, V3((x0 + x1) / 2, y, (z0 + z1) / 2), if (kind == Kind.CEILING) V3(0.0, -1.0, 0.0) else up,
            listOf(V3(x0, y, z0), V3(x1, y, z0), V3(x1, y, z1), V3(x0, y, z1)))
    /** Wall in the plane z = [z], from x0 to x1, y0 to y1, facing +z. */
    private fun wallZ(id: String, z: Double, x0: Double, x1: Double, y0: Double, y1: Double) =
        Info(id, Kind.WALL, V3((x0 + x1) / 2, (y0 + y1) / 2, z), V3(0.0, 0.0, 1.0), listOf(V3(x0, y0, z), V3(x1, y0, z), V3(x1, y1, z), V3(x0, y1, z)))
    /** Wall in the plane x = [x], facing +x. */
    private fun wallX(id: String, x: Double, z0: Double, z1: Double, y0: Double, y1: Double) =
        Info(id, Kind.WALL, V3(x, (y0 + y1) / 2, (z0 + z1) / 2), V3(1.0, 0.0, 0.0), listOf(V3(x, y0, z0), V3(x, y0, z1), V3(x, y1, z1), V3(x, y1, z0)))

    private val floor = horiz("sol", -1.4, -2.0, 2.0, -3.0, 0.5)
    private val table = horiz("table", -0.65, -0.5, 0.5, -1.5, -0.5)
    private val tiny = horiz("petit", -1.42, 1.0, 1.3, 0.0, 0.3)

    @Test fun areaAndFloor() {
        assertEquals(14.0, floor.area, 1e-9); assertEquals(1.0, table.area, 1e-9)
        assertEquals(-1.4, ArPlanes.floorY(listOf(table, floor, tiny))!!, 1e-9) // tiny (< 0.25 m²) ignored even if lower
    }

    @Test fun furnitureIgnoredForFloorAim() {
        assertTrue(ArPlanes.isFurniture(table, -1.4))
        val cand = ArPlanes.floorCandidates(listOf(table, floor, tiny))
        assertEquals(listOf("sol"), cand.map { it.id })
        // Aim down through the table: with the filter the floor is hit, not the table top.
        val planes = cand.map { Aim.Plane(it.center, it.normal, "AR_PLANE") }
        val hit = Aim.resolveSupport(V3(0.0, 0.0, -1.0), V3(0.0, -1.0, 0.0), planes, Support.SOL, null, true, -1.4, null) as AimResult.Hit
        assertEquals(-1.4, hit.point.y, 1e-9)
    }

    @Test fun skirtingCornerAndCornice() {
        val back = wallZ("fond", -3.0, -2.0, 2.0, -1.4, 1.1)
        val side = wallX("cote", -2.0, -3.0, 0.5, -1.4, 1.1)
        val ceiling = horiz("plafond", 1.1, -2.0, 2.0, -3.0, 0.5, Kind.CEILING)
        val skirting = assertNotNull(ArPlanes.intersection(floor, back))
        assertEquals(ArPlanes.EdgeKind.PLINTHE, skirting.kind)
        assertEquals(-1.4, skirting.a.y, 1e-9); assertEquals(-3.0, skirting.a.z, 1e-9)
        assertEquals(4.0, skirting.a.distanceTo(skirting.b), 1e-9)
        val corner = assertNotNull(ArPlanes.intersection(back, side))
        assertEquals(ArPlanes.EdgeKind.ANGLE, corner.kind)
        assertEquals(2.5, abs(corner.a.y - corner.b.y), 1e-9); assertEquals(-2.0, corner.a.x, 1e-9)
        assertEquals(ArPlanes.EdgeKind.CORNICHE, ArPlanes.intersection(back, ceiling)!!.kind)
        assertNull(ArPlanes.intersection(floor, ceiling)) // parallel
        assertEquals(5, ArPlanes.roomEdges(listOf(floor, back, side, ceiling)).size)
    }

    @Test fun edgeBoundedByPolygons() {
        // Wall seen only 1 m wide above a 4 m floor: the skirting is 1 m long, not the floor's 4 m side.
        val partial = wallZ("fond", -3.0, 0.0, 1.0, -1.4, 0.0)
        val e = assertNotNull(ArPlanes.intersection(floor, partial))
        assertEquals(1.0, e.a.distanceTo(e.b), 1e-9)
        // Wall 1 m away from the floor edge: no edge.
        assertNull(ArPlanes.intersection(floor, wallZ("loin", -4.0, -2.0, 2.0, -1.4, 1.1)))
    }

    @Test fun wallsAlignedWithSite() {
        val back = wallZ("fond", -3.0, -2.0, 2.0, -1.4, 1.1) // line along x: direction 0 rad
        val skew = Info("biais", Kind.WALL, V3(0.0, 0.0, 0.0), V3(0.5, 0.0, 0.866), back.polygon)
        assertEquals(listOf("fond"), ArPlanes.alignedWalls(listOf(back, skew), listOf(Math.toRadians(5.0))).map { it.id })
        assertEquals(2, ArPlanes.alignedWalls(listOf(back, skew), listOf(Math.toRadians(45.0))).size) // none aligned: keep all
        assertEquals(10.0, ArPlanes.angleBetweenLinesDeg(Math.toRadians(175.0), Math.toRadians(5.0)), 1e-9)
    }

    @Test fun virtualCeilingIsExplicit() {
        val o = V3(0.0, 0.0, 0.0); val upward = V3(0.0, 1.0, -1.0)
        val hit = Aim.resolveSupport(o, upward, emptyList(), Support.PLAFOND, null, true, -1.4, 2.6) as AimResult.Hit
        assertEquals(ArPlanes.CEILING_ESTIMATED, hit.source); assertEquals(1.2, hit.point.y, 1e-9)
        // No height (or no floor): never a silent point.
        assertEquals(AimResult.ToComplete, Aim.resolveSupport(o, upward, emptyList(), Support.PLAFOND, null, true, -1.4, null))
        assertEquals(AimResult.ToComplete, Aim.resolveSupport(o, upward, emptyList(), Support.PLAFOND, null, true, null, 2.6))
        // A detected ceiling wins over the virtual one.
        val real = Aim.Plane(V3(0.0, 1.0, 0.0), V3(0.0, -1.0, 0.0), "AR_CEILING")
        assertEquals("AR_CEILING", (Aim.resolveSupport(o, upward, listOf(real), Support.PLAFOND, null, true, -1.4, 2.6) as AimResult.Hit).source)
        // Wall aim without a wall: « position à compléter ».
        assertEquals(AimResult.ToComplete, Aim.resolveSupport(o, V3(0.0, 0.0, -1.0), emptyList(), Support.MUR, null, true, -1.4, 2.6))
        assertEquals(2.6, ArPlanes.ceilingHeight(1.2, -1.4)!!, 1e-9); assertNull(ArPlanes.ceilingHeight(-1.0, -1.4))
        assertEquals("estimée", PositionQuality.ESTIMATED.label)
    }
}
