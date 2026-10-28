package app.lumisite.core

import app.lumisite.core.geo.Angles
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LatLon
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Vec2
import app.lumisite.core.geo.Wgs84
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class GeoTest {
    private val ltp = LocalTangentPlane(Fixtures.origin)

    @Test fun originMapsToZero() {
        val e = ltp.toEnu(Fixtures.origin)
        assertEquals(0.0, e.e, 1e-6); assertEquals(0.0, e.n, 1e-6); assertEquals(0.0, e.u, 1e-6)
    }

    @Test fun ecefRoundTrip() {
        val p = LatLon(45.123456, -1.987654, 123.4)
        val r = Wgs84.fromEcef(Wgs84.toEcef(p))
        assertEquals(p.lat, r.lat, 1e-9); assertEquals(p.lon, r.lon, 1e-9); assertEquals(p.h!!, r.h!!, 1e-4)
    }

    @Test fun enuRoundTripMillimetre() {
        for (enu in listOf(Enu(10.0, 20.0), Enu(-350.0, 812.5), Enu(1500.0, -2000.0, 3.0))) {
            val back = ltp.toEnu(ltp.toLatLon(enu))
            assertEquals(enu.e, back.e, 1e-3); assertEquals(enu.n, back.n, 1e-3)
        }
    }

    @Test fun knownDistancesNorthAndEast() {
        // 0.001° of latitude at 48.8566°N ≈ 111.2 m; 0.001° of longitude ≈ 73.3 m (WGS84)
        val n = ltp.toEnu(LatLon(Fixtures.origin.lat + 0.001, Fixtures.origin.lon))
        val e = ltp.toEnu(LatLon(Fixtures.origin.lat, Fixtures.origin.lon + 0.001))
        assertEquals(111.2, n.n, 0.1); assertEquals(0.0, n.e, 1e-3)
        assertEquals(73.3, e.e, 0.1); assertEquals(0.0, e.n, 1e-3)
    }

    @Test fun similarityFromTwoPointsRecoversTransform() {
        val truth = Similarity2D(1.0, 0.7, 12.0, -4.0)
        val a = Vec2(1.0, 2.0); val b = Vec2(-3.0, 8.0)
        val t = Similarity2D.fromTwoPoints(a, b, truth.apply(a), truth.apply(b))
        assertEquals(truth.scale, t.scale, 1e-9); assertEquals(truth.rotationRad, t.rotationRad, 1e-9)
        assertEquals(truth.tx, t.tx, 1e-9); assertEquals(truth.ty, t.ty, 1e-9)
    }

    @Test fun similarityCompositionMatchesSequentialApplication() {
        val a = Similarity2D(2.0, 0.3, 1.0, 2.0); val b = Similarity2D(0.5, -1.1, -4.0, 3.0)
        val p = Vec2(3.3, -7.1)
        val seq = b.apply(a.apply(p)); val comp = a.then(b).apply(p)
        assertEquals(seq.x, comp.x, 1e-9); assertEquals(seq.y, comp.y, 1e-9)
    }

    @Test fun headingConversion() {
        assertEquals(PI / 2, Angles.headingDegToEnuRad(0.0), 1e-12) // north = +y
        assertEquals(0.0, Angles.headingDegToEnuRad(90.0), 1e-12) // east = +x
        assertEquals(270.0, Angles.enuRadToHeadingDeg(PI), 1e-9)
    }
}
