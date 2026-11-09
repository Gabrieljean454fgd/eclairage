package app.lumisite.core.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Paquet 2 — synchro maître / esclave (Accueil > Synchro). Pure model, transport-agnostic (Nearby Connections P2P_STAR
 * in the app, a fake transport in JVM tests). Four layers are kept apart on purpose:
 *  - connexion : link to the peer (Nearby endpoint) — [Link];
 *  - alignement temporel : offset between monotonic clocks, from ping/pong round trips — [Clock];
 *  - capture : what the slave is recording — [Capture];
 *  - repère spatial : slaves do not share the master's site frame (they only timestamp) — [Frame].
 * Only the master records business surveys; slaves send monotonic timestamps and media.
 */
enum class SyncRole2 { SAISIE_MAITRE, CAPTATION_ESCLAVE }

enum class Link(val label: String) { CONNECTING("connexion…"), CONNECTED("connecté"), LOST("perdu") }

enum class Clock(val label: String) { NOT_ALIGNED("non aligné"), ALIGNING("alignement…"), ALIGNED("aligné") }

enum class Capture(val label: String) { IDLE("en attente"), CAPTURING("en capture"), STOPPED("arrêtée") }

enum class Frame(val label: String) { NOT_SHARED("repère non partagé (horodatage seul)") }

/** Messages exchanged as Nearby BYTES payloads (one JSON object each). Times are monotonic ns of the sender. */
@Serializable
sealed interface MeshMsg {
    @Serializable @SerialName("hello") data class Hello(val deviceId: String, val name: String, val role: SyncRole2, val timeBaseId: String) : MeshMsg
    @Serializable @SerialName("ping") data class Ping(val seq: Long, val tMasterTxNs: Long) : MeshMsg
    @Serializable @SerialName("pong") data class Pong(val seq: Long, val tMasterTxNs: Long, val tSlaveRxNs: Long, val tSlaveTxNs: Long) : MeshMsg
    @Serializable @SerialName("start") data class StartCapture(val captureId: String, val tMasterNs: Long) : MeshMsg
    @Serializable @SerialName("stop") data class StopCapture(val captureId: String, val tMasterNs: Long) : MeshMsg
    @Serializable @SerialName("status") data class Status(val capture: Capture, val tSlaveNs: Long, val timeBaseId: String, val gap: String? = null) : MeshMsg
    /** Announces a FILE payload: [payloadId] is the Nearby payload id, times in the slave clock. */
    @Serializable @SerialName("media") data class Media(val payloadId: Long, val name: String, val tStartNs: Long, val tEndNs: Long, val bytes: Long, val sha256: String, val timeBaseId: String) : MeshMsg

    companion object {
        private val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = false }
        fun encode(m: MeshMsg): ByteArray = json.encodeToString(serializer(), m).toByteArray()
        fun decode(b: ByteArray): MeshMsg? = runCatching { json.decodeFromString(serializer(), b.decodeToString()) }.getOrNull()
    }
}

/**
 * NTP-like offset from one round trip: master sends at t0 (master clock), slave receives t1 / replies t2 (slave clock),
 * master receives t3. offset = slave − master ≈ ((t1 − t0) + (t2 − t3)) / 2, error ≤ rtt / 2.
 */
data class ClockSample(val tMasterNs: Long, val offsetNs: Long, val rttNs: Long)

object ClockAlign {
    const val MIN_SAMPLES = 3
    /** Max half round trip accepted as « aligné » (Nearby over Bluetooth/Wi-Fi: tens of ms; acoustic beacons stay the fine marker). */
    const val MAX_UNCERTAINTY_NS = 50_000_000L
    const val WINDOW = 20

    fun sample(p: MeshMsg.Pong, tMasterRxNs: Long): ClockSample? {
        val rtt = (tMasterRxNs - p.tMasterTxNs) - (p.tSlaveTxNs - p.tSlaveRxNs)
        if (rtt < 0) return null
        return ClockSample(p.tMasterTxNs, ((p.tSlaveRxNs - p.tMasterTxNs) + (p.tSlaveTxNs - tMasterRxNs)) / 2, rtt)
    }

    /** Best estimate = the sample with the smallest round trip in the window; its uncertainty is rtt / 2. */
    fun best(samples: List<ClockSample>): ClockSample? = samples.takeLast(WINDOW).minByOrNull { it.rttNs }

