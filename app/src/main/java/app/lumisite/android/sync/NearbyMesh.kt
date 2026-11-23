package app.lumisite.android.sync

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.Manifest
import android.os.Build
import app.lumisite.android.data.AndroidClock
import app.lumisite.android.data.DeviceInfo
import app.lumisite.android.data.DiagLog
import app.lumisite.android.data.Ids
import app.lumisite.core.sync.Capture
import app.lumisite.core.sync.MasterMesh
import app.lumisite.core.sync.SyncRoundCalc
import app.lumisite.core.sync.MeshLog
import app.lumisite.core.sync.MeshMsg
import app.lumisite.core.sync.SlaveMesh
import app.lumisite.core.sync.SyncRole2
import app.lumisite.core.sync.HeardChirp
import app.lumisite.core.sync.SensorInventory
import app.lumisite.core.sync.SensorState
import app.lumisite.core.sync.SyncDisplay
import app.lumisite.android.capture.AudioSync
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import java.io.BufferedWriter
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Paquet 2 — Accueil > Synchro. Nearby Connections P2P_STAR, local only (docs/SYNCHRO_NEARBY.md).
 * Master (« Saisie ») advertises and accepts slaves; slaves (« Captation-synchro ») discover and connect.
 * Every state change is appended to `files/synchro/<début>/synchro.ndjson` (t_mono_ns, utc, appareil, quoi).
 * Lives in the application process: the master can go back to its session while the synchro keeps running.
 */
object NearbyMesh {
    const val SERVICE_ID = "app.lumisite.synchro"
    private val STRATEGY = Strategy.P2P_STAR

    data class UiState(
        val role: SyncRole2? = null,
        val running: Boolean = false,
        /** Nearby radio status (advertising / discovery), separate from the peers' state. */
        val radio: String = "",
        val master: MasterMesh.Summary? = null,
        val peers: List<app.lumisite.core.sync.Peer> = emptyList(),
        val capturing: Boolean = false,
        val slaveMaster: String? = null,
        val slaveCapture: Capture = Capture.IDLE,
        val slaveLastPingNs: Long = 0,
        val log: List<String> = emptyList(),
        /** v176d: sensors detected / activated on this phone (slave). */
        val sensors: List<SensorState> = emptyList(),
        /** v176d: acoustic round in progress or last result on this phone. */
        val round: String? = null,
        val roundBusy: Boolean = false,
        /** v176g: readable name of this phone, as announced to the others (Nearby endpoint name and Hello). */
        val selfName: String = "",
        /** v176g 1f: foreground service « Synchro LumiSite active » running (null = not attempted / refused, see journal). */
        val foreground: Boolean = false,
        /** v176g 1g: debug-only simulated slave injected in the master. */
        val simulated: String? = null,
        /** v176h: run folder of this phone (files/synchro/<début>), kept after the synchro stops. */
        val runDir: String? = null,
        /** v176h: site / visit / master tied to the run (from the master's SiteContext). */
        val tiedTo: String? = null,
        /** v176h: GPS state of this phone (slave), with the last fix accuracy. */
        val gps: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: ConnectionsClient? = null
    private var master: MasterMesh? = null
    private var slave: SlaveMesh? = null
    private var masterEndpoint: String? = null
    private var loop: Job? = null
    private var dir: File? = null
    private var appCtx: Context? = null
    private val pendingMedia = HashMap<Long, MeshMsg.Media>()
    private val pendingFiles = HashMap<Long, Payload>()
    private var recorder: SlaveRecorder? = null
    private val lock = Any()

    private fun now() = AndroidClock.monotonicNs()

    private fun journal(endpoint: String, what: String, raw: String? = null) {
        val line = "{\"t_mono_ns\":${now()},\"utc_ms\":${AndroidClock.utcEpochMs()},\"appareil\":${q(endpoint)},\"quoi\":${q(what)}" +
            (raw?.let { ",\"brut\":${q(it)}" } ?: "") + "}\n"
        runCatching { dir?.let { File(it, "synchro.ndjson").appendText(line) } }
        DiagLog.i("SYNCHRO", "$endpoint : $what")
        val label = master?.peers?.get(endpoint)?.name ?: endpoint
        val hms = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(AndroidClock.utcEpochMs()))
        _state.update { it.copy(log = (it.log + "$hms · $label · $what").takeLast(30)) }
    }

