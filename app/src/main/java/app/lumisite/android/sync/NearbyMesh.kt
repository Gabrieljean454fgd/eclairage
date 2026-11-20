package app.lumisite.android.sync

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.provider.Settings
import app.lumisite.android.BuildConfig
import app.lumisite.android.data.AndroidClock
import app.lumisite.android.data.DeviceInfo
import app.lumisite.android.data.DeviceName
import app.lumisite.android.data.DiagLog
import app.lumisite.android.data.Ids
import app.lumisite.core.sync.Capture
import app.lumisite.core.sync.MasterMesh
import app.lumisite.core.sync.MeshLog
import app.lumisite.core.sync.MeshMsg
import app.lumisite.core.sync.SlaveMesh
import app.lumisite.core.sync.SyncDisplay
import app.lumisite.core.sync.SyncRole2
import app.lumisite.core.sync.HeardChirp
import app.lumisite.core.sync.SensorInventory
import app.lumisite.core.sync.SensorState
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
        /** Nom du maître côté esclave (endpointName Nearby), null quand aucun maître. */
        val slaveMasterName: String? = null,
        val slaveCapture: Capture = Capture.IDLE,
        val slaveLastPingNs: Long = 0,
        val log: List<String> = emptyList(),
        /** v176d: sensors detected / activated on this phone (slave). */
        val sensors: List<SensorState> = emptyList(),
        /** v176d: acoustic round in progress or last result on this phone. */
        val round: String? = null,
        val roundBusy: Boolean = false,
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

    private fun journal(endpoint: String, what: String, screen: String? = null) {
        val line = "{\"t_mono_ns\":${now()},\"utc_ms\":${AndroidClock.utcEpochMs()},\"appareil\":${q(endpoint)},\"quoi\":${q(what)}}\n"
        runCatching { dir?.let { File(it, "synchro.ndjson").appendText(line) } }
        DiagLog.i("SYNCHRO", "$endpoint : $what")
        val label = master?.peers?.get(endpoint)?.name ?: endpoint
        val hms = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(AndroidClock.utcEpochMs()))
        _state.update { it.copy(log = (it.log + "$hms · $label · ${screen ?: what}").takeLast(30)) }
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun logs(l: List<MeshLog>) = l.forEach { journal(it.endpointId, it.what, it.screen) }

    private fun publish() {
        val m = master
        _state.update {
            it.copy(master = m?.summary(), peers = m?.peers?.values?.sortedBy { p -> p.name } ?: emptyList(),
                capturing = m?.captureId != null, slaveCapture = slave?.capture ?: Capture.IDLE, slaveLastPingNs = slave?.lastPingNs ?: 0)
        }
    }

    fun start(ctx: Context, role: SyncRole2) {
        stop()
        appCtx = ctx.applicationContext
        val c = Nearby.getConnectionsClient(ctx.applicationContext)
        client = c
        dir = File(ctx.filesDir, "synchro/" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())).apply { mkdirs() }
        // build 199 point 1a: readable name (Réglages > Nom de ce téléphone, sinon device_name système, sinon modèle),
        // same name as Nearby endpointName and in the Hello message.
        val name = DeviceName.label(ctx)
        _state.update { UiState(role = role, running = true, radio = "Démarrage…") }
        journal("local", "rôle : " + if (role == SyncRole2.SAISIE_MAITRE) "Saisie (maître)" else "Captation-synchro (esclave)")
        // build 199 point 1f: foreground service so HyperOS does not kill the synchro with the screen off.
        appCtx?.let(SynchroService::start)
        if (role == SyncRole2.SAISIE_MAITRE) {
            master = MasterMesh(DeviceInfo.deviceId(ctx))
            injectSimulatedSlave(ctx)
            c.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
                .addOnSuccessListener { radio("Recherche d'esclaves (annonce active)") }
                .addOnFailureListener { radio("Annonce impossible : ${it.message}") }
            loop = scope.launch {
                while (isActive) {
                    synchronized(lock) {
                        val m = master ?: return@launch
                        val t = now()
                        m.pings(t).forEach { (id, p) -> send(id, p) }
                        logs(m.tick(t))
                        publish()
                    }
                    delay(MasterMesh.PING_PERIOD_NS / 1_000_000)
                }
            }
        } else {
            slave = SlaveMesh(DeviceInfo.deviceId(ctx), name, Ids.new("tb"))
            startSensors(ctx)
            discover(c)
            loop = scope.launch {
                while (isActive) {
                    synchronized(lock) {
                        val s = slave ?: return@launch
                        if (s.masterSilent(now())) {
                            journal("local", "maître perdu (aucun ping depuis ${MasterMesh.LOST_AFTER_NS / 1_000_000_000} s) : reconnexion automatique")
                            masterEndpoint?.let { client?.disconnectFromEndpoint(it) }
                            s.onDisconnected(); masterEndpoint = null
                            _state.update { it.copy(slaveMasterName = null) }
                            client?.let { discover(it) }
                        }
                    }
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
            if (slave != null) _state.update { it.copy(slaveMasterName = info.endpointName) }
            client?.acceptConnection(id, payloads)
            publish()
        }

        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            val ok = r.status.isSuccess
            synchronized(lock) {
                master?.let { logs(if (ok) it.onConnected(id, now()) else it.onDisconnected(id, now())) }
                slave?.let { s ->
                    if (ok) { masterEndpoint = id; s.onConnected(); client?.stopDiscovery(); send(id, s.hello()); send(id, MeshMsg.Sensors(_state.value.sensors))
                        journal(id, "connexion : connecté") }
                    else { journal(id, "connexion refusée : ${r.status.statusMessage}"); _state.update { it.copy(slaveMasterName = null) } }
                }
            }
            publish()
        }

        override fun onDisconnected(id: String) {
            synchronized(lock) {
                master?.let { logs(it.onDisconnected(id, now())) }
                slave?.let { s ->
                    s.onDisconnected(); masterEndpoint = null
                    _state.update { it.copy(slaveMasterName = null) }
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
                            if (m is MeshMsg.Media) pendingMedia[m.payloadId] = m
                        }
                        slave?.let { s ->
                            when (m) {
                                is MeshMsg.StartCapture -> appCtx?.let { startRecording(it, m.captureId, id) }
                                is MeshMsg.StopCapture -> stopRecording(id)
                                is MeshMsg.SyncRound -> slaveRound(id, m)
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
        if (synchronized(lock) { id in simulatedPeers }) { simReply(id, m); return }
        client?.sendPayload(id, Payload.fromBytes(MeshMsg.encode(m)))
    }

    // ---- build 199 point 1g (debug/CI only): a fake slave injected into the master ----
    // `adb shell settings put global lumisite_simuler_esclave "Xiaomi 13T Pro"` then start the master role:
    // the peer gets a Nearby connection, a Hello and automatic answers (pongs on the same monotonic clock,
    // so it aligns like a real slave). No radio involved; cleared by stop(). Never in release builds.

    private data class SimPeer(val deviceId: String, val timeBaseId: String, val hex: String)
    private val simulatedPeers = HashMap<String, SimPeer>()

    private fun injectSimulatedSlave(ctx: Context) {
        if (!BuildConfig.DEBUG) return
        val base = runCatching { Settings.Global.getString(ctx.contentResolver, "lumisite_simuler_esclave") }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val m = master ?: return
        val tag = base.filter { it.isLetterOrDigit() }.take(8).lowercase().ifEmpty { "pair" }
        val endpoint = "sim-$tag"
        val deviceId = "dev-$endpoint"
        val tb = "tb-$endpoint"
        val name = SyncDisplay.deviceLabel(null, base, "", "", endpoint)
        synchronized(lock) {
            simulatedPeers[endpoint] = SimPeer(deviceId, tb, "%04X".format(app.lumisite.core.sync.IdFrame.idOf(deviceId)))
            val t = now()
            logs(m.onConnecting(endpoint, name, t))
            logs(m.onConnected(endpoint, t))
            val (l, reply) = m.onMessage(endpoint, MeshMsg.Hello(deviceId, name, SyncRole2.CAPTATION_ESCLAVE, tb), t)
            logs(l); reply?.let { send(endpoint, it) }
            journal(endpoint, "esclave simulé (debug) : $name")
            publish()
        }
    }

    /** The simulated slave answers on this device's own monotonic clock: pongs give ~0 offset, fast alignment. */
    private fun simReply(id: String, m: MeshMsg) {
        val sim = synchronized(lock) { simulatedPeers[id] } ?: return
        val mm = master ?: return
        synchronized(lock) {
            val t = now()
            val reply = when (m) {
                is MeshMsg.Ping -> MeshMsg.Pong(m.seq, m.tMasterTxNs, t, t)
                is MeshMsg.StartCapture -> MeshMsg.Status(Capture.CAPTURING, t, sim.timeBaseId)
                is MeshMsg.StopCapture -> MeshMsg.Status(Capture.STOPPED, t, sim.timeBaseId)
                is MeshMsg.SyncRound -> MeshMsg.SyncResult(m.roundId, sim.hex, emptyList(), sim.timeBaseId)
                else -> null
            } ?: return
            val (l, _) = mm.onMessage(id, reply, t)
            logs(l)
            publish()
        }
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
            master?.let { logs(it.onOwnRound(roundId, "%04X".format(ownId(ctx)), heard(r), now())) }
            _state.update { it.copy(roundBusy = false, round = "Maître : ${heard(r).size} chirp(s) identifié(s). Résultat par téléphone ci-dessous.") }
            publish()
        }
        return r
    }

    private fun slaveRound(masterId: String, m: MeshMsg.SyncRound) {
        val ctx = appCtx ?: return
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
                _state.update { it.copy(roundBusy = false, round = "Dernier top : ${res.heard.size} chirp(s) entendu(s)" + (res.error?.let { e -> " · échec : $e" } ?: "")) }
            }
        }
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
            recorder?.let { r -> recorder = null; r.stop(); journal("local", "interruption : capture locale arrêtée avec la synchro (${r.file.name} gardé sur l'appareil)") }
            loop?.cancel(); loop = null
            val c = client
            if (c != null && stops.isNotEmpty()) scope.launch { delay(1500); c.stopAllEndpoints() }
            client?.apply { stopAdvertising(); stopDiscovery(); if (stops.isEmpty()) stopAllEndpoints() }
            client = null; master = null; slave = null; masterEndpoint = null; simulatedPeers.clear()
            _state.update { UiState() }
            // build 199 point 1f: the foreground service follows the synchro (also stopped by its « Arrêter » action).
            appCtx?.let(SynchroService::stop)
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
