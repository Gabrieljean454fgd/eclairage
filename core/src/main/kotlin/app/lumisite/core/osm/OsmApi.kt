package app.lumisite.core.osm

import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Site rectangle helpers (decimal degrees, WGS84). */
object Bboxes {
    fun fromCorners(a: LatLon, b: LatLon) = BBox(min(a.lat, b.lat), min(a.lon, b.lon), max(a.lat, b.lat), max(a.lon, b.lon))
    fun areaDeg2(b: BBox) = abs(b.north - b.south) * abs(b.east - b.west)
    fun center(b: BBox) = LatLon((b.south + b.north) / 2, (b.west + b.east) / 2)

    /** Same centre, each side multiplied by [factor]. */
    fun scaled(b: BBox, factor: Double): BBox {
        val c = center(b); val h = (b.north - b.south) / 2 * factor; val w = (b.east - b.west) / 2 * factor
        return BBox(c.lat - h, c.lon - w, c.lat + h, c.lon + w)
    }

    fun quadrants(b: BBox): List<BBox> {
        val c = center(b)
        return listOf(
            BBox(b.south, b.west, c.lat, c.lon), BBox(b.south, c.lon, c.lat, b.east),
            BBox(c.lat, b.west, b.north, c.lon), BBox(c.lat, c.lon, b.north, b.east),
        )
    }

    /** API order: ouest,sud,est,nord. */
    fun apiParam(b: BBox) = "%.7f,%.7f,%.7f,%.7f".format(java.util.Locale.ROOT, b.west, b.south, b.east, b.north)
}

/** Guards of docs/PROPOSITIONS.md fiche L: area before the call, density (HTTP 400 too many nodes) after. */
object OsmGuard {
    /** Refused before any call (the server accepts 0.25, but dense areas already fail at 0.002). */
    const val MAX_AREA_DEG2 = 0.01
    const val TOO_MANY_NODES = "You requested too many nodes (limit is 50000). Either request a smaller area, or use planet.osm"
    const val MAX_CALLS = 16
    const val MAX_DEPTH = 2

    fun areaOk(b: BBox) = Bboxes.areaDeg2(b) <= MAX_AREA_DEG2

    enum class Failure { TOO_MANY_NODES, BBOX_TOO_LARGE, RATE_LIMITED, OTHER }

    fun classify(code: Int, body: String): Failure = when {
        code == 400 && body.contains("too many nodes", ignoreCase = true) -> Failure.TOO_MANY_NODES
        code == 400 && body.contains("maximum bbox size", ignoreCase = true) -> Failure.BBOX_TOO_LARGE
        code == 429 || code == 509 -> Failure.RATE_LIMITED
        else -> Failure.OTHER
    }
}

class OsmHttpException(val code: Int, val body: String) : java.io.IOException("HTTP $code : ${body.take(200)}") {
    val failure = OsmGuard.classify(code, body)
}

class CallCapReached(val calls: Int) : java.io.IOException("Plafond de $calls appels atteint")

data class OsmNode(val id: Long, val pos: LatLon, val tags: Map<String, String>)
data class OsmWay(val id: Long, val nodes: List<Long>, val tags: Map<String, String>)
data class OsmMember(val type: String, val ref: Long, val role: String)
data class OsmRelation(val id: Long, val members: List<OsmMember>, val tags: Map<String, String>)

/** Elements of /api/0.6/map.json (ways only hold node ids). */
data class OsmElements(val nodes: Map<Long, OsmNode>, val ways: Map<Long, OsmWay>, val relations: Map<Long, OsmRelation>) {
    /** Union de-duplicated by type + id (sub-bbox results overlap on their borders). */
    operator fun plus(o: OsmElements) = OsmElements(nodes + o.nodes, ways + o.ways, relations + o.relations)
    val size get() = nodes.size + ways.size + relations.size

    companion object { val EMPTY = OsmElements(emptyMap(), emptyMap(), emptyMap()) }
}

