package app.lumisite.core

import app.lumisite.core.export.ClockRef
import app.lumisite.core.export.GeoJsonFeatures
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.model.DxfHint
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.SyncEvent
import app.lumisite.core.model.SyncMethod
import app.lumisite.core.model.SyncRole
import app.lumisite.core.model.TrackingState
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** v176g: time.clock_offset_ms / clock_sigma_ms / sync_ref come from the synchro top, null without top (never 0). */
class ClockRefTest {
    private val ltp = LocalTangentPlane(Fixtures.site.origin)
    private fun srv(id: String, t: Long) = SurveyEvent(
        id = id, t = Fixtures.stamp(t), kind = "LS_ECLAIRAGE_DOWNLIGHT", tracking = TrackingState.TRACKING, positionSource = "MANUAL_MAP",
        manualSite = Enu(1.0, 2.0, 0.0), payloadSchema = "lumisite-equipment/1",
        payload = buildJsonObject { put("code", "LS_ECLAIRAGE_DOWNLIGHT") }, dxf = DxfHint("RES - Elec", "spot"),
    )
    private fun top(id: String, t: Long, offsetNs: Long?, sigmaNs: Long?) = SyncEvent(id, Fixtures.stamp(t), "TOP-$id", SyncMethod.AUDIO_CHIRP,
        SyncRole.EMITTER, Fixtures.stamp(t).monotonicNs, clockOffsetNs = offsetNs, clockSigmaNs = sigmaNs)
    private fun time(events: List<app.lumisite.core.model.SessionEvent>) =
        GeoJsonFeatures.lines(events, ltp, "dev-1", "sess-1").single()["properties"]!!.jsonObject["time"]!!.jsonObject

    @Test fun noTopLeavesNullNeverZero() {
        val t = time(listOf(srv("a", 5_000)))
        assertEquals(JsonNull, t["clock_offset_ms"]); assertEquals(JsonNull, t["clock_sigma_ms"]); assertEquals(JsonNull, t["sync_ref"])
        // A manual sync tap (no measured offset) is not a top either.
        assertNull(ClockRef.of(Fixtures.stamp(5_000), ClockRef.tops(listOf(top("m", 1_000, null, null)))))
    }

    @Test fun latestTopBeforeTheObject() {
        val t = time(listOf(top("r1", 1_000, 0, 30_000_000), top("r2", 3_000, 0, 33_900_000), srv("a", 5_000), top("r3", 9_000, 0, 10_000_000)))
        assertEquals(0.0, t["clock_offset_ms"]!!.jsonPrimitive.double)
        assertEquals(33.9, t["clock_sigma_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("TOP-r2", t["sync_ref"]!!.jsonPrimitive.content)
    }

    @Test fun firstTopAfterWhenNoneBefore() {
        val r = ClockRef.of(Fixtures.stamp(1_000), ClockRef.tops(listOf(top("r9", 4_000, 12_500_000, 2_000_000))))!!
        assertEquals(12.5, r.offsetMs, 1e-9); assertEquals(2.0, r.sigmaMs!!, 1e-9); assertEquals("TOP-r9", r.syncRef)
    }

    @Test fun otherTimeBaseIsIgnored() {
        val other = top("x", 1_000, 0, 1).let { it.copy(t = it.t.copy(timeBaseId = "tb-autre")) }
        assertNull(ClockRef.of(Fixtures.stamp(5_000), ClockRef.tops(listOf(other))))
    }
}
