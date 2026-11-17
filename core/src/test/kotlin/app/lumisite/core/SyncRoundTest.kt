package app.lumisite.core

import app.lumisite.core.sync.HeardChirp
import app.lumisite.core.sync.Link
import app.lumisite.core.sync.MasterMesh
import app.lumisite.core.sync.MeshMsg
import app.lumisite.core.sync.SensorInventory
import app.lumisite.core.sync.SlaveMesh
import app.lumisite.core.sync.SyncPrereq
import app.lumisite.core.sync.SyncRoundCalc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncRoundTest {
    // Synthetic round: A (master) emits at real t=1.000 s, B (slave) at t=1.700 s, 3.43 m apart (10 ms of sound).
    // A's clock = real; B's clock = real + 12.3 ms.
    private val prop = 10_000_000L
    private val off = 12_300_000L
    private val masterHeard = listOf(HeardChirp("018A", 7, 1_000_000_000L), HeardChirp("02B7", 3, 1_700_000_000L + prop))
    private fun slaveResult(heard: List<HeardChirp> = listOf(HeardChirp("018A", 7, 1_000_000_000L + prop + off), HeardChirp("02B7", 3, 1_700_000_000L + off))) =
        MeshMsg.SyncResult("r1", "02B7", heard, "tb2")

    @Test fun beepBeepCancelsPropagation() {
        val a = SyncRoundCalc.offset("r1", 7, "018A", masterHeard, "02B7", slaveResult())
        assertEquals(off, a.offsetNs)
        assertEquals(SyncRoundCalc.UNCERTAINTY_NS, a.uncertaintyNs)
        assertEquals("Xiaomi : entendu, décalage 12,3 ms ± 1,0", a.text("Xiaomi").replace('.', ','))
    }

    @Test fun oneWayHearingGivesNoOffset() {
        val a = SyncRoundCalc.offset("r1", 7, "018A", masterHeard, "02B7", slaveResult(listOf(HeardChirp("02B7", 3, 5L))))
        assertNull(a.offsetNs); assertTrue(a.masterHeardSlave); assertFalse(a.slaveHeardMaster)
        val b = SyncRoundCalc.offset("r1", 7, "018A", masterHeard, "02B7", slaveResult().copy(error = "micro occupé"))
        assertNull(b.offsetNs); assertTrue(b.text("X").contains("échec"))
    }

    @Test fun protocolRoundTripThroughMasterMesh() {
        val m = MasterMesh("A"); var t = 1L
        m.onConnecting("e1", "Xiaomi", t); m.onConnected("e1", t)
        val (_, sends) = m.syncRound("r1", 7, 12_000, t)
        assertEquals(listOf("e1"), sends.map { it.first })
        val msg = MeshMsg.decode(MeshMsg.encode(sends.single().second)) as MeshMsg.SyncRound
        assertEquals(12_000, msg.windowMs); assertEquals(7, msg.counter)
        // Slave result before the master's own detections: no offset yet, then computed once both are known.
        m.onMessage("e1", MeshMsg.decode(MeshMsg.encode(slaveResult()))!!, ++t)
        assertNull(m.peers["e1"]!!.audio)
        m.onOwnRound("r1", "018A", masterHeard, ++t)
        assertEquals(off, m.peers["e1"]!!.audio!!.offsetNs)
        // A stale result (other round) is ignored.
        m.syncRound("r2", 8, 12_000, ++t)
        m.onMessage("e1", slaveResult(), ++t); m.onOwnRound("r2", "018A", masterHeard, ++t)
        assertNull(m.peers["e1"]!!.audio)
    }

    @Test fun pingEvery5sLostAfter15s() {
        assertEquals(5_000_000_000L, MasterMesh.PING_PERIOD_NS); assertEquals(15_000_000_000L, MasterMesh.LOST_AFTER_NS)
        val m = MasterMesh("A"); m.onConnecting("e1", "X", 0); m.onConnected("e1", 0)
        m.tick(14_000_000_000L); assertEquals(Link.CONNECTED, m.peers["e1"]!!.link)
        m.tick(16_000_000_000L); assertEquals(Link.LOST, m.peers["e1"]!!.link)
        val s = SlaveMesh("B", "X", "tb"); s.onConnected(0L); s.onMessage(MeshMsg.Ping(1, 0), 1_000L) { 2_000L }
        assertFalse(s.masterSilent(10_000_000_000L)); assertTrue(s.masterSilent(16_000_001_000L))
    }

    @Test fun sensorsListedEvenAbsentAndSentToMaster() {
        val l = SensorInventory.report(mapOf("MICRO" to null, "ACCEL" to "BMI", "BARO" to null), setOf("MICRO", "ACCEL", "DEPTH"))
        assertEquals(SensorInventory.KINDS.size, l.size)
        assertEquals("Accéléromètre : actif (BMI)", l.first { it.kind == "ACCEL" }.text)
        assertEquals("Baromètre : présent, inactif", l.first { it.kind == "BARO" }.text)
        assertEquals("Profondeur (ToF) : absent", l.first { it.kind == "DEPTH" }.text)
        val m = MasterMesh("A"); m.onConnecting("e1", "X", 0); m.onConnected("e1", 0)
        m.onMessage("e1", MeshMsg.decode(MeshMsg.encode(MeshMsg.Sensors(l)))!!, 1)
        assertEquals(l, m.peers["e1"]!!.sensors)
    }

    @Test fun prereqMessagesOnePerMissingItem() {
        assertEquals(0, SyncPrereq.messages(true, true, true, true, 34).size)
        assertEquals(4, SyncPrereq.messages(false, false, false, false, 34).size)
        assertEquals(1, SyncPrereq.messages(false, false, false, true, 30).size)
    }

    @Test fun listeningWindowIsBounded() {
        assertTrue(SyncRoundCalc.windowOk(12_000))
        assertFalse(SyncRoundCalc.windowOk(Int.MAX_VALUE)); assertFalse(SyncRoundCalc.windowOk(0)); assertFalse(SyncRoundCalc.windowOk(-1))
    }

    @Test fun reconnectionRestartsTheSilenceDeadline() {
        val s = SlaveMesh("B", "X", "tb"); s.onConnected(0L); s.onMessage(MeshMsg.Ping(1, 0), 1_000_000_000L) { 0L }
        s.onDisconnected(); s.onConnected(30_000_000_000L)
        assertFalse(s.masterSilent(31_000_000_000L))
        assertTrue(s.masterSilent(46_000_000_000L)) // no ping at all after the reconnection: lost after 15 s as well
    }
}