object OsmMapParser {
    private val json = Json { ignoreUnknownKeys = true }
    val EXCLUDED_RELATIONS = setOf("route", "superroute", "route_master", "boundary", "public_transport")

    private fun tags(o: JsonObject) = o["tags"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()

    fun parse(text: String): OsmElements {
        val nodes = HashMap<Long, OsmNode>(); val ways = HashMap<Long, OsmWay>(); val rels = HashMap<Long, OsmRelation>()
        for (e in json.parseToJsonElement(text).jsonObject["elements"]?.jsonArray.orEmpty()) {
            val o = e.jsonObject
            val id = o["id"]?.jsonPrimitive?.long ?: continue
            when (o["type"]?.jsonPrimitive?.contentOrNull) {
                "node" -> {
                    val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
                    nodes[id] = OsmNode(id, LatLon(lat, o["lon"]!!.jsonPrimitive.double), tags(o))
                }
                "way" -> ways[id] = OsmWay(id, o["nodes"]?.jsonArray?.map { it.jsonPrimitive.long }.orEmpty(), tags(o))
                "relation" -> rels[id] = OsmRelation(id, o["members"]?.jsonArray?.map { m ->
                    m.jsonObject.let { OsmMember(it["type"]!!.jsonPrimitive.content, it["ref"]!!.jsonPrimitive.long, it["role"]?.jsonPrimitive?.contentOrNull ?: "") }
                }.orEmpty(), tags(o))
            }
        }
        return OsmElements(nodes, ways, rels)
    }

    /** Local filter of fiche L: building, building:part, indoor, highway=street_lamp, entrance, man_made, power. */
    fun isUseful(t: Map<String, String>) = "building" in t || "building:part" in t || "indoor" in t || t["highway"] == "street_lamp" ||
        "entrance" in t || "man_made" in t || "power" in t

    fun category(t: Map<String, String>) = when {
        "building" in t || "building:part" in t -> "building"
        t["highway"] == "street_lamp" || t["man_made"] == "street_lamp" -> "street_lamp"
        "entrance" in t -> "entrance"
        "indoor" in t -> "indoor"
        "power" in t -> "power"
        "man_made" in t -> "man_made"
        else -> "other"
    }

    /** Joins way pieces end to end into closed rings (multipolygon members are often split). Open leftovers are dropped. */
    fun assembleRings(pieces: List<List<Long>>): List<List<Long>> {
        val todo = pieces.filter { it.size >= 2 }.toMutableList()
        val rings = ArrayList<List<Long>>()
        while (todo.isNotEmpty()) {
            var cur = todo.removeAt(0).toMutableList()
            var progress = true
            while (cur.first() != cur.last() && progress) {
                progress = false
                val i = todo.indexOfFirst { it.first() == cur.last() || it.last() == cur.last() || it.first() == cur.first() || it.last() == cur.first() }
                if (i >= 0) {
                    val p = todo.removeAt(i)
                    cur = when {
                        p.first() == cur.last() -> (cur + p.drop(1)).toMutableList()
                        p.last() == cur.last() -> (cur + p.reversed().drop(1)).toMutableList()
                        p.last() == cur.first() -> (p + cur.drop(1)).toMutableList()
                        else -> (p.reversed() + cur.drop(1)).toMutableList()
                    }
                    progress = true
                }
            }
            if (cur.size >= 4 && cur.first() == cur.last()) rings += cur
        }
        return rings
    }

    private fun ringArea(r: List<LatLon>) = abs(r.zipWithNext { a, b -> a.lon * b.lat - b.lon * a.lat }.sum()) / 2

    /** Geometries from the filtered elements; multipolygons keep their inner rings (courtyards). */
    fun features(el: OsmElements): List<OsmFeature> {
        fun coords(ids: List<Long>) = ids.mapNotNull { el.nodes[it]?.pos }.takeIf { it.size == ids.size }
        val out = ArrayList<OsmFeature>()
        el.nodes.values.filter { it.tags.isNotEmpty() && isUseful(it.tags) }
            .forEach { out += OsmFeature("node", it.id, it.tags, listOf(it.pos), category(it.tags)) }
        el.ways.values.filter { isUseful(it.tags) }.forEach { w ->
            coords(w.nodes)?.let { out += OsmFeature("way", w.id, w.tags, it, category(w.tags)) }
        }
        el.relations.values.filter { it.tags["type"] !in EXCLUDED_RELATIONS && it.tags["type"] in setOf("multipolygon", "building") && isUseful(it.tags) }
            .forEach { r ->
                fun rings(role: (String) -> Boolean) = assembleRings(r.members.filter { it.type == "way" && role(it.role) }.mapNotNull { el.ways[it.ref]?.nodes })
                    .mapNotNull(::coords)
                val outers = rings { it == "outer" || it.isEmpty() }.sortedByDescending(::ringArea)
                if (outers.isEmpty()) return@forEach
                out += OsmFeature("relation", r.id, r.tags, outers.first(), category(r.tags), holes = rings { it == "inner" }, outers = outers.drop(1))
            }
        return out
    }
}

/**
 * Fetch with the density guard: on HTTP 400 too many nodes, split into quadrants (bounded recursion and call cap),
 * then merge de-duplicated by type + id. [fetch] performs exactly one HTTP request.
 */
class SplitFetcher(private val fetch: (BBox) -> String, val maxCalls: Int = OsmGuard.MAX_CALLS, val maxDepth: Int = OsmGuard.MAX_DEPTH) {
    var calls = 0; private set
    val responses = ArrayList<Pair<BBox, String>>()

