package app.lumisite.core

import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.survey.DragMove
import app.lumisite.core.survey.ScreenProjection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DragMoveTest {
    private val origin = LatLon(43.6045, 1.4440)
    private val ltp = LocalTangentPlane(origin)

    @Test fun screenRoundTrip() {
        val pr = ScreenProjection(origin, 20.0, 1080, 1200)
        val ll = pr.fromPixel(700.0, 300.0)
        val (x, y) = pr.toPixel(ll)
        assertEquals(700.0, x, 1e-6); assertEquals(300.0, y, 1e-6)
        val c = pr.toPixel(origin)
        assertEquals(540.0, c.first, 1e-6); assertEquals(600.0, c.second, 1e-6)
    }

    /** A finger drag of (dx, dy) pixels moves the object by (dx, −dy) × metres per pixel in the plane, keeping the grab offset. */
    @Test fun dragFollowsTheFingerInPlaneCoordinates() {
        val pr = ScreenProjection(origin, 20.0, 1080, 1200)
        val mpp = pr.metresPerPixel()
        val obj = Enu(3.0, 4.0, 2.5)
        val (ox, oy) = pr.toPixel(ltp.toLatLon(obj))
        val grab = ltp.toEnu(pr.fromPixel(ox + 12, oy - 8)) // grabbed slightly off the symbol
        val finger = ltp.toEnu(pr.fromPixel(ox + 12 + 100, oy - 8 + 50))
        val t = DragMove.target(obj, grab, finger)
        // Mercator scale varies with latitude: metres per pixel at the centre are exact to ~0.2 % a few metres away.
        assertEquals(3.0 + 100 * mpp, t.e, 0.05)
        assertEquals(4.0 - 50 * mpp, t.n, 0.05)
        assertEquals(2.5, t.u)
        val (tx, ty) = pr.toPixel(ltp.toLatLon(t.copy(u = 0.0)))
        assertTrue(abs(tx - (ox + 100)) < 0.5 && abs(ty - (oy + 50)) < 0.5, "symbol under the dragged position: $tx,$ty")
    }

    @Test fun noMoveWithoutFingerMotion() {
        val g = Enu(10.0, -2.0, 0.0)
        assertEquals(Enu(1.0, 1.0, 0.3), DragMove.target(Enu(1.0, 1.0, 0.3), g, g))
    }
}