    fun state(samples: List<ClockSample>): Clock {
        val b = best(samples) ?: return Clock.NOT_ALIGNED
        return if (samples.size >= MIN_SAMPLES && b.rttNs / 2 <= MAX_UNCERTAINTY_NS) Clock.ALIGNED else Clock.ALIGNING
    }
}

data class Peer(
    val endpointId: String,
    val name: String,
    val link: Link = Link.CONNECTING,
    val samples: List<ClockSample> = emptyList(),
    val capture: Capture = Capture.IDLE,
    val lastHeardNs: Long = 0,
    val deviceId: String? = null,
    val timeBaseId: String? = null,
    val media: Int = 0,
    val frame: Frame = Frame.NOT_SHARED,
    /** Nearby link open (independent of [link], which turns LOST after a silence): pings keep going so it can recover. */
    val transport: Boolean = false,
) {
    val clock: Clock get() = if (link == Link.CONNECTED) ClockAlign.state(samples) else Clock.NOT_ALIGNED
    val offsetNs: Long? get() = ClockAlign.best(samples)?.offsetNs
    val uncertaintyNs: Long? get() = ClockAlign.best(samples)?.let { it.rttNs / 2 }
    /** « synchronisé » = connected AND clock aligned; capture and spatial frame are reported separately. */
    val synced: Boolean get() = link == Link.CONNECTED && clock == Clock.ALIGNED
}

/** Event worth journaling (link change, alignment change, capture change, media received). */
data class MeshLog(val tNs: Long, val endpointId: String, val what: String)

/**
 * Master-side state machine. Every transition is returned as [MeshLog] so the caller can journal it (append-only).
 * A peer silent for more than [LOST_AFTER_NS] is « perdu »: it is never shown as still synchronised.
 */
class MasterMesh(val deviceId: String) {
    companion object {
        const val PING_PERIOD_NS = 2_000_000_000L
        const val LOST_AFTER_NS = 6_000_000_000L
    }

    var peers: Map<String, Peer> = emptyMap(); private set
    var captureId: String? = null; private set
    private var seq = 0L

    private fun put(p: Peer) { peers = peers + (p.endpointId to p) }

    private fun change(old: Peer?, new: Peer, tNs: Long): List<MeshLog> = buildList {
        put(new)
        if (old?.link != new.link) add(MeshLog(tNs, new.endpointId, "connexion : ${new.link.label}"))
        if (old?.clock != new.clock) add(MeshLog(tNs, new.endpointId, "alignement : ${new.clock.label}" +
            (new.offsetNs?.takeIf { new.clock == Clock.ALIGNED }?.let { " (décalage %.1f ms ± %.1f ms)".format(it / 1e6, (new.uncertaintyNs ?: 0) / 1e6) } ?: "")))
        if (old?.capture != new.capture) add(MeshLog(tNs, new.endpointId, "capture : ${new.capture.label}"))
    }

    fun onConnecting(id: String, name: String, tNs: Long) = change(peers[id], (peers[id] ?: Peer(id, name)).copy(name = name, link = Link.CONNECTING, lastHeardNs = tNs), tNs)

    fun onConnected(id: String, tNs: Long) = peers[id]?.let { change(it, it.copy(link = Link.CONNECTED, lastHeardNs = tNs, transport = true), tNs) } ?: emptyList()

    fun onDisconnected(id: String, tNs: Long) = peers[id]?.let { change(it, it.copy(link = Link.LOST, transport = false), tNs) } ?: emptyList()

    /** Pings to send now: one per open Nearby link, including peers shown « perdu » after a silence (they may answer again). */
    fun pings(tNs: Long): List<Pair<String, MeshMsg.Ping>> =
        peers.values.filter { it.transport }.map { it.endpointId to MeshMsg.Ping(++seq, tNs) }

    /** Marks silent peers as lost. */
    fun tick(tNs: Long): List<MeshLog> = peers.values.filter { it.link == Link.CONNECTED && tNs - it.lastHeardNs > LOST_AFTER_NS }
        .flatMap { change(it, it.copy(link = Link.LOST), tNs) }

    fun start(captureId: String, tNs: Long): List<Pair<String, MeshMsg>> {
        this.captureId = captureId
        return peers.values.filter { it.transport }.map { it.endpointId to MeshMsg.StartCapture(captureId, tNs) }
    }

