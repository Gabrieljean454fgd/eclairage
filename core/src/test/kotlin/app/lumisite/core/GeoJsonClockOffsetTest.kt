package app.lumisite.core

import app.lumisite.core.export.GeoJsonFeatures
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.model.DxfHint
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.TrackingState
import app.lumisite.core.sync.AudioOffset
import app.lumisite.core.sync.HeardChirp
import app.lumisite.core.sync.MeshMsg
import app.lumisite.core.sync.SyncRoundCalc
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * time.clock_offset_ms / clock_sigma_ms / sync_ref : remplis uniquement par le résultat d'un « top de
 * synchro » réussi (SyncRoundCalc → AudioOffset). Sans synchro, round en échec ou offset non calculé :
 * les trois champs restent null — jamais de décalage inventé.
 */
class GeoJsonClockOffsetTest {
    private val ltp = LocalTangentPlane(Fixtures.site.origin)

    private fun srv(id: String) = SurveyEvent(
        id = id, t = Fixtures.stamp(1_000), kind = "spot", tracking = TrackingState.TRACKING, positionSource = "MANUAL_MAP",
        manualSite = Enu(10.0, 20.0, 0.0), payloadSchema = "lumisite-equipment/1",
        payload = buildJsonObject { put("catalog_version", "lumisite-catalog-1.0"); put("block_name", "spot") },
        dxf = DxfHint("RES - Elec", "spot"),
    )

    private fun feature(sync: AudioOffset? = null) =
        GeoJsonFeatures.feature(srv("a"), Enu(10.0, 20.0, 0.0), ltp, "a", 1, "dev-1", "sess-1", sync = sync)

    private fun timeOf(f: JsonObject) = f["properties"]!!.jsonObject["time"]!!.jsonObject

    // Même round synthétique que SyncRoundTest : l'horloge de l'esclave vaut la réelle + 12,3 ms.
    private val prop = 10_000_000L
    private val off = 12_300_000L
    private val masterHeard = listOf(HeardChirp("018A", 7, 1_000_000_000L), HeardChirp("02B7", 3, 1_700_000_000L + prop))
    private fun slaveResult(heard: List<HeardChirp> = listOf(
        HeardChirp("018A", 7, 1_000_000_000L + prop + off), HeardChirp("02B7", 3, 1_700_000_000L + off),
    )) = MeshMsg.SyncResult("r1", "02B7", heard, "tb2")

    @Test fun sansSynchroLesTroisChampsRestentNuls() {
        val t = timeOf(feature())
        assertEquals(JsonNull, t["clock_offset_ms"]); assertEquals(JsonNull, t["clock_sigma_ms"]); assertEquals(JsonNull, t["sync_ref"])
        val l = GeoJsonFeatures.lines(listOf(srv("a")), ltp, "dev-1", "sess-1").single()
        assertEquals(JsonNull, timeOf(l)["clock_offset_ms"])
    }

    @Test fun topReussiDuCalculRemplitLesTroisChamps() {
        val audio = SyncRoundCalc.offset("r1", 7, "018A", masterHeard, "02B7", slaveResult())
        val t = timeOf(feature(audio))
        assertEquals(12.3, t["clock_offset_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(SyncRoundCalc.UNCERTAINTY_NS / 1e6, t["clock_sigma_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("r1", t["sync_ref"]!!.jsonPrimitive.content)
    }

    @Test fun offsetNegatifEstConserve() {
        val t = timeOf(feature(AudioOffset("top-9", true, true, -12_300_000, 1_000_000)))
        assertEquals(-12.3, t["clock_offset_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(1.0, t["clock_sigma_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("top-9", t["sync_ref"]!!.jsonPrimitive.content)
    }

    @Test fun roundEnErreurOuSansOffsetToutResteNul() {
        val cases = listOf(
            AudioOffset("r1", false, false, null, null, "micro occupé"),
            AudioOffset("r1", true, false, null, null),
            AudioOffset("r1", true, true, null, null),
            AudioOffset("r1", true, true, null, 1_000_000, "bizarre"),
        )
        for (a in cases) {
            val t = timeOf(feature(a))
            assertEquals(JsonNull, t["clock_offset_ms"], "$a")
            assertEquals(JsonNull, t["clock_sigma_ms"], "$a")
            assertEquals(JsonNull, t["sync_ref"], "$a")
        }
    }

    @Test fun incertitudeAbsenteLaisseSigmaNul() {
        val t = timeOf(feature(AudioOffset("top-2", true, true, 5_000_000, null)))
        assertEquals(5.0, t["clock_offset_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(JsonNull, t["clock_sigma_ms"])
        assertEquals("top-2", t["sync_ref"]!!.jsonPrimitive.content)
    }

    @Test fun conversionNsVersMsAuMilliemeLePlusProche() {
        // 12,345678 ms → 12,346 ; 0,999999 ms → 1,0. Même règle pour l'offset et le sigma.
        val t = timeOf(feature(AudioOffset("top-x", true, true, 12_345_678, 999_999)))
        assertEquals(12.346, t["clock_offset_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(1.0, t["clock_sigma_ms"]!!.jsonPrimitive.double, 1e-9)
        val n = timeOf(feature(AudioOffset("top-y", true, true, -12_345_678, null)))
        assertEquals(-12.346, n["clock_offset_ms"]!!.jsonPrimitive.double, 1e-9)
    }

    @Test fun linesPropageLeSyncAuxFeatures() {
        val audio = AudioOffset("top-7", true, true, 3_000_000, 500_000)
        val l = GeoJsonFeatures.lines(listOf(srv("a")), ltp, "dev-1", "sess-1", sync = audio).single()
        val t = timeOf(l)
        assertEquals(3.0, t["clock_offset_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals(0.5, t["clock_sigma_ms"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("top-7", t["sync_ref"]!!.jsonPrimitive.content)
    }
}
