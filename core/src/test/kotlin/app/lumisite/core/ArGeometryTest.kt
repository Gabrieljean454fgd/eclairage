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

    // ── build 199, point 2d : arêtes = intersections de plans bornées aux polygones ──

    private val floorEq = ArGeometry.PlaneEq(V3(0.0, 0.0, 0.0), V3(0.0, 1.0, 0.0))
    private val floorPoly = listOf(V3(-2.0, 0.0, -2.0), V3(2.0, 0.0, -2.0), V3(2.0, 0.0, 2.0), V3(-2.0, 0.0, 2.0))
    private val wallEq = ArGeometry.PlaneEq(V3(0.0, 1.0, -1.5), V3(0.0, 0.0, 1.0))

    @Test fun planeIntersectionOfFloorAndWallIsAHorizontalLine() {
        val (o, d) = ArGeometry.intersectPlanes(floorEq, wallEq)!!
        assertEquals(0.0, o.y, 1e-9); assertEquals(-1.5, o.z, 1e-9)          // the line lies in both planes
        assertEquals(0.0, d.y, 1e-9); assertEquals(0.0, d.z, 1e-9)
        assertEquals(1.0, kotlin.math.abs(d.x), 1e-9)
    }

    @Test fun quasiParallelPlanesGiveNoLine() {
        // 5° between the normals (< 10°): rejected, the line position would tremble.
        val a = Math.toRadians(5.0)
        assertNull(ArGeometry.intersectPlanes(floorEq, ArGeometry.PlaneEq(V3(0.0, 0.0, 0.0), V3(0.0, kotlin.math.cos(a), kotlin.math.sin(a)))))
        // Opposite normals are parallel too; 45° crosses.
        assertNull(ArGeometry.intersectPlanes(floorEq, ArGeometry.PlaneEq(V3(0.0, 0.0, 0.0), V3(0.0, -1.0, 0.0))))
        val b = Math.toRadians(45.0)
        assertTrue(ArGeometry.intersectPlanes(floorEq, ArGeometry.PlaneEq(V3(0.0, 0.0, 0.0), V3(0.0, kotlin.math.cos(b), kotlin.math.sin(b)))) != null)
        // Degenerate normal: no crash, no line.
        assertNull(ArGeometry.intersectPlanes(floorEq, ArGeometry.PlaneEq(V3(0.0, 0.0, 0.0), V3(0.0, 0.0, 0.0))))
    }

    @Test fun skirtingEdgeIsBoundedByBothPolygons() {
        // Wall at z = -1.5 but only 2 m wide (x ∈ [-1, 1]); the floor runs 4 m wide.
        val wallPoly = listOf(V3(-1.0, 0.0, -1.5), V3(1.0, 0.0, -1.5), V3(1.0, 2.5, -1.5), V3(-1.0, 2.5, -1.5))
        val (p, q) = ArGeometry.edgeSegment(floorEq, floorPoly, wallEq, wallPoly)!!
        assertEquals(0.0, p.y, 1e-9); assertEquals(-1.5, p.z, 1e-9)
        assertEquals(0.0, q.y, 1e-9); assertEquals(-1.5, q.z, 1e-9)
        val xs = listOf(p.x, q.x).sorted()
        assertEquals(-1.0, xs[0], 1e-9); assertEquals(1.0, xs[1], 1e-9)
    }

    @Test fun noEdgeWhenTheMeetingIsOutsideAPolygon() {
        // Wall floating above the floor: the intersection line (y = 0) is under the wall polygon (y ∈ [1, 3]).
        val high = listOf(V3(-1.0, 1.0, -1.5), V3(1.0, 1.0, -1.5), V3(1.0, 3.0, -1.5), V3(-1.0, 3.0, -1.5))
        assertNull(ArGeometry.edgeSegment(floorEq, floorPoly, wallEq, high))
        // Wall beyond the floor polygon (z = -5 outside z ∈ [-2, 2]).
        val far = listOf(V3(-1.0, 0.0, -5.0), V3(1.0, 0.0, -5.0), V3(1.0, 2.5, -5.0), V3(-1.0, 2.5, -5.0))
        assertNull(ArGeometry.edgeSegment(floorEq, floorPoly, ArGeometry.PlaneEq(V3(0.0, 1.0, -5.0), V3(0.0, 0.0, 1.0)), far))
        // Degenerate polygon: no edge.
        assertNull(ArGeometry.edgeSegment(floorEq, floorPoly, wallEq, listOf(V3(0.0, 0.0, -1.5))))
    }

    @Test fun wallWallMeetingIsAVerticalCorner() {
        // Wall A z = -1 spanning x ∈ [0, 2]; wall B x = 0 spanning z ∈ [-3, -1]. Shared line x = 0, z = -1, y up.
        val aEq = ArGeometry.PlaneEq(V3(1.0, 1.0, -1.0), V3(0.0, 0.0, 1.0))
        val aPoly = listOf(V3(0.0, 0.0, -1.0), V3(2.0, 0.0, -1.0), V3(2.0, 2.5, -1.0), V3(0.0, 2.5, -1.0))
        val bEq = ArGeometry.PlaneEq(V3(0.0, 1.0, -2.0), V3(1.0, 0.0, 0.0))
        val bPoly = listOf(V3(0.0, 0.0, -1.0), V3(0.0, 0.0, -3.0), V3(0.0, 2.5, -3.0), V3(0.0, 2.5, -1.0))
        val (p, q) = ArGeometry.edgeSegment(aEq, aPoly, bEq, bPoly)!!
        assertEquals(0.0, p.x, 1e-9); assertEquals(-1.0, p.z, 1e-9)
        assertEquals(0.0, q.x, 1e-9); assertEquals(-1.0, q.z, 1e-9)
        val ys = listOf(p.y, q.y).sorted()
        assertEquals(0.0, ys[0], 1e-9); assertEquals(2.5, ys[1], 1e-9)
    }

    @Test fun clipLineToConvexPolygon() {
        // Diagonal through a square; reversed vertex order clips identically (inward normals follow the centroid).
        val iv = ArGeometry.clipToPolygon(V3(0.0, 0.0, 0.0), V3(1.0, 0.0, 0.0), floorPoly, V3(0.0, 1.0, 0.0))!!
        assertEquals(-2.0, iv.first, 1e-9); assertEquals(2.0, iv.second, 1e-9)
        val ivRev = ArGeometry.clipToPolygon(V3(0.0, 0.0, 0.0), V3(1.0, 0.0, 0.0), floorPoly.reversed(), V3(0.0, 1.0, 0.0))!!
        assertEquals(iv.first, ivRev.first, 1e-9); assertEquals(iv.second, ivRev.second, 1e-9)
        // Line parallel to an edge, outside the polygon.
        assertNull(ArGeometry.clipToPolygon(V3(0.0, 0.0, 3.0), V3(1.0, 0.0, 0.0), floorPoly, V3(0.0, 1.0, 0.0)))
    }

    @Test fun polygonAreaIsShoelaceOnLocalXz() {
        assertEquals(1.0, ArGeometry.polygonAreaM2(listOf(-0.5, -0.5, 0.5, -0.5, 0.5, 0.5, -0.5, 0.5)), 1e-9)
        assertEquals(0.5, ArGeometry.polygonAreaM2(listOf(0.0, 0.0, 1.0, 0.0, 0.0, 1.0)), 1e-9)
        assertEquals(0.0, ArGeometry.polygonAreaM2(emptyList()), 1e-9)
    }
}