    fun stop(tNs: Long): List<Pair<String, MeshMsg>> {
        val id = captureId ?: return emptyList()
        captureId = null
        return peers.values.filter { it.transport }.map { it.endpointId to MeshMsg.StopCapture(id, tNs) }
    }

    /** Message from a slave. Returns logs, plus a StartCapture to send if a capture is running and the slave just joined. */
    fun onMessage(id: String, m: MeshMsg, tNs: Long): Pair<List<MeshLog>, MeshMsg?> {
        val p = peers[id] ?: return emptyList<MeshLog>() to null
        var reply: MeshMsg? = null
        val n = when (m) {
            is MeshMsg.Hello -> { if (captureId != null) reply = MeshMsg.StartCapture(captureId!!, tNs); p.copy(deviceId = m.deviceId, timeBaseId = m.timeBaseId, name = m.name,
                samples = if (p.timeBaseId != null && p.timeBaseId != m.timeBaseId) emptyList() else p.samples) }
            is MeshMsg.Pong -> ClockAlign.sample(m, tNs)?.let { s -> p.copy(samples = (p.samples + s).takeLast(ClockAlign.WINDOW)) } ?: p
            // A new time base on the slave (restart) invalidates the clock samples: no silent continuity.
            is MeshMsg.Status -> if (p.timeBaseId != null && p.timeBaseId != m.timeBaseId) p.copy(capture = m.capture, timeBaseId = m.timeBaseId, samples = emptyList())
                else p.copy(capture = m.capture, timeBaseId = m.timeBaseId)
            is MeshMsg.Media -> p.copy(media = p.media + 1)
            else -> p
        }.copy(link = Link.CONNECTED, lastHeardNs = tNs)
        val logs = change(p, n, tNs).toMutableList()
        if (m is MeshMsg.Status && m.gap != null) logs += MeshLog(tNs, id, "interruption : ${m.gap}")
        if (m is MeshMsg.Media) logs += MeshLog(tNs, id, "média annoncé : ${m.name} (${m.bytes} o)")
        return logs to reply
    }

    data class Summary(val connected: Int, val synced: Int, val capturing: Int, val lost: Int) {
        /** « 0 appareil » with no slave; otherwise « k/N synchronisé(s) » where N = connected slaves. */
        val text: String get() = when {
            connected == 0 && lost == 0 -> "0 appareil"
            connected == 0 -> "0 appareil connecté · $lost perdu(s)"
            else -> "$synced/$connected synchronisé(s) · $capturing en capture" + (if (lost > 0) " · $lost perdu(s)" else "")
        }
        val allSynced: Boolean get() = connected > 0 && synced == connected
    }

    fun summary(): Summary = peers.values.let { v ->
        Summary(v.count { it.link == Link.CONNECTED }, v.count { it.synced }, v.count { it.link == Link.CONNECTED && it.capture == Capture.CAPTURING }, v.count { it.link == Link.LOST })
    }
}

/** Slave side: answers pings with its own monotonic clock and follows start/stop orders. */
class SlaveMesh(val deviceId: String, val name: String, var timeBaseId: String) {
    var capture: Capture = Capture.IDLE; private set
    var captureId: String? = null; private set
    var masterConnected = false; private set
    var lastPingNs: Long = 0; private set

    fun hello() = MeshMsg.Hello(deviceId, name, SyncRole2.CAPTATION_ESCLAVE, timeBaseId)

    fun onConnected() { masterConnected = true }
    fun onDisconnected() { masterConnected = false }

    /** Returns the reply to send (pong / status), or null. [tRxNs] = reception time, [now] gives the send time. */
    fun onMessage(m: MeshMsg, tRxNs: Long, now: () -> Long): MeshMsg? = when (m) {
        is MeshMsg.Ping -> { lastPingNs = tRxNs; MeshMsg.Pong(m.seq, m.tMasterTxNs, tRxNs, now()) }
        is MeshMsg.StartCapture -> { capture = Capture.CAPTURING; captureId = m.captureId; MeshMsg.Status(capture, now(), timeBaseId) }
        is MeshMsg.StopCapture -> { capture = Capture.STOPPED; MeshMsg.Status(capture, now(), timeBaseId) }
        else -> null
    }
}
