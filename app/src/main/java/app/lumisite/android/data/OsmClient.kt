package app.lumisite.android.data

import app.lumisite.android.BuildConfig
import app.lumisite.core.geo.LatLon
import app.lumisite.core.model.BBox
import app.lumisite.core.model.OsmFeature
import app.lumisite.core.model.OsmSnapshot
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
 * Public OSM services, used only on explicit user action (one request per tap) to respect their usage
 * policies: https://operations.osmfoundation.org/policies/nominatim/ and Overpass API fair use.
 * No API key required.
 */
object OsmClient {
    const val NOMINATIM = "https://nominatim.openstreetmap.org/search"
    const val OVERPASS = "https://overpass-api.de/api/interpreter"
    private val userAgent = "LumiSite/${BuildConfig.VERSION_NAME} (field lighting survey prototype; Android)"
    private val json = Json { ignoreUnknownKeys = true }
    private const val RAW_MAX = 500_000

    data class Place(val name: String, val lat: Double, val lon: Double, val osmRef: String?)

    fun search(query: String): List<Place> {
        val url = "$NOMINATIM?format=jsonv2&limit=8&q=" + URLEncoder.encode(query, "UTF-8")
        val arr = json.parseToJsonElement(get(url)).jsonArray
        return arr.map { it.jsonObject }.map { o ->
            Place(
                o["display_name"]?.jsonPrimitive?.contentOrNull ?: "?",
                o["lat"]!!.jsonPrimitive.content.toDouble(), o["lon"]!!.jsonPrimitive.content.toDouble(),
                o["osm_type"]?.jsonPrimitive?.contentOrNull?.let { t -> "$t/${o["osm_id"]?.jsonPrimitive?.contentOrNull}" },
            )
        }
    }

    fun bboxAround(c: LatLon, radiusM: Double): BBox {
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(c.lat)))
        return BBox(c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon)
    }

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
  way["highway"~"footway|pedestrian|service|path"]($bb);
);
out geom tags;"""
    }

    fun fetchFeatures(center: LatLon, radiusM: Double, nowUtcMs: Long): OsmSnapshot {
        val bbox = bboxAround(center, radiusM)
        val q = overpassQuery(bbox)
        val body = post(OVERPASS, "data=" + URLEncoder.encode(q, "UTF-8"))
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["elements"]!!.jsonArray.map { it.jsonObject }.mapNotNull(::toFeature)
        return OsmSnapshot(
            source = "Overpass API (OpenStreetMap)", endpoint = OVERPASS, query = q, retrievedUtcMs = nowUtcMs,
            bbox = bbox, features = features, rawResponse = body.take(RAW_MAX),
        )
    }

    fun toFeature(o: JsonObject): OsmFeature? {
        val type = o["type"]?.jsonPrimitive?.content ?: return null
        val id = o["id"]?.jsonPrimitive?.long ?: return null
        val tags = o["tags"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        val geom = when (type) {
            "node" -> listOf(LatLon(o["lat"]!!.jsonPrimitive.double, o["lon"]!!.jsonPrimitive.double))
            "way" -> o["geometry"]?.jsonArray?.map { g -> g.jsonObject.let { LatLon(it["lat"]!!.jsonPrimitive.double, it["lon"]!!.jsonPrimitive.double) } } ?: emptyList()
            // relation: keep outer member geometries concatenated is lossy; keep first outer ring only, flagged
            "relation" -> o["members"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it["role"]?.jsonPrimitive?.contentOrNull == "outer" }
                ?.get("geometry")?.jsonArray?.map { g -> g.jsonObject.let { LatLon(it["lat"]!!.jsonPrimitive.double, it["lon"]!!.jsonPrimitive.double) } }
                ?: emptyList()
            else -> emptyList()
        }
        val category = when {
            tags.containsKey("building") || tags.containsKey("building:part") -> "building"
            tags["highway"] == "street_lamp" || tags["man_made"] == "street_lamp" -> "street_lamp"
            tags.containsKey("entrance") -> "entrance"
            tags.containsKey("indoor") -> "indoor"
            tags.containsKey("highway") -> "path"
            else -> "other"
        }
        val t2 = if (type == "relation") tags + ("lumisite:geometry_note" to "first outer ring only") else tags
        return OsmFeature(type, id, t2, geom, category)
    }

    private fun get(url: String): String = open(url).run {
        requestMethod = "GET"; read()
    }

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
        if (code !in 200..299) throw java.io.IOException("HTTP $code from ${url.host}: " + (errorStream?.bufferedReader()?.readText()?.take(200) ?: ""))
        return inputStream.bufferedReader().use { it.readText() }
    }
}
