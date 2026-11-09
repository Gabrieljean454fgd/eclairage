package app.lumisite.core

import app.lumisite.core.sync.Capture
import app.lumisite.core.sync.Clock
import app.lumisite.core.sync.ClockAlign
import app.lumisite.core.sync.Link
import app.lumisite.core.sync.MasterMesh
import app.lumisite.core.sync.MeshMsg
import app.lumisite.core.sync.SlaveMesh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Paquet 2: master + 2 slaves over a fake transport (latency, clock offsets), no Nearby. */
class SyncMeshTest {
    private val ms = 1_000_000L

    @Test
    fun noSlaveShowsZeroDevice() {
        val m = MasterMesh("maitre")
        assertEquals("0 appareil", m.summary().text)
        assertTrue(!m.summary().allSynced)
    }

    @Test
    fun twoSlavesGetConnectedAlignedCapturingThenOneIsLost() {
        val master = MasterMesh("maitre")
        // Slave clocks: monotonic ns = master + offset.
        val offsets = mapOf("e1" to 5_000 * ms, "e2" to -123_456 * ms)
        val slaves = offsets.keys.associateWith { SlaveMesh("dev-$it", "Esclave $it", "tb-$it") }
        var t = 0L
        val oneWay = 20 * ms
        fun deliverToSlave(id: String, msg: MeshMsg, tSent: Long) {
            val rx = tSent + oneWay
            val reply = slaves.getValue(id).onMessage(MeshMsg.decode(MeshMsg.encode(msg))!!, rx + offsets.getValue(id)) { rx + 1 * ms + offsets.getValue(id) }
            if (reply != null) master.onMessage(id, MeshMsg.decode(MeshMsg.encode(reply))!!, rx + 1 * ms + oneWay)
        }
        for (id in offsets.keys) {
            master.onConnecting(id, "Esclave $id", t)
            master.onConnected(id, t)
            master.onMessage(id, slaves.getValue(id).hello(), t)
        }
        assertEquals("0/2 synchronisé(s) · 0 en capture", master.summary().text)
        repeat(3) { t += MasterMesh.PING_PERIOD_NS; master.pings(t).forEach { (id, p) -> deliverToSlave(id, p, t) } }
        for ((id, off) in offsets) {
            val p = master.peers.getValue(id)
            assertEquals(Clock.ALIGNED, p.clock)
            assertTrue(kotlin.math.abs(p.offsetNs!! - off) <= p.uncertaintyNs!!, "offset ${p.offsetNs} vs $off ± ${p.uncertaintyNs}")
        }
        assertTrue(master.summary().allSynced)
        master.start("cap1", t).forEach { (id, msg) -> deliverToSlave(id, msg, t) }
        assertEquals("2/2 synchronisé(s) · 2 en capture", master.summary().text)
        assertTrue(slaves.values.all { it.capture == Capture.CAPTURING })
        // e2 goes silent: lost after 6 s, never shown as synchronised.
        repeat(4) { t += MasterMesh.PING_PERIOD_NS; master.pings(t).filter { it.first == "e1" }.forEach { (id, p) -> deliverToSlave(id, p, t) }; master.tick(t) }
        assertEquals(Link.LOST, master.peers.getValue("e2").link)
        assertEquals("1/1 synchronisé(s) · 1 en capture · 1 perdu(s)", master.summary().text)
    }

    @Test
    fun slaveRestartDropsClockSamples() {
        val master = MasterMesh("maitre")
        master.onConnecting("e1", "E1", 0); master.onConnected("e1", 0)
        master.onMessage("e1", MeshMsg.Status(Capture.CAPTURING, 0, "tb-1"), 0)
        repeat(3) { i -> master.onMessage("e1", MeshMsg.Pong(i.toLong(), i * 1000L, i * 1000L + 10, i * 1000L + 20), i * 1000L + 30) }
        assertEquals(Clock.ALIGNED, master.peers.getValue("e1").clock)
        val logs = master.onMessage("e1", MeshMsg.Status(Capture.CAPTURING, 0, "tb-2", gap = "redémarrage"), 5000).first
        assertEquals(Clock.NOT_ALIGNED, master.peers.getValue("e1").clock)
        assertTrue(logs.any { it.what.startsWith("interruption") })
    }

    @Test
    fun roundTripSampleAndHugeRttIsNotAligned() {
        val s = assertNotNull(ClockAlign.sample(MeshMsg.Pong(1, 0, 1_010, 1_020), 40))
        assertEquals(995L, s.offsetNs); assertEquals(30L, s.rttNs)
        val slow = List(3) { ClockAlign.sample(MeshMsg.Pong(1, 0, 400 * ms, 400 * ms), 800 * ms)!! }
        assertEquals(Clock.ALIGNING, ClockAlign.state(slow))
    }
}
