package app.lumisite.android.data

import app.lumisite.android.BuildConfig
import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature
import app.lumisite.core.model.OsmSnapshot
import app.lumisite.core.osm.Bboxes
import app.lumisite.core.osm.Geocoding
import app.lumisite.core.osm.OsmElements
import app.lumisite.core.osm.OsmHttpException
import app.lumisite.core.osm.OsmMapParser
import app.lumisite.core.osm.SplitFetcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos

/**
 * Public OSM/IGN services, used only on explicit user action (one request per gesture, no retry loop):
 * https://operations.osmfoundation.org/policies/api/ and /policies/nominatim/. No API key required.
 */
object OsmClient {
    /** Configurable (site editor, "URL des services"). */
    @Volatile var osmApiBase = "https://api.openstreetmap.org/api/0.6"
    @Volatile var ignGeocodeUrl = "https://data.geopf.fr/geocodage/search"
    @Volatile var nominatimUrl = "https://nominatim.openstreetmap.org/search"
    const val OVERPASS = "https://overpass-api.de/api/interpreter"
    private val userAgent = "LumiSite/${BuildConfig.VERSION_NAME} (field lighting survey; Android; github.com/Gabrieljean454fgd/eclairage)"
    private val json = Json { ignoreUnknownKeys = true }
    /** Maximum size kept per raw response (osm_source.json, rawResponse). */
    const val RAW_MAX = 500_000
    private var lastNominatimMs = 0L

    /** IGN first, Nominatim if IGN fails or finds nothing (outside France). Nominatim: at most 1 request per second. */
    fun search(query: String): List<Geocoding.Place> {
        val q = URLEncoder.encode(query, "UTF-8")
        return Geocoding.search(
            ign = { get("$ignGeocodeUrl?limit=8&q=$q") },
            nominatim = {
                synchronized(this) {
                    val wait = 1_000 - (System.currentTimeMillis() - lastNominatimMs)
                    if (wait > 0) Thread.sleep(wait)
                    lastNominatimMs = System.currentTimeMillis()
                }
                get("$nominatimUrl?format=jsonv2&limit=8&q=$q")
            },
        )
    }

    fun bboxAround(c: LatLon, radiusM: Double): BBox {
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(c.lat)))
        return BBox(c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon)
    }

    fun mapUrl(b: BBox): String {
        require(isHttps(osmApiBase)) { "L'URL de l'API OSM doit commencer par https://" }
        return "$osmApiBase/map.json?bbox=${Bboxes.apiParam(b)}"
    }

    /** Result of a fetch: the snapshot plus the raw dated responses for osm_source.json. */
    data class Fetched(val snapshot: OsmSnapshot, val raw: List<Pair<BBox, String>>, val calls: Int, val missing: List<BBox> = emptyList())

    fun isHttps(url: String) = url.trim().startsWith("https://")

    /** One call to /map.json. Throws [OsmHttpException] (too many nodes, bbox size, 429/509…) without retrying. */
    fun fetchMap(b: BBox, nowUtcMs: Long): Fetched {
        val body = get(mapUrl(b))
        return snapshot(b, OsmMapParser.parse(body), listOf(b to body), 1, nowUtcMs, "bbox=${Bboxes.apiParam(b)}")
    }

    /** "Découper et fusionner": quadrants, bounded recursion, capped number of calls, merge by type+id. */
    fun fetchMapSplit(b: BBox, nowUtcMs: Long, onCall: (Int) -> Unit = {}): Fetched {
        val f = SplitFetcher({ q -> get(mapUrl(q)) }, onCall = onCall)
        val el = f.fetchSplit(b)
        val partial = if (f.missing.isEmpty()) "" else ", partiel : ${f.missing.size} sous-zone(s) trop dense(s) non récupérée(s)"
        return snapshot(b, el, f.responses, f.calls, nowUtcMs, "bbox=${Bboxes.apiParam(b)} découpé en ${f.responses.size} sous-zones$partial")
            .copy(missing = f.missing.toList())
    }

    private fun snapshot(b: BBox, el: OsmElements, raw: List<Pair<BBox, String>>, calls: Int, now: Long, query: String) = Fetched(
        OsmSnapshot(source = "API OSM v0.6 (OpenStreetMap)", endpoint = "$osmApiBase/map.json", query = query, retrievedUtcMs = now,
            bbox = b, features = OsmMapParser.features(el), rawResponse = null),
        raw, calls,
    )

    fun overpassQuery(b: BBox): String {
        val bb = "${b.south},${b.west},${b.north},${b.east}"
        return """[out:json][timeout:25];
(
  way["building"]($bb);
  relation["building"]($bb);
  way["building:part"]($bb);
  node["highway"="street_lamp"]($bb);
  node["man_made"="street_lamp"]($bb);
  node["entrance"]($bb);
  way["indoor"]($bb);
);
out geom tags;"""
    }

    /** Fallback when the OSM API answers 429/509 (Overpass, fiche C). */
    fun fetchOverpass(bbox: BBox, nowUtcMs: Long): Fetched {
        val q = overpassQuery(bbox)
        val body = post(OVERPASS, "data=" + URLEncoder.encode(q, "UTF-8"))
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["elements"]!!.jsonArray.map { it.jsonObject }.mapNotNull(::toFeature)
        return Fetched(OsmSnapshot(
            source = "Overpass API (OpenStreetMap)", endpoint = OVERPASS, query = q, retrievedUtcMs = nowUtcMs,
            bbox = bbox, features = features, rawResponse = body.take(RAW_MAX),
        ), listOf(bbox to body), 1)
    }

    /** Overpass `out geom` element → feature (relations: first outer ring only, flagged). */
    fun toFeature(o: JsonObject): OsmFeature? {
        val type = o["type"]?.jsonPrimitive?.content ?: return null
        val id = o["id"]?.jsonPrimitive?.long ?: return null
        val tags = o["tags"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        val geom = when (type) {
            "node" -> listOf(LatLon(o["lat"]!!.jsonPrimitive.double, o["lon"]!!.jsonPrimitive.double))
            "way" -> o["geometry"]?.jsonArray?.map { g -> g.jsonObject.let { LatLon(it["lat"]!!.jsonPrimitive.double, it["lon"]!!.jsonPrimitive.double) } } ?: emptyList()
            "relation" -> o["members"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it["role"]?.jsonPrimitive?.contentOrNull == "outer" }
                ?.get("geometry")?.jsonArray?.map { g -> g.jsonObject.let { LatLon(it["lat"]!!.jsonPrimitive.double, it["lon"]!!.jsonPrimitive.double) } }
                ?: emptyList()
            else -> emptyList()
        }
        val t2 = if (type == "relation") tags + ("lumisite:geometry_note" to "first outer ring only") else tags
        return OsmFeature(type, id, t2, geom, OsmMapParser.category(tags))
    }

    private fun get(url: String): String = open(url).run { requestMethod = "GET"; read() }

    private fun post(url: String, form: String): String = open(url).run {
        requestMethod = "POST"; doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        outputStream.use { it.write(form.toByteArray()) }
        read()
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 40_000
        setRequestProperty("User-Agent", userAgent)
        setRequestProperty("Accept", "application/json")
    }

    private fun HttpURLConnection.read(): String {
        val code = responseCode
        if (code !in 200..299) throw OsmHttpException(code, errorStream?.bufferedReader()?.readText()?.take(500) ?: "")
        return inputStream.bufferedReader().use { it.readText() }
    }
}
