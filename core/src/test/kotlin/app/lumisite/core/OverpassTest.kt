package app.lumisite.core

import app.lumisite.core.model.BBox
import app.lumisite.core.osm.CallCapReached
import app.lumisite.core.osm.FormPoster
import app.lumisite.core.osm.MirrorFetcher
import app.lumisite.core.osm.OsmGuard
import app.lumisite.core.osm.OsmHttpException
import app.lumisite.core.osm.Overpass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverpassTest {
    @Test fun queryUsesRectangleTimeoutAndCap() {
        val q = Overpass.query(BBox(48.85, 2.33, 48.86, 2.34))
        assertTrue(q.startsWith("[out:json][timeout:25];"))
        assertTrue("(48.8500000,2.3300000,48.8600000,2.3400000)" in q)
        assertTrue(q.trimEnd().endsWith("out geom tags 500;"))
        assertFalse("around" in q)
    }

    @Test fun fallbackOnlyOnNetworkRateLimitAndServerErrors() {
        assertTrue(Overpass.shouldFallback(java.net.UnknownHostException("x")))
        assertTrue(Overpass.shouldFallback(OsmHttpException(429, "")))
        assertTrue(Overpass.shouldFallback(OsmHttpException(509, "")))
        assertTrue(Overpass.shouldFallback(OsmHttpException(503, "")))
        assertFalse(Overpass.shouldFallback(OsmHttpException(400, OsmGuard.TOO_MANY_NODES)))
        assertFalse(Overpass.shouldFallback(CallCapReached(16)))
    }

    @Test fun mirrorsInOrderWithBackoff() {
        val sleeps = ArrayList<Long>()
        val f = MirrorFetcher({ url, _ -> if (url.contains("private.coffee")) "{}" else throw java.io.IOException("502") }, sleep = { sleeps += it })
        val (m, body) = f.fetch("data=x")
        assertEquals(Overpass.MIRRORS[2], m); assertEquals("{}", body)
        assertEquals(listOf(Overpass.MIRRORS[0], Overpass.MIRRORS[0], Overpass.MIRRORS[1], Overpass.MIRRORS[1], Overpass.MIRRORS[2]), f.tried)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 4_000L), sleeps)
    }

    @Test fun allMirrorsFailing() {
        val f = MirrorFetcher(FormPoster { _, _ -> throw java.io.IOException("timeout") }, sleep = {})
        assertFailsWith<java.io.IOException> { f.fetch("data=x") }
        assertEquals(6, f.tried.size)
    }
}
