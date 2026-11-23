package app.lumisite.android.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import app.lumisite.core.osm.AddressHistory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/** files/recent_addresses.json: validated addresses of Nouveau site (200 at most), usable without network. */
object AddressStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "recent_addresses.json")

    fun load(ctx: Context): List<AddressHistory.Entry> = runCatching {
        (Json.parseToJsonElement(file(ctx).readText()) as JsonArray).mapNotNull { el ->
            val o = el.jsonObject
            val t = o["text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            AddressHistory.Entry(t, o["lat"]?.jsonPrimitive?.doubleOrNull, o["lon"]?.jsonPrimitive?.doubleOrNull, o["used_ms"]?.jsonPrimitive?.longOrNull ?: 0L)
        }
    }.getOrDefault(emptyList())

    fun save(ctx: Context, history: List<AddressHistory.Entry>) {
        val text = buildJsonArray {
            history.forEach { e -> add(buildJsonObject { put("text", e.text); e.lat?.let { put("lat", it) }; e.lon?.let { put("lon", it) }; put("used_ms", e.usedMs) }) }
        }.toString()
        val f = file(ctx); val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text); if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun record(ctx: Context, text: String, lat: Double?, lon: Double?): List<AddressHistory.Entry> =
        AddressHistory.record(load(ctx), text, lat, lon, System.currentTimeMillis()).also { save(ctx, it) }

    /** Last known position (GPS or network), no request: null without permission or fix. */
    @SuppressLint("MissingPermission")
    fun lastPosition(ctx: Context): Pair<Double, Double>? {
        val granted = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
        }.getOrNull()
    }
}
