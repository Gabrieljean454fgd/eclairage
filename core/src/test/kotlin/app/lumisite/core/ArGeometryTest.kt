package app.lumisite.core

import app.lumisite.core.compute.ArGeometry
import app.lumisite.core.compute.V3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArGeometryTest {
    private val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)

    @Test fun projectionCentreCornersAndBehind() {
        // Simple perspective: w = -z (camera looks down -z), x/y unchanged.
        val vp = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, -1.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(540.0 to 405.0, ArGeometry.project(vp, V3(0.0, 0.0, -2.0), 1080, 810))
        val (x, y) = ArGeometry.project(vp, V3(1.0, 1.0, -1.0), 1080, 810)!!
        assertEquals(1080.0, x, 1e-9); assertEquals(0.0, y, 1e-9)
        assertNull(ArGeometry.project(vp, V3(0.0, 0.0, 2.0), 1080, 810))
    }

    @Test fun polygonOfAVerticalPlaneGivesTwoVerticalEdges() {
        // Wall plane: local x along world x, local z along world -y (plane normal = +z), centred at (1, 1.5, -3).
        val pose = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 1.0, 1.5, -3.0, 1.0)
        val world = ArGeometry.polygonToWorld(pose, listOf(-2.0, -1.0, 2.0, -1.0, 2.0, 1.0, -2.0, 1.0))
        assertEquals(V3(-1.0, 2.5, -3.0), world[0])
        val edges = ArGeometry.verticalEdges(world)
        assertEquals(2, edges.size)
        val xs = edges.map { it.first.x }.sorted()
        assertEquals(-1.0, xs[0], 1e-9); assertEquals(3.0, xs[1], 1e-9)
        edges.forEach { (a, b) -> assertEquals(0.5, a.y, 1e-9); assertEquals(2.5, b.y, 1e-9); assertEquals(a.x, b.x, 1e-9); assertEquals(-3.0, a.z, 1e-9) }
    }

    @Test fun degenerateWallsAndFlagAndSymbol() {
        assertTrue(ArGeometry.verticalEdges(listOf(V3(0.0, 0.0, 0.0), V3(1.0, 0.0, 0.0))).isEmpty())
        // Flat (floor) polygon: no vertical extent, no edge.
        assertTrue(ArGeometry.verticalEdges(ArGeometry.polygonToWorld(identity, listOf(-1.0, -1.0, 1.0, -1.0, 1.0, 1.0))).isEmpty())
        val f = ArGeometry.flag(V3(0.0, 0.0, 0.0))
        assertEquals(V3(0.0, 0.3, 0.0), f[0].second)
        assertEquals(3, f.size)
        assertEquals(18, ArGeometry.luminaire(V3(0.0, 2.8, 0.0)).size)
    }
}
