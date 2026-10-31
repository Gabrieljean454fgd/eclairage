package app.lumisite.core.time

import kotlinx.serialization.Serializable

/**
 * A timestamp as captured on one device. All fields are raw values; nothing is converted on capture.
 *
 * - [utcEpochMs]: civil wall-clock time (UTC, ms since Unix epoch). May jump (NTP, user change).
 * - [monotonicNs]: monotonic clock of the [timeBaseId] (Android: SystemClock.elapsedRealtimeNanos()).
 *   Use it, never UTC, to measure intervals.
 * - [sensorNs]: the native timestamp provided by the sensor/API (SensorEvent.timestamp, Frame.timestamp,
 *   Location.elapsedRealtimeNanos...) when it differs from the capture time. Kept untouched.
 */
@Serializable
data class Stamp(
    val utcEpochMs: Long,
    val monotonicNs: Long,
    val timeBaseId: String,
    val sensorNs: Long? = null,
)

/**
 * Describes one monotonic clock of one device for one session. A new time base is created if the device
 * reboots (the monotonic clock resets).
 */
@Serializable
data class TimeBase(
    val id: String,
    val deviceId: String,
    /** e.g. "ANDROID_ELAPSED_REALTIME_NANOS" (includes deep sleep, starts at boot). */
    val clock: String,
    /** Reference pair captured together, used for documented monotonic->UTC conversion. */
    val referenceUtcEpochMs: Long,
    val referenceMonotonicNs: Long,
    val notes: String? = null,
    /**
     * Stage 1.7: identity of the phone boot this monotonic clock belongs to (Android: /proc/sys/kernel/random/boot_id,
     * or Settings.Global.BOOT_COUNT). Two time bases with different boot ids can never be compared on monotonicNs.
     */
    val bootId: String? = null,
    val bootCount: Int? = null,
) {
    /**
     * Derived UTC for a monotonic value of this base, assuming no drift between the two clocks since the
     * reference pair. Result is a computed value, never a raw one.
     */
    fun monotonicToUtcMs(monotonicNs: Long): Double =
        referenceUtcEpochMs + (monotonicNs - referenceMonotonicNs) / 1_000_000.0

    companion object {
        const val ANDROID_ELAPSED_REALTIME = "ANDROID_ELAPSED_REALTIME_NANOS"
    }
}

/** Source of "now" so that capture code is testable. */
interface Clock {
    fun utcEpochMs(): Long
    fun monotonicNs(): Long
}

class StampFactory(private val clock: Clock, val timeBase: TimeBase) {
    fun now(sensorNs: Long? = null) = Stamp(clock.utcEpochMs(), clock.monotonicNs(), timeBase.id, sensorNs)
}

object TimeMath {
    fun nsToSeconds(ns: Long): Double = ns / 1e9

    /** Interval between two stamps of the same time base, from the monotonic clock only. */
    fun intervalNs(a: Stamp, b: Stamp): Long {
        require(a.timeBaseId == b.timeBaseId) { "Stamps belong to different time bases: ${a.timeBaseId} vs ${b.timeBaseId}" }
        return b.monotonicNs - a.monotonicNs
    }
}