    /** JSON string literal (backslash, quote and every control character escaped: one journal record per line). */
    private fun q(s: String) = "\"" + buildString { s.forEach { ch -> when {
        ch == '\\' -> append("\\\\"); ch == '"' -> append("\\\""); ch == '\n' -> append("\\n"); ch == '\r' -> append("\\r"); ch == '\t' -> append("\\t")
        ch < ' ' || ch == '\u2028' || ch == '\u2029' -> append("\\u%04x".format(ch.code)); else -> append(ch) } } } + "\""

    private fun logs(l: List<MeshLog>) = l.forEach { journal(it.endpointId, it.what, it.raw) }

    /** v176g: offset of one slave for a top (raw values, master clock − slave clock). */
    data class TopOffset(val name: String, val offsetNs: Long, val uncertaintyNs: Long?)

    /** Called (Nearby thread) each time a top yields slave offsets; the open session records it (export clock_offset_ms). */
    @Volatile var onTop: ((roundId: String, offsets: List<TopOffset>) -> Unit)? = null

    private fun notifyTop(m: MasterMesh) {
        val a = m.peers.values.mapNotNull { p -> p.audio?.takeIf { it.offsetNs != null }?.let { p to it } }
        val roundId = a.lastOrNull()?.second?.roundId ?: return
        val offs = a.filter { it.second.roundId == roundId }.map { (p, o) -> TopOffset(p.name, o.offsetNs!!, o.uncertaintyNs) }
        runCatching { onTop?.invoke(roundId, offs) }
        a.filter { it.second.roundId == roundId }.forEach { (p, o) -> send(p.endpointId, MeshMsg.ClockInfo("top", o.offsetNs?.let { -it }, o.uncertaintyNs, roundId)) }
    }

    private fun publish() {
        val m = master
        _state.update { it.copy(master = m?.summary(), peers = m?.peers?.values?.sortedBy { it.name } ?: emptyList(),
            capturing = m?.captureId != null, slaveCapture = slave?.capture ?: Capture.IDLE, slaveLastPingNs = slave?.lastPingNs ?: 0) }
    }

