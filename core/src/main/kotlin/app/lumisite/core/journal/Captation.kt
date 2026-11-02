package app.lumisite.core.journal

import app.lumisite.core.model.GpsFix
import app.lumisite.core.model.GpsStatusSample
import app.lumisite.core.model.ImuSample
import app.lumisite.core.model.PoseSample
import app.lumisite.core.time.Stamp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.RandomAccessFile

/**
 * D9 capture frames (docs/FORMAT_ECHANGE_CAPTATION.md): one JSONL line per sample with t_mono_ns, clock_offset_ns,
 * seq, device_id, time_base, type and v. Written by [SessionJournal] alongside the historical raw streams.
 */
object Captation {
    fun frame(seq: Long, deviceId: String, t: Stamp, type: String, v: JsonObject, clockOffsetNs: Long? = null): String = buildJsonObject {
        put("t_mono_ns", t.monotonicNs)
        put("clock_offset_ns", clockOffsetNs?.let { JsonPrimitive(it) } ?: JsonNull)
        put("seq", seq)
        put("device_id", deviceId)
        put("time_base", t.timeBaseId)
        put("type", type)
        put("v", v)
    }.toString()

    /** Android Sensor.TYPE_* → frame type. */
    fun imuType(sensorType: Int) = when (sensorType) {
        1 -> "imu.accel"; 4 -> "imu.gyro"; 2 -> "imu.mag"; 11, 15 -> "imu.rotation"; else -> "imu.other"
    }

    fun imu(s: ImuSample): JsonObject = buildJsonObject {
        val x = s.values
        when (val type = imuType(s.sensorType)) {
            "imu.rotation" -> {
                put("x", x.getOrNull(0)); put("y", x.getOrNull(1)); put("z", x.getOrNull(2)); put("w", x.getOrNull(3))
                put("source", if (s.sensorType == 11) "ROTATION_VECTOR" else "GAME_ROTATION_VECTOR")
            }
            "imu.other" -> { put("sensor_type", s.sensorType); putJsonArray("values") { x.forEach { add(JsonPrimitive(it)) } } }
            else -> {
                put("x", x.getOrNull(0)); put("y", x.getOrNull(1)); put("z", x.getOrNull(2))
                put("unit", when (type) { "imu.accel" -> "m/s2"; "imu.gyro" -> "rad/s"; else -> "uT" })
            }
        }
        put("sensor_name", s.sensorName)
        put("accuracy", s.accuracy)
        s.t.sensorNs?.let { put("sensor_ns", it) }
    }

    fun pose(p: PoseSample): JsonObject = buildJsonObject {
        putJsonArray("p") { p.p.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("q") { p.q.forEach { add(JsonPrimitive(it)) } }
        put("tracking", p.tracking.name)
        put("reason", p.failureReason)
        put("source", p.source)
    }

    fun gnss(f: GpsFix): JsonObject = buildJsonObject {
        put("lat", f.lat); put("lon", f.lon); put("alt", f.altitudeM); put("acc_m", f.horizontalAccuracyM)
        put("provider", f.provider); put("fix_utc_ms", f.fixUtcMs); put("sats_used", f.satellitesUsed); put("sats_visible", f.satellitesVisible)
        put("speed_mps", f.speedMps); put("bearing_deg", f.bearingDeg)
    }

    fun gnssStatus(s: GpsStatusSample): JsonObject = buildJsonObject {
        put("provider", s.provider); put("status", s.status); put("detail", s.detail)
    }

    /** Last seq already written in [files] (resume after a restart): read from the last line, else counted. */
    fun lastSeq(files: List<File>): Long {
        val f = files.lastOrNull { it.isFile && it.length() > 0 } ?: return 0
        val tail = RandomAccessFile(f, "r").use { r ->
            val n = minOf(r.length(), 16_384L); r.seek(r.length() - n); ByteArray(n.toInt()).also { r.readFully(it) }
        }.toString(Charsets.UTF_8)
        return runCatching { Json.parseToJsonElement(tail.trimEnd('\n').substringAfterLast('\n')).jsonObject["seq"]!!.jsonPrimitive.long }
            .getOrElse { var n = 0L; Ndjson.forEachLine(files) { _, _ -> n++ }; n }
    }
}

/** Numbers the frames without gap (seq strictly increasing) and keeps file order = seq order. */
class CaptationWriter(private val out: SegmentedAppender, private val deviceId: String, startSeq: Long) {
    private var seq = startSeq

    @Synchronized
    fun write(t: Stamp, type: String, v: JsonObject) = out.appendLine(Captation.frame(++seq, deviceId, t, type, v))
}