    fun fetchSplit(b: BBox): OsmElements {
        val parts = Bboxes.quadrants(b)
        return parts.fold(OsmElements.EMPTY) { acc, q -> acc + one(q, 1) }
    }

    private fun one(b: BBox, depth: Int): OsmElements {
        if (calls >= maxCalls) throw CallCapReached(calls)
        calls++
        return try {
            val body = fetch(b); responses += b to body; OsmMapParser.parse(body)
        } catch (e: OsmHttpException) {
            if (e.failure != OsmGuard.Failure.TOO_MANY_NODES || depth >= maxDepth) throw e
            Bboxes.quadrants(b).fold(OsmElements.EMPTY) { acc, q -> acc + one(q, depth + 1) }
        }
    }
}

/** Geocoding: IGN Géoplateforme first (France), Nominatim as fallback. */
object Geocoding {
    data class Place(val name: String, val lat: Double, val lon: Double, val source: String, val ref: String?)
    private val json = Json { ignoreUnknownKeys = true }

    fun parseIgn(text: String): List<Place> = json.parseToJsonElement(text).jsonObject["features"]?.jsonArray.orEmpty().mapNotNull { f ->
        val o = f.jsonObject
        val c = o["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
        val p = o["properties"]?.jsonObject
        Place(p?.get("label")?.jsonPrimitive?.contentOrNull ?: "?", c[1].jsonPrimitive.double, c[0].jsonPrimitive.double, "IGN", p?.get("id")?.jsonPrimitive?.contentOrNull)
    }

    fun parseNominatim(text: String): List<Place> = json.parseToJsonElement(text).jsonArray.map { it.jsonObject }.map { o ->
        Place(
            o["display_name"]?.jsonPrimitive?.contentOrNull ?: "?", o["lat"]!!.jsonPrimitive.content.toDouble(), o["lon"]!!.jsonPrimitive.content.toDouble(),
            "NOMINATIM", o["osm_type"]?.jsonPrimitive?.contentOrNull?.let { t -> "$t/${o["osm_id"]?.jsonPrimitive?.contentOrNull}" },
        )
    }

    /** IGN first; Nominatim only if IGN fails or returns nothing (outside France). */
    fun search(ign: () -> String, nominatim: () -> String): List<Place> =
        runCatching { parseIgn(ign()) }.getOrDefault(emptyList()).ifEmpty { parseNominatim(nominatim()) }
}