    fun start(ctx: Context, role: SyncRole2) {
        stop()
        appCtx = ctx.applicationContext
        val c = Nearby.getConnectionsClient(ctx.applicationContext)
        client = c
        dir = File(ctx.filesDir, "synchro/" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())).apply { mkdirs() }
        val name = deviceName(ctx)
        _state.value = UiState(role = role, running = true, radio = "Démarrage…", selfName = name)
        val fg = SyncService.start(ctx.applicationContext)
        _state.update { it.copy(foreground = fg) }
        _state.update { it.copy(runDir = dir?.name) }
        meta = app.lumisite.core.sync.SyncFolderMeta(role = if (role == SyncRole2.SAISIE_MAITRE) "maitre" else "esclave", device = name,
            deviceId = DeviceInfo.deviceId(ctx), startedUtcMs = AndroidClock.utcEpochMs(),
            masterName = if (role == SyncRole2.SAISIE_MAITRE) name else null,
        ).let { m -> siteCtx?.takeIf { role == SyncRole2.SAISIE_MAITRE }?.let { m.linkedTo(it.siteId, it.siteName, it.sessionId, m.startedUtcMs) } ?: m }
        saveMeta()
        journal("local", "nom de ce téléphone : $name")
        journal("local", if (_state.value.foreground) "service de premier plan actif (notification « Synchro LumiSite active »)" else "service de premier plan refusé : la synchro peut être coupée écran éteint")
        journal("local", "rôle : " + if (role == SyncRole2.SAISIE_MAITRE) "Saisie (maître)" else "Captation-synchro (esclave)")
        if (role == SyncRole2.SAISIE_MAITRE) {
            master = MasterMesh(DeviceInfo.deviceId(ctx))
            c.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
                .addOnSuccessListener { radio("Recherche d'esclaves (annonce active)") }
                .addOnFailureListener { radio("Annonce impossible : ${it.message}") }
            simulatedSlaveName(ctx)?.let { simulate(it) }
            loop = scope.launch {
                while (isActive) {
                    synchronized(lock) {
                        val m = master ?: return@launch
                        val t = now()
                        m.pings(t).forEach { (id, p) -> send(id, p) }
                        if (_state.value.simulated != null) answerSimulated(t)
                        logs(m.tick(t))
                        if (++clockTicks % CLOCK_EVERY_TICKS == 0) m.peers.values.filter { it.link == app.lumisite.core.sync.Link.CONNECTED && it.offsetNs != null }
                            .forEach { p -> send(p.endpointId, MeshMsg.ClockInfo("ping", -p.offsetNs!!, p.uncertaintyNs)) }
                        publish()
                    }
                    delay(MasterMesh.PING_PERIOD_NS / 1_000_000)
                }
            }
        } else {
            slave = SlaveMesh(DeviceInfo.deviceId(ctx), name, Ids.new("tb"))
            meta = meta?.copy(timeBaseId = slave?.timeBaseId); saveMeta()
            startSensors(ctx)
            startGps(ctx)
            discover(c)
            loop = scope.launch {
                while (isActive) {
                    synchronized(lock) {
                        val s = slave ?: return@launch
                        if (s.masterSilent(now())) {
                            journal("local", "maître perdu (aucun ping depuis ${MasterMesh.LOST_AFTER_NS / 1_000_000_000} s) : reconnexion automatique")
                            masterEndpoint?.let { client?.disconnectFromEndpoint(it) }
                            s.onDisconnected(); masterEndpoint = null
                            _state.update { it.copy(slaveMaster = null) }
                            client?.let { discover(it) }
                        }
                    }
                    flushFiles()
                    publish(); delay(1000)
                }
            }
        }
        publish()
    }

    private fun discover(c: ConnectionsClient) {
        c.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                if (masterEndpoint != null) return
                journal(id, "maître trouvé : ${info.endpointName}")
                c.requestConnection(slave?.name ?: "esclave", id, lifecycle)
                    .addOnFailureListener { journal(id, "demande de connexion refusée : ${it.message}") }
            }
            override fun onEndpointLost(id: String) { journal(id, "maître hors de portée") }
        }, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnSuccessListener { radio("Recherche du maître (découverte active)") }
            .addOnFailureListener { radio("Découverte impossible : ${it.message}") }
    }

    private fun radio(s: String) { _state.update { it.copy(radio = s) }; journal("local", "radio : $s") }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            synchronized(lock) { master?.let { logs(it.onConnecting(id, info.endpointName, now())) } }
            if (slave != null) _state.update { it.copy(slaveMaster = "${info.endpointName} (connexion…)") }
            client?.acceptConnection(id, payloads)
            publish()
        }

        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            val ok = r.status.isSuccess
            synchronized(lock) {
                master?.let { logs(if (ok) it.onConnected(id, now()) else it.onDisconnected(id, now())) }
                slave?.let { s ->
                    if (ok) { masterEndpoint = id; s.onConnected(now()); client?.stopDiscovery(); send(id, s.hello()); send(id, MeshMsg.Sensors(_state.value.sensors))
                        _state.update { it.copy(slaveMaster = it.slaveMaster?.replace("(connexion…)", "(connecté)")) }; journal(id, "connexion : connecté") }
                    else journal(id, "connexion refusée : ${r.status.statusMessage}")
                }
            }
            publish()
        }

        override fun onDisconnected(id: String) {
            synchronized(lock) {
                master?.let { logs(it.onDisconnected(id, now())) }
                slave?.let { s ->
                    s.onDisconnected(); masterEndpoint = null
                    _state.update { it.copy(slaveMaster = null) }
                    journal(id, "connexion : perdu")
                    // The capture keeps running locally (journaled); rediscover the master.
                    client?.let { discover(it) }
                }
            }
            publish()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) {
            val t = now()
            when (p.type) {
                Payload.Type.BYTES -> {
                    val m = MeshMsg.decode(p.asBytes() ?: return) ?: return
                    synchronized(lock) {
                        master?.let { mm ->
                            val (l, reply) = mm.onMessage(id, m, t)
                            logs(l); reply?.let { send(id, it) }
                            if (m is MeshMsg.SyncResult) notifyTop(mm)
                            if (m is MeshMsg.Hello) send(id, siteMessage())
                            if (m is MeshMsg.Media) pendingMedia[m.payloadId] = m
                        }
                        slave?.let { s ->
                            when (m) {
                                is MeshMsg.StartCapture -> appCtx?.let { startRecording(it, m.captureId, id) }
                                is MeshMsg.StopCapture -> stopRecording(id)
                                is MeshMsg.SyncRound -> slaveRound(id, m)
                                is MeshMsg.SiteContext -> onSiteContext(m)
                                is MeshMsg.ClockInfo -> journal("local", "horloge (${m.source}) : maître − esclave " +
                                    (m.offsetNs?.let { SyncDisplay.alignmentText(it, m.uncertaintyNs ?: 0) } ?: "non mesuré") + (m.roundId?.let { " · top $it" } ?: ""),
                                    "{\"source\":${q(m.source)},\"decalage_ns\":${m.offsetNs},\"incertitude_ns\":${m.uncertaintyNs},\"top\":${m.roundId?.let { q(it) }}}")
                                else -> {}
                            }
                            s.onMessage(m, t, ::now)?.let { send(id, it) }
                            if (m is MeshMsg.StartCapture || m is MeshMsg.StopCapture) journal("local", "capture : ${s.capture.label}")
                        }
                    }
                }
                Payload.Type.FILE -> synchronized(lock) { pendingFiles[p.id] = p }
                else -> {}
            }
            publish()
        }

        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {
            if (u.status != PayloadTransferUpdate.Status.SUCCESS && u.status != PayloadTransferUpdate.Status.FAILURE) return
            synchronized(lock) {
                val p = pendingFiles.remove(u.payloadId) ?: return
                val meta = pendingMedia.remove(u.payloadId)
                if (u.status == PayloadTransferUpdate.Status.FAILURE) { journal(id, "média non reçu (${meta?.name ?: u.payloadId})"); return }
                val target = File(dir, "media/" + safeName("${id}_${meta?.name ?: "media_${u.payloadId}"}")).apply { parentFile?.mkdirs() }
                runCatching {
                    val f = p.asFile() ?: return
                    if (Build.VERSION.SDK_INT >= 29) appCtx!!.contentResolver.openInputStream(f.asUri()!!)!!.use { i -> target.outputStream().use { i.copyTo(it) } }
                    else @Suppress("DEPRECATION") f.asJavaFile()!!.copyTo(target, overwrite = true)
                }.onSuccess {
                    val sha = sha256(target)
                    journal(id, "média reçu : ${target.name} (${target.length()} o" + (meta?.let { m -> if (m.sha256 == sha) ", SHA-256 vérifié" else ", SHA-256 DIFFÉRENT" } ?: "") + ")")
                    meta?.let { m -> File(target.path + ".json").writeText("{\"esclave\":${q(id)},\"nom\":${q(m.name)},\"t_debut_ns_esclave\":${m.tStartNs},\"t_fin_ns_esclave\":${m.tEndNs},\"base_de_temps\":${q(m.timeBaseId)},\"decalage_ns\":${master?.peers?.get(id)?.offsetNs},\"incertitude_ns\":${master?.peers?.get(id)?.uncertaintyNs}}\n") }
                }.onFailure { journal(id, "média illisible : ${it.message}") }
            }
        }
    }

    /** A peer-supplied name never leaves media/: no separators, no « .. ». */
    private fun safeName(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').ifEmpty { "media" }

    private fun send(id: String, m: MeshMsg) {
        if (id == SIMULATED_ID) return
        client?.sendPayload(id, Payload.fromBytes(MeshMsg.encode(m)))
    }

    // ---- v176g: readable name and debug-only simulated slave ----

    private const val PREFS = "lumisite_sync"
    private const val KEY_NAME = "nom_telephone"
    const val SIMULATED_ID = "SIMU"

    fun userName(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""
    fun setUserName(ctx: Context, v: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NAME, v.take(SyncDisplay.MAX_NAME)).apply()

    /** Réglages > Nom de ce téléphone, else the system device name, else manufacturer + model; « (ABCD) » from the id. */
    fun deviceName(ctx: Context): String = SyncDisplay.deviceLabel(userName(ctx),
        runCatching { android.provider.Settings.Global.getString(ctx.contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull(),
        Build.MANUFACTURER, Build.MODEL, DeviceInfo.deviceId(ctx).takeLast(4))

    /** `adb shell settings put global lumisite_simuler_esclave "Xiaomi 13T Pro"` (debug APK only): CI without two phones. */
    private fun simulatedSlaveName(ctx: Context): String? = if (!app.lumisite.android.BuildConfig.DEBUG) null
        else runCatching { android.provider.Settings.Global.getString(ctx.contentResolver, "lumisite_simuler_esclave") }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != "0" }

    private val simulatedClockOffsetNs = 987_654_321_000_000L
    private var simulatedSeq = 0L

    /** Same calls as a real Nearby slave: onConnecting (endpoint name), onConnected, Hello, then pongs on every ping. */
    private fun simulate(model: String) = synchronized(lock) {
        val m = master ?: return@synchronized
        val label = SyncDisplay.deviceLabel(null, model, null, null, "SIMU")
        _state.update { it.copy(simulated = label) }
        journal("local", "esclave simulé (debug) : $label")
        val t = now()
        logs(m.onConnecting(SIMULATED_ID, label, t))
        logs(m.onConnected(SIMULATED_ID, t))
        logs(m.onMessage(SIMULATED_ID, MeshMsg.Hello("simu-device", label, SyncRole2.CAPTATION_ESCLAVE, "tb-simu"), t).first)
        repeat(3) { answerSimulated(now()) }
        publish()
    }

    private fun answerSimulated(t: Long) {
        val m = master ?: return
        val rx = t + 4_000_000 + simulatedClockOffsetNs
        val pong = MeshMsg.Pong(++simulatedSeq, t, rx, rx + 1_000_000)
        logs(m.onMessage(SIMULATED_ID, pong, t + 9_000_000).first)
    }

    // ---- v176d: acoustic round (« top de synchro ») driven by the master over Nearby ----

    private fun ownId(ctx: Context) = app.lumisite.core.sync.IdFrame.idOf(DeviceInfo.deviceId(ctx))

    private fun heard(r: AudioSync.Round) = r.heard.filter { it.frameOk && it.emitterHex != null }
        .map { HeardChirp(it.emitterHex!!, it.counter ?: -1, it.eventMonotonicNs) }

    /** Master is running a Nearby synchro (Session > Synchroniser then also asks every slave to emit). */
    val masterActive: Boolean get() = master != null

    /**
     * Blocking (call off the main thread): sends SyncRound to every connected slave, then runs the master's own round
     * ([wav] kept) and matches the slaves' answers (BeepBeep, core SyncRoundCalc). Returns the master's own round.
     */
    fun masterRound(ctx: Context, counter: Int, wav: File?): AudioSync.Round {
        val roundId = Ids.new("top")
        synchronized(lock) {
            val m = master ?: error("pas de synchro maître")
            val (l, sends) = m.syncRound(roundId, counter, AudioSync.WINDOW_MS, now())
            logs(l); sends.forEach { (id, msg) -> send(id, msg) }
            _state.update { it.copy(roundBusy = true, round = "Top en cours : écoute ${AudioSync.WINDOW_MS / 1000} s, chaque téléphone émet son chirp…") }
        }
        val r = try {
            AudioSync.round(AudioSync.WINDOW_MS, kotlin.random.Random.nextInt(AudioSync.EMIT_MIN_MS, AudioSync.EMIT_MAX_MS), ownId(ctx), counter,
                wav ?: dir?.let { File(it, "top_${roundId}.wav") }, AudioSync.unprocessedSupported(ctx))
        } catch (e: Exception) {
            synchronized(lock) { journal("local", "top : échec audio du maître (${e.message})"); _state.update { it.copy(roundBusy = false, round = "Échec audio : ${e.message}") } }
            throw e
        }
        synchronized(lock) {
            master?.let { logs(it.onOwnRound(roundId, "%04X".format(ownId(ctx)), heard(r), now())); notifyTop(it) }
            _state.update { it.copy(roundBusy = false, round = "Maître : ${heard(r).size} chirp(s) identifié(s). Résultat par téléphone ci-dessous.") }
            publish()
        }
        return r
    }

    private fun slaveRound(masterId: String, m: MeshMsg.SyncRound) {
        val ctx = appCtx ?: return
        if (!SyncRoundCalc.windowOk(m.windowMs)) {
            val err = "fenêtre d'écoute refusée (${m.windowMs} ms, attendu ${SyncRoundCalc.MIN_WINDOW_MS}–${SyncRoundCalc.MAX_WINDOW_MS} ms)"
            synchronized(lock) { send(masterId, MeshMsg.SyncResult(m.roundId, "%04X".format(ownId(ctx)), emptyList(), slave?.timeBaseId ?: "", err)); journal("local", "top refusé : $err") }
            return
        }
        journal("local", "top de synchro reçu : écoute ${m.windowMs / 1000} s et émission du chirp")
        _state.update { it.copy(roundBusy = true, round = "Top reçu : écoute et émission…") }
        scope.launch(Dispatchers.IO) {
            val hex = "%04X".format(ownId(ctx))
            val tb = slave?.timeBaseId ?: ""
            val res = try {
                val r = AudioSync.round(m.windowMs, kotlin.random.Random.nextInt(AudioSync.EMIT_MIN_MS, minOf(AudioSync.EMIT_MAX_MS, m.windowMs - 1500)), ownId(ctx), m.counter,
                    dir?.let { File(it, "top_${m.roundId}.wav") }, AudioSync.unprocessedSupported(ctx))
                MeshMsg.SyncResult(m.roundId, hex, heard(r), tb)
            } catch (e: Exception) { MeshMsg.SyncResult(m.roundId, hex, emptyList(), tb, e.message ?: e.javaClass.simpleName) }
            synchronized(lock) {
                send(masterId, res)
                journal("local", "top : ${res.heard.size} chirp(s) entendu(s)" + (res.error?.let { " · échec $it" } ?: "") + ", résultat envoyé au maître")
                _state.update { it.copy(roundBusy = false, round = "Dernier top : ${res.heard.size} chirp(s) entendu(s)" + (res.error?.let { " · échec : $it" } ?: "")) }
            }
        }
    }

    // ---- v176h: run descriptor (synchro.json), site / visit link, GPS, flush, export ----

    private const val CLOCK_EVERY_TICKS = 15
    private var clockTicks = 0
    private var meta: app.lumisite.core.sync.SyncFolderMeta? = null
    @Volatile private var siteCtx: MeshMsg.SiteContext? = null

    private fun saveMeta() { val d = dir ?: return; val m = meta ?: return; runCatching { app.lumisite.core.sync.SyncFolder.write(d, m) } }

    private fun tiedText(m: app.lumisite.core.sync.SyncFolderMeta?) = m?.let {
        listOfNotNull(it.siteName?.let { n -> "site « $n »" }, it.sessionId?.let { s -> "visite $s" }, it.masterName?.takeIf { _ -> it.role == "esclave" }?.let { n -> "maître $n" })
            .joinToString(" · ").ifEmpty { null }
    }

    private fun siteMessage() = MeshMsg.SiteContext(_state.value.selfName, siteCtx?.siteId, siteCtx?.siteName, siteCtx?.sessionId)

    /** Master side: the open session tells which site / visit the synchro belongs to; sent to every slave. */
    fun setSite(siteId: String, siteName: String, sessionId: String) = synchronized(lock) {
        siteCtx = MeshMsg.SiteContext("", siteId, siteName, sessionId)
        val m = master ?: return
        meta = meta?.linkedTo(siteId, siteName, sessionId, AndroidClock.utcEpochMs()); saveMeta()
        _state.update { it.copy(tiedTo = tiedText(meta)) }
        journal("local", "synchro liée au site « $siteName », visite $sessionId")
        m.peers.values.filter { it.link == app.lumisite.core.sync.Link.CONNECTED }.forEach { send(it.endpointId, siteMessage()) }
    }

    /** Master side: the visit is finished; later recordings are no longer tied to it (the run keeps the earlier link in `sites`). */
    fun clearSite(sessionId: String) = synchronized(lock) {
        if (siteCtx?.sessionId != sessionId) return
        siteCtx = null
        val m = master ?: return
        meta = meta?.linkedTo(null, null, null, AndroidClock.utcEpochMs()); saveMeta()
        _state.update { it.copy(tiedTo = tiedText(meta)) }
        journal("local", "visite $sessionId terminée : synchro plus liée à un site")
        m.peers.values.filter { it.link == app.lumisite.core.sync.Link.CONNECTED }.forEach { send(it.endpointId, siteMessage()) }
    }

    private fun onSiteContext(m: MeshMsg.SiteContext) {
        meta = meta?.copy(masterName = m.masterName)?.linkedTo(m.siteId, m.siteName, m.sessionId, AndroidClock.utcEpochMs()); saveMeta()
        _state.update { it.copy(tiedTo = tiedText(meta)) }
        journal("local", "maître ${m.masterName}" + (m.siteName?.let { " · site « $it »" } ?: " · aucun site ouvert") + (m.sessionId?.let { " · visite $it" } ?: ""))
    }

    private var gpsListener: android.location.LocationListener? = null
    private var gpsOut: BufferedWriter? = null

    @android.annotation.SuppressLint("MissingPermission")
    private fun startGps(ctx: Context) {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (!granted) { _state.update { it.copy(gps = "GPS : localisation refusée") }; journal("local", "GPS : localisation refusée, aucune position enregistrée"); return }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val f = File(dir, app.lumisite.core.sync.SyncFolder.GPS)
        val out = runCatching { f.bufferedWriter().also { it.write(app.lumisite.core.sync.SyncFolder.GPS_HEADER) } }.getOrNull()
        gpsOut = out
        val l = android.location.LocationListener { loc ->
            val acc = if (loc.hasAccuracy()) loc.accuracy else null
            runCatching { synchronized(this) { out?.write("${loc.elapsedRealtimeNanos},${loc.time},${loc.provider},${loc.latitude},${loc.longitude},${acc ?: ""},${if (loc.hasAltitude()) loc.altitude else ""}," +
                "${if (Build.VERSION.SDK_INT >= 26 && loc.hasVerticalAccuracy()) loc.verticalAccuracyMeters else ""}\n") } }
            _state.update { it.copy(gps = "GPS : position reçue (${loc.provider}" + (acc?.let { a -> ", ± %.0f m".format(Locale.FRANCE, a) } ?: "") + ")") }
        }
        gpsListener = l
        val providers = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        providers.forEach { p -> runCatching { lm.requestLocationUpdates(p, 1000L, 0f, l, android.os.Looper.getMainLooper()) } }
        val txt = if (providers.isEmpty()) "GPS : localisation désactivée" else "GPS : en attente de position (" + providers.joinToString() + ")"
        _state.update { it.copy(gps = txt) }
        journal("local", txt)
    }

    private fun stopGps() {
        gpsListener?.let { l -> runCatching { (appCtx?.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager)?.removeUpdates(l) } }
        gpsListener = null
        runCatching { gpsOut?.close() }; gpsOut = null
    }

    /** Every second on the slave: samples reach the file system (survive a kill, a screen-off cut or Back). */
    private fun flushFiles() { runCatching { sensorOut?.flush() }; runCatching { gpsOut?.flush() } }

    /** Run folder to show / export: the current one, otherwise the latest kept on this phone. */
    fun lastRun(ctx: Context): File? = dir?.takeIf { _state.value.running } ?: app.lumisite.core.sync.SyncFolder.runs(File(ctx.filesDir, "synchro")).firstOrNull()

    fun lastRunTied(ctx: Context): String? = lastRun(ctx)?.let { tiedText(app.lumisite.core.sync.SyncFolder.read(it)) }

    /**
     * Exports the run (current or latest) as a ZIP in the site export format (manifest + SHA-256, synchro/<run>/),
     * copied to Documents/LumiSite/_synchro/. Blocking: call off the main thread.
     */
    fun exportRun(ctx: Context): String {
        val run = lastRun(ctx) ?: return "Aucune synchro enregistrée sur ce téléphone."
        synchronized(lock) { flushFiles() }
        val work = File(ctx.filesDir, "exports/synchro_${run.name}").apply { deleteRecursively(); mkdirs() }
        val root = app.lumisite.core.sync.SyncFolder.buildPackage(listOf(run), work, AndroidClock.utcEpochMs(), "LumiSite Android ${app.lumisite.android.BuildConfig.VERSION_NAME}")
        val problems = app.lumisite.core.export.ExportPackage.verify(root)
        check(problems.isEmpty()) { "Vérification du paquet : $problems" }
        val zip = File(ctx.filesDir, "exports/LumiSite_synchro_${safeName(_state.value.selfName.ifEmpty { deviceName(ctx) })}_${run.name}.zip")
        app.lumisite.core.export.ExportPackage.zipStored(root, zip)
        work.deleteRecursively()
        val pub = runCatching { app.lumisite.android.data.PublicStore(ctx).copy(zip, listOf("_synchro")) }.getOrNull()
        journal("local", "synchro exportée : ${zip.name} (${zip.length() / 1000} Ko)")
        return "ZIP prêt : ${zip.name} (${zip.length() / 1000} Ko)" + (pub?.let { " · copié dans ${it.displayPath}" } ?: " · copie dans Documents impossible, ZIP gardé dans l'appli")
    }

    // ---- v176d: slave sensors: detect, journal and activate (samples appended to capteurs.csv) ----

    private var sensorListener: SensorEventListener? = null
    private var sensorOut: BufferedWriter? = null

    private fun startSensors(ctx: Context) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val types = mapOf("ACCEL" to Sensor.TYPE_ACCELEROMETER, "GYRO" to Sensor.TYPE_GYROSCOPE, "MAGNETO" to Sensor.TYPE_MAGNETIC_FIELD, "BARO" to Sensor.TYPE_PRESSURE)
        val present = HashMap<String, String?>()
        val active = HashSet<String>()
        if (ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)) {
            present["MICRO"] = null
            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) active += "MICRO"
        }
        runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.firstOrNull { id -> cm.getCameraCharacteristics(id).get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) == true }?.let { present["DEPTH"] = "caméra $it, non enregistrée" }
        }
        val out = runCatching { File(dir, "capteurs.csv").bufferedWriter().also { it.write("t_mono_ns,capteur,x,y,z\n") } }.getOrNull()
        sensorOut = out
        val names = HashMap<Int, String>()
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values
                runCatching { synchronized(this) { out?.write("${e.timestamp},${names[e.sensor.type]},${v.getOrElse(0) { 0f }},${v.getOrElse(1) { 0f }},${v.getOrElse(2) { 0f }}\n") } }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorListener = l
        types.forEach { (k, t) ->
            val sensor = sm.getDefaultSensor(t) ?: return@forEach
            present[k] = sensor.name; names[t] = k
            if (sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)) active += k
        }
        val list = SensorInventory.report(present, active)
        _state.update { it.copy(sensors = list) }
        journal("local", "capteurs : " + list.joinToString { it.text })
    }

    private fun stopSensors(ctx: Context?) {
        val l = sensorListener ?: return
        sensorListener = null
        (ctx?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(l)
        runCatching { sensorOut?.close() }; sensorOut = null
    }

    fun startCapture() = synchronized(lock) {
        val m = master ?: return
        val capId = Ids.new("cap")
        journal("local", "captures démarrées ($capId)")
        m.start(capId, now()).forEach { (id, msg) -> send(id, msg) }
        publish()
    }

    fun stopCapture() = synchronized(lock) {
        val m = master ?: return
        journal("local", "captures arrêtées")
        m.stop(now()).forEach { (id, msg) -> send(id, msg) }
        publish()
    }

    fun stop() {
        synchronized(lock) {
            if (_state.value.running) journal("local", "synchro arrêtée")
            // Master: end the slaves' captures first, so they send their media instead of recording on after the link drops.
            val stops = master?.stop(now()).orEmpty()
            stops.forEach { (id, msg) -> send(id, msg) }
            stopSensors(appCtx)
            stopGps()
            recorder?.let { r -> recorder = null; r.stop(); journal("local", "interruption : capture locale arrêtée avec la synchro (${r.file.name} gardé sur l'appareil)") }
            loop?.cancel(); loop = null
            val c = client
            if (c != null && stops.isNotEmpty()) scope.launch { delay(1500); c.stopAllEndpoints() }
            client?.apply { stopAdvertising(); stopDiscovery(); if (stops.isEmpty()) stopAllEndpoints() }
            client = null; master = null; slave = null; masterEndpoint = null
            appCtx?.let { SyncService.stop(it) }
            val keep = _state.value
            _state.value = UiState(runDir = keep.runDir, tiedTo = keep.tiedTo)
        }
    }

    // ---- slave capture: microphone WAV, monotonic start/end, sent to the master as a FILE payload ----

    private fun startRecording(ctx: Context, captureId: String, masterId: String) {
        recorder?.let { r ->
            if (r.captureId == captureId) return
            // The capture changed while this slave was away: close and send the old one, never merge two periods.
            journal("local", "interruption : capture ${r.captureId} close, nouvelle capture $captureId")
            stopRecording(masterId)
        }
        val f = File(dir, "capture_${safeName(captureId)}.wav")
        recorder = SlaveRecorder(f, captureId).also { it.start() }
        if (recorder?.error != null) journal("local", "interruption : micro indisponible (${recorder?.error})")
    }

    private fun stopRecording(masterId: String) {
        val r = recorder ?: return
        recorder = null
        r.stop()
        val f = r.file
        if (!f.exists() || f.length() <= 44) { journal("local", "aucun audio enregistré"); return }
        val payload = Payload.fromFile(f)
        val s = slave ?: return
        send(masterId, MeshMsg.Media(payload.id, f.name, r.tStartNs, r.tEndNs, f.length(), sha256(f), s.timeBaseId))
        client?.sendPayload(masterId, payload)
        journal("local", "média envoyé : ${f.name} (${f.length()} o)")
    }

    private fun sha256(f: File): String = MessageDigest.getInstance("SHA-256").let { md ->
        f.inputStream().use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    private class SlaveRecorder(val file: File, val captureId: String) {
        var tStartNs = 0L; var tEndNs = 0L; var error: String? = null
        @Volatile private var on = false
        private var thread: Thread? = null
        private val rate = 16_000

        @SuppressLint("MissingPermission")
        fun start() {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate)) }.getOrElse { error = it.message; return }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { error = "AudioRecord non initialisé"; rec.release(); return }
            on = true
            thread = Thread {
                RandomAccessFile(file, "rw").use { out ->
                    out.setLength(0); out.write(ByteArray(44))
                    val buf = ByteArray(rate / 5)
                    rec.startRecording(); tStartNs = AndroidClock.monotonicNs()
                    while (on) { val n = rec.read(buf, 0, buf.size); if (n > 0) out.write(buf, 0, n) }
                    tEndNs = AndroidClock.monotonicNs()
                    rec.stop(); rec.release()
                    val data = out.length() - 44
                    out.seek(0); out.write(wavHeader(data, rate))
                }
            }.apply { start() }
        }

        fun stop() { on = false; thread?.join(2000) }

        private fun wavHeader(data: Long, rate: Int): ByteArray = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt((36 + data).toInt()); put("WAVE".toByteArray()); put("fmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.toInt())
        }.array()
    }
}
