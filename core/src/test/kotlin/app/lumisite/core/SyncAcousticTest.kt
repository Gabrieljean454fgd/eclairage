package app.lumisite.core

import app.lumisite.core.journal.LumiJson
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SyncEvent
import app.lumisite.core.model.SyncMethod
import app.lumisite.core.model.SyncRole
import app.lumisite.core.sync.Chirp
import app.lumisite.core.sync.IdFrame
import app.lumisite.core.sync.MultiChirp
import app.lumisite.core.time.Stamp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncAcousticTest {
    private val rate = Chirp.DEFAULT_SAMPLE_RATE

    @Test fun twoEmittersAreDetectedAndIdentified() {
        val rnd = Random(7)
        val x = FloatArray(rate * 3) { (rnd.nextFloat() - 0.5f) * 0.1f }
        val a = IdFrame.idOf("dev-A"); val b = IdFrame.idOf("dev-B")
        listOf(Triple(a, 3, 24_000), Triple(b, 250, 81_700)).forEach { (id, ctr, at) ->
            IdFrame.signal(id, ctr).forEachIndexed { i, v -> x[at + i] += 0.4f * v }
        }
        val heard = MultiChirp.detectAll(x, Chirp.generate())
        assertEquals(listOf(24_000, 81_700), heard.map { it.detection.index })
        assertEquals(listOf(a to 3, b to 250), heard.map { it.frame!!.emitterId to it.frame!!.counter })
        assertTrue(heard.all { it.frame!!.ok })
    }

    @Test fun silenceGivesNoChirp() {
        val rnd = Random(3)
        val x = FloatArray(rate * 2) { (rnd.nextFloat() - 0.5f) * 0.1f }
        assertTrue(MultiChirp.detectAll(x, Chirp.generate()).isEmpty())
        assertNull(IdFrame.decode(x, x.size - 10))
    }

    @Test fun corruptedFrameFailsChecksum() {
        val s = IdFrame.signal(0x1234, 9)
        val off = IdFrame.offsetAfterChirpStart(rate)
        for (i in 0 until IdFrame.bitSamples(rate)) s[off + i] = 0f // erase first bit (a 0 bit for 0x1234)
        val bitOne = IdFrame.encode(0xFFFF, 0xFF)
        for (i in 0 until IdFrame.bitSamples(rate)) s[off + i] = bitOne[i]
        assertEquals(false, IdFrame.decode(s, 0)!!.ok)
    }

    @Test fun optionalFieldsRoundTripAndOldEventsStillRead() {
        val e = SyncEvent("syn-1", Stamp(1, 2, "tb"), "AC-1234-9", SyncMethod.AUDIO_CHIRP, SyncRole.EMITTER, 2,
            protocol = IdFrame.PROTOCOL, heardIds = listOf("1234", "ABCD"), localChirpIndex = 0, peerChirpIndices = listOf(1), emitCounter = 9, label = "S1432")
        val json = LumiJson.encodeToString(SessionEvent.serializer(), e)
        assertEquals(e, LumiJson.decodeFromString(SessionEvent.serializer(), json))
        val new = setOf("protocol", "heardIds", "localChirpIndex", "peerChirpIndices", "emitCounter", "label")
        val old = JsonObject(LumiJson.parseToJsonElement(json).jsonObject.filterKeys { it !in new })
        val back = LumiJson.decodeFromString(SessionEvent.serializer(), old.toString()) as SyncEvent
        assertEquals(null, back.protocol); assertEquals("AC-1234-9", back.syncCode)
    }
}
