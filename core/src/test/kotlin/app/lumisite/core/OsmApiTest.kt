package app.lumisite.core

import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.BBox
import app.lumisite.core.osm.Bboxes
import app.lumisite.core.osm.CallCapReached
import app.lumisite.core.osm.Geocoding
import app.lumisite.core.osm.OsmGuard
import app.lumisite.core.osm.OsmHttpException
import app.lumisite.core.osm.OsmMapParser
import app.lumisite.core.osm.SplitFetcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsmApiTest {
    private fun node(id: Long, lat: Double, lon: Double, tags: String = "") =
        """{"type":"node","id":$id,"lat":$lat,"lon":$lon${if (tags.isNotEmpty()) ",\"tags\":{$tags}" else ""}}"""
    private fun way(id: Long, nodes: List<Long>, tags: String = "") =
        """{"type":"way","id":$id,"nodes":${nodes}${if (tags.isNotEmpty()) ",\"tags\":{$tags}" else ""}}"""
    private fun doc(vararg e: String) = """{"version":"0.6","elements":[${e.joinToString(",")}]}"""

    /** Square building 1-2-3-4 with a courtyard 5-6-7-8, as a multipolygon whose outer ring is split in two ways. */
    private val courtyard = doc(
        node(1, 0.0, 0.0), node(2, 0.0, 1.0), node(3, 1.0, 1.0), node(4, 1.0, 0.0),
        node(5, 0.4, 0.4), node(6, 0.4, 0.6), node(7, 0.6, 0.6), node(8, 0.6, 0.4),
        node(9, 0.5, 2.0, "\"highway\":\"street_lamp\""), node(10, 0.5, 3.0, "\"amenity\":\"bench\""),
        way(100, listOf(1, 2, 3)), way(101, listOf(3, 4, 1)), way(102, listOf(5, 6, 7, 8, 5)),
        way(103, listOf(1, 2), "\"highway\":\"path\""),
        """{"type":"relation","id":200,"members":[{"type":"way","ref":100,"role":"outer"},{"type":"way","ref":101,"role":"outer"},{"type":"way","ref":102,"role":"inner"}],"tags":{"type":"multipolygon","building":"yes"}}""",
        """{"type":"relation","id":300,"members":[{"type":"way","ref":100,"role":""}],"tags":{"type":"route","route":"hiking","building":"yes"}}""",
    )

    @Test fun waysAreJoinedToNodesAndCourtyardIsKept() {
        val f = OsmMapParser.features(OsmMapParser.parse(courtyard)).associateBy { it.ref }
        val b = f.getValue("relation/200")
        assertEquals("building", b.category)
        assertEquals(5, b.geometry.size)
        assertEquals(1, b.holes.size)
        assertEquals(LatLon(0.4, 0.4), b.holes[0].first())
    }

    @Test fun routesAndUselessTagsAreExcluded() {
        val refs = OsmMapParser.features(OsmMapParser.parse(courtyard)).map { it.ref }.toSet()
        assertEquals(setOf("relation/200", "node/9"), refs)
    }

    @Test fun mergeDeduplicatesByTypeAndId() {
        val a = OsmMapParser.parse(doc(node(1, 0.0, 0.0), node(2, 0.0, 1.0), way(10, listOf(1, 2), "\"power\":\"line\"")))
        val b = OsmMapParser.parse(doc(node(2, 0.0, 1.0), node(3, 1.0, 1.0), way(10, listOf(1, 2), "\"power\":\"line\"")))
        val m = a + b
        assertEquals(3, m.nodes.size); assertEquals(1, m.ways.size)
        assertEquals(1, OsmMapParser.features(m).size)
    }

    @Test fun areaGuard() {
        assertTrue(OsmGuard.areaOk(BBox(48.85, 2.33, 48.89, 2.38)))
        assertFalse(OsmGuard.areaOk(BBox(48.8, 2.3, 48.95, 2.45)))
        assertEquals("2.3300000,48.8500000,2.3800000,48.8900000", Bboxes.apiParam(BBox(48.85, 2.33, 48.89, 2.38)))
        assertEquals(BBox(1.0, 2.0, 3.0, 4.0), Bboxes.fromCorners(LatLon(3.0, 2.0), LatLon(1.0, 4.0)))
    }

    @Test fun errorClassification() {
        assertEquals(OsmGuard.Failure.TOO_MANY_NODES, OsmGuard.classify(400, OsmGuard.TOO_MANY_NODES))
        assertEquals(OsmGuard.Failure.BBOX_TOO_LARGE, OsmGuard.classify(400, "The maximum bbox size is 0.25, and your request was too large."))
        assertEquals(OsmGuard.Failure.RATE_LIMITED, OsmGuard.classify(509, ""))
        assertEquals(OsmGuard.Failure.RATE_LIMITED, OsmGuard.classify(429, ""))
        assertEquals(OsmGuard.Failure.OTHER, OsmGuard.classify(500, ""))
    }

    @Test fun splitAndMergeRecursesOnlyWhereTooDense() {
        val dense = BBox(0.0, 0.0, 1.0, 1.0)
        val f = SplitFetcher({ b ->
            // the south-west quadrant is still too dense once
            if (b.north - b.south > 0.3 && b.south == 0.0 && b.west == 0.0) throw OsmHttpException(400, OsmGuard.TOO_MANY_NODES)
            doc(node(1, 0.0, 0.0, "\"power\":\"pole\""), node((b.south * 100 + b.west * 10).toLong() + 2, b.south, b.west, "\"power\":\"pole\""))
        })
        val el = f.fetchSplit(dense)
        assertEquals(4 + 4, f.calls) // 4 quadrants, the dense one split again
        assertEquals(7, f.responses.size)
        assertTrue(el.nodes.containsKey(1))
        assertEquals(el.nodes.size, el.nodes.keys.toSet().size)
    }

    @Test fun callCapIsEnforced() {
        val f = SplitFetcher({ throw OsmHttpException(400, OsmGuard.TOO_MANY_NODES) }, maxCalls = 5, maxDepth = 10)
        assertFailsWith<CallCapReached> { f.fetchSplit(BBox(0.0, 0.0, 1.0, 1.0)) }
        assertEquals(5, f.calls)
    }

    @Test fun depthIsBoundedAndKeepsPartialResults() {
        val seen = ArrayList<Int>()
        val f = SplitFetcher({ b ->
            if (b.south == 0.0 && b.west == 0.0) throw OsmHttpException(400, OsmGuard.TOO_MANY_NODES)
            doc(node((b.south * 100 + b.west * 10).toLong() + 2, b.south, b.west, "\"power\":\"pole\""))
        }, maxCalls = 100, maxDepth = 2, onCall = { seen += it })
        val el = f.fetchSplit(BBox(0.0, 0.0, 1.0, 1.0))
        assertEquals(1 + 4 + 3, f.calls) // SW quadrant split once, its SW sub-quadrant still too dense at max depth
        assertEquals(listOf(BBox(0.0, 0.0, 0.25, 0.25)), f.missing)
        assertEquals(6, el.nodes.size) // 3 quadrants + 3 sub-quadrants kept
        assertEquals((1..8).toList(), seen)
    }

    @Test fun otherFailuresAreNotHidden() {
        val f = SplitFetcher({ throw OsmHttpException(429, "") }, maxCalls = 100, maxDepth = 2)
        assertFailsWith<OsmHttpException> { f.fetchSplit(BBox(0.0, 0.0, 1.0, 1.0)) }
    }

    @Test fun ignFirstThenNominatim() {
        val ign = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[2.336,48.861]},"properties":{"label":"10 Rue de Rivoli 75004 Paris","id":"75104_8158_00010"}}]}"""
        val nom = """[{"display_name":"Elsewhere","lat":"1.0","lon":"2.0","osm_type":"node","osm_id":5}]"""
        var nominatimCalled = false
        val r = Geocoding.search({ ign }, { nominatimCalled = true; nom })
        assertEquals("IGN", r.first().source); assertEquals(48.861, r.first().lat); assertFalse(nominatimCalled)
        val empty = """{"type":"FeatureCollection","features":[]}"""
        assertEquals("NOMINATIM", Geocoding.search({ empty }, { nom }).first().source)
        assertEquals("NOMINATIM", Geocoding.search({ throw java.io.IOException("down") }, { nom }).first().source)
    }
}
