package app.lumisite.core.export

import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SyncEvent
import app.lumisite.core.time.Stamp

/**
 * v176g: `time.clock_offset_ms / clock_sigma_ms / sync_ref` of an exported object, from the synchro tops recorded in the
 * session (SyncEvent with [SyncEvent.clockOffsetNs]). Same time base only (a reboot breaks the monotonic clock); the
 * latest top before the object, otherwise the first one after it. No top → null (the export writes null, never 0).
 */
object ClockRef {
    data class Ref(val offsetMs: Double, val sigmaMs: Double?, val syncRef: String)

    fun tops(events: List<SessionEvent>): List<SyncEvent> =
        events.filterIsInstance<SyncEvent>().filter { it.clockOffsetNs != null }.sortedBy { it.t.monotonicNs }

    fun of(t: Stamp, tops: List<SyncEvent>): Ref? {
        val same = tops.filter { it.t.timeBaseId == t.timeBaseId }
        val e = same.lastOrNull { it.t.monotonicNs <= t.monotonicNs } ?: same.firstOrNull() ?: return null
        return Ref(e.clockOffsetNs!! / 1e6, e.clockSigmaNs?.let { it / 1e6 }, e.syncCode)
    }
}
