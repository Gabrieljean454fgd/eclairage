package app.lumisite.android

import app.lumisite.core.model.ControlPointRemoved
import app.lumisite.core.model.ControlPointEvent
import app.lumisite.core.model.CalibrationMethod
import app.lumisite.core.geo.Similarity2D
import app.lumisite.core.geo.Angles
import app.lumisite.core.geo.Vec2
import app.lumisite.core.position.SolverSettings
import app.lumisite.core.position.Solution
import app.lumisite.core.position.GnssQuality
import app.lumisite.core.position.GnssObservation
import app.lumisite.core.position.GnssClassifier
import app.lumisite.core.position.ControlPoint
import app.lumisite.core.position.CalibrationSolver
import android.content.Context
import app.lumisite.android.capture.ArTracker
import app.lumisite.android.capture.GpsRecorder
import app.lumisite.android.capture.SensorRecorder
import app.lumisite.android.data.AndroidClock
import app.lumisite.android.data.CatalogStore
import app.lumisite.core.catalog.CatalogEntry
import app.lumisite.core.catalog.EquipmentCatalog
import app.lumisite.core.compute.SurveyPositions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.lumisite.android.data.DeviceInfo
import app.lumisite.android.data.Ids
import app.lumisite.android.data.Repository
import app.lumisite.core.compute.Calibrator
import app.lumisite.core.compute.CorrectedPoint
import app.lumisite.core.compute.PoseGeometry
import app.lumisite.core.compute.Relocalizer
import app.lumisite.core.compute.TrajectoryCorrector
import app.lumisite.core.geo.CrsInfo
import app.lumisite.core.geo.Enu
import app.lumisite.core.geo.LocalTangentPlane
import app.lumisite.core.journal.SessionJournal
import app.lumisite.core.model.StreamStateEvent
import app.lumisite.android.data.DiagLog
import app.lumisite.android.data.BootInfo
import app.lumisite.core.export.sha256
import app.lumisite.core.survey.Shapes
import app.lumisite.core.model.Attachment
import app.lumisite.core.model.ShapeStyle
import app.lumisite.core.model.ShapeEvent
import app.lumisite.core.export.GeoJsonSeqWriter
import java.io.File
import app.lumisite.core.journal.SessionReader
import app.lumisite.core.model.AudioDetection
import app.lumisite.core.model.CalibrationEvent
import app.lumisite.core.model.DxfHint
import app.lumisite.core.model.GpsFix
import app.lumisite.core.model.GpsStatusSample
import app.lumisite.core.model.LocalObservation
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.ReferencePoint
import app.lumisite.core.model.RelocalizationEvent
import app.lumisite.core.model.SessionEvent
import app.lumisite.core.model.SessionHeader
import app.lumisite.core.model.SessionStarted
import app.lumisite.core.model.SessionStopped
import app.lumisite.core.model.Site
import app.lumisite.core.model.SurveyEvent
import app.lumisite.core.model.SyncEvent
import app.lumisite.core.model.SyncMethod
import app.lumisite.core.model.SyncRole
import app.lumisite.core.model.TimeBaseAdded
import app.lumisite.core.model.TrackingState
import app.lumisite.core.model.TrackingStateChanged
import app.lumisite.core.time.StampFactory
import app.lumisite.core.time.TimeBase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of the live session for the UI. */
data class LiveState(
    val tracking: TrackingState = TrackingState.UNAVAILABLE,
    val trackingReason: String? = null,
    val arStatus: String = "",
    val lastPose: PoseSample? = null,
    val lastGps: GpsFix? = null,
    val gpsStatus: Map<String, String> = emptyMap(),
    val poseCount: Int = 0,
    val imuCount: Long = 0,
    val events: List<SessionEvent> = emptyList(),
    /** Derived trajectory (with calibration + relocalizations) split into continuous TRACKING segments. */
    val trajectory: List<List<CorrectedPoint>> = emptyList(),
    /** Same raw poses with the first calibration only (no relocalization), for comparison. */
    val uncorrected: List<List<CorrectedPoint>> = emptyList(),
    val currentSite: Enu? = null,
    val distanceSinceFixM: Double? = null,
    val pendingCalibrationA: LocalObservation? = null,
    val message: String? = null,
    /** Last valid AR position (TRACKING + calibrated), its wall time and the camera heading then (stage 2.8). */
    val lastArSite: Enu? = null,
    val lastArValidMs: Long? = null,
    val lastHeadingDeg: Double? = null,
    /** Marker left on the plan by Calibrer / Capturer A (stage 2.9). */
    val calibMark: CalibMark? = null,
    /** D7 classification of the GNSS and why (shown, journaled; never injected into the AR position). */
    val gnssQuality: GnssQuality = GnssQuality.INUTILISABLE,
    val gnssReasons: List<String> = emptyList(),
    /** D3–D5: last solution of the calibration / relocalization tables (residual and status of every row). */
    val controlSolution: Solution? = null,
)

/** One row of Session > Calibrer / Recaler > tableau. */
data class ControlRow(val n: Int, val e: ControlPointEvent, val residualM: Double?, val status: String)

/** Debug/CI only: synthetic AR poses (Maestro launchApp argument simuler_pose_ar = true; the emulator has no ARCore). */
object DebugFlags { @Volatile var simulatedPoses = false }

/** Phone position (site frame) and camera direction at the moment of a calibration. */
data class CalibMark(val site: Enu, val headingDeg: Double?, val label: String)

/**
 * Orchestrates capture for one field session: journal (append-only, local), AR poses, IMU, GPS, and the
 * user events (calibration, relocalization, survey, sync). Business rules live in :core.
 */
const val NO_POSITION = "Suivi ou calibration manquant : touchez la carte ou cochez la position manuelle."

class SessionController(private val ctx: Context, private val repo: Repository, val site: Site, val sessionId: String, resume: Boolean) {
    val ltp = LocalTangentPlane(site.origin)
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    val header: SessionHeader
    private val journal: SessionJournal
    private val stamps: StampFactory
    private val poses = ArrayList<PoseSample>()
    private var lastPoseMono = 0L
    private var lastTracking: TrackingState? = null
    private var imuCount = 0L

    val sensors = SensorRecorder(ctx, { stamps }) { s ->
        if (closed) return@SensorRecorder
        journal.imu(s); imuCount++
        if (imuCount % 50 == 0L) _state.update { it.copy(imuCount = imuCount) }
    }
    private val gps = GpsRecorder(ctx, { stamps }, ::onGps, ::onGpsStatus)
    val ar = ArTracker(ctx, { stamps }, ::onArFrame).also { it.onAnchors = { m -> onAnchors(m) } }
    val gnss = GnssClassifier()
    val solverSettings = SolverSettings()
    private var lastGnssFix: GpsFix? = null
    private var lastPosLogMs = 0L
    private val tick = android.os.Handler(android.os.Looper.getMainLooper())
    private val gnssTick = object : Runnable { override fun run() { if (closed) return; updateGnss(); tick.postDelayed(this, 1000L) } }
    /** Stage 1.2: finished segments and attachments are copied to Documents/LumiSite/<site>/<session>/ as they come. */
    private val mirror = repo.mirror(site, sessionId) { msg, e -> DiagLog.e("COPIE", msg, e) }
    val mirrorDir get() = mirror.displayDir

    init {
        val tbRef = TimeBase(Ids.new("tb"), DeviceInfo.deviceId(ctx), TimeBase.ANDROID_ELAPSED_REALTIME, AndroidClock.utcEpochMs(), AndroidClock.monotonicNs(),
            notes = "SystemClock.elapsedRealtimeNanos(); UTC from System.currentTimeMillis() captured at the same instant",
            bootId = BootInfo.bootId(), bootCount = BootInfo.bootCount(ctx))
        stamps = StampFactory(AndroidClock, tbRef)
        val dir = repo.sessionDir(sessionId)
        if (!resume) {
            val arSupport = runCatching { ArTracker.availability(ctx).name }.getOrDefault("UNKNOWN")
            header = SessionHeader(
                id = sessionId, siteId = site.id, name = "Session ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date())}",
                createdUtcMs = AndroidClock.utcEpochMs(), device = DeviceInfo.describe(ctx, arSupport), timeBases = listOf(tbRef),
                localFrame = CrsInfo("AR_SESSION", "ARCORE_WORLD", "ARCore session world frame: right-handed, +Y up (gravity), metres; origin = device pose at AR session start. Horizontal plane mapping: (x, -z)."),
            )
            journal = SessionJournal(dir, header, onSegmentClosed = { mirror.enqueue(it) })
            val started = SessionStarted(Ids.new("ev"), stamps.now())
            journal.event(started)
            _state.update { it.copy(events = listOf(started)) }
        } else {
            val data = SessionReader.read(dir, includeImu = false)
            header = data.header
            poses += data.poses
            journal = SessionJournal(dir, onSegmentClosed = { mirror.enqueue(it) })
            val previousBoot = (data.header.timeBases + data.events.filterIsInstance<TimeBaseAdded>().map { it.timeBase }).lastOrNull()?.bootId
            val rebooted = previousBoot != null && tbRef.bootId != null && previousBoot != tbRef.bootId
            val resumed = TimeBaseAdded(Ids.new("ev"), stamps.now(), tbRef, if (rebooted) "session resumed after PHONE_REBOOT" else "session resumed")
            journal.event(resumed)
            DiagLog.i("SESSION", "Reprise de $sessionId" + if (rebooted) " après redémarrage du téléphone (nouvelle base de temps)" else "")
            // The AR session frame of a resumed session is new: previous transforms do not apply to it.
            _state.update { it.copy(events = data.events + resumed, poseCount = data.poses.size, message = "Session reprise : nouveau repère AR, recalibrez avant de poursuivre.") }
            recompute()
        }
        // Reserve R2: segments left open by an abrupt stop were closed by SessionJournal; say so in the session journal.
        journal.repaired.forEach { r ->
            val e = StreamStateEvent(Ids.new("ev"), stamps.now(), r.stream.removeSuffix(".ndjson"), "REPAIRED", "ABRUPT_STOP",
                "${r.file.name} fermé à la reprise (${r.bytes} octets${if (r.truncatedTail) ", dernière ligne tronquée conservée et signalée" else ""})")
            journal.event(e); _state.update { it.copy(events = it.events + e) }
            DiagLog.i("FLUX", "Segment réparé après arrêt brutal : ${r.file.name}")
        }
        mirror.syncAll()
        DiagLog.i("GNSS", "Seuils D7 : ${gnss.thresholds} ; solveur D3-D5 : $solverSettings")
    }

    private var paused = false

    private fun streamState(streams: List<String>, state: String, reason: String) {
        if (closed) return
        val evs = streams.map { StreamStateEvent(Ids.new("ev"), stamps.now(), it, state, reason) }
        evs.forEach(journal::event)
        _state.update { it.copy(events = it.events + evs) }
        DiagLog.i("FLUX", "${streams.joinToString()} : $state ($reason)")
    }

    /** Closes the open segments and copies everything final to Documents/LumiSite (export, pause, end of session). */
    fun mirrorNow() { if (!closed) runCatching { journal.rotateAll() }; mirror.syncAll(); mirror.drain(); DiagLog.mirrorNow() }

    // ------------------------------------------------------------------ capture lifecycle

    fun startCapture(hasLocation: Boolean) {
        if (paused) { paused = false; streamState(listOf("imu", "gps", "arcore"), "RESUMED", "APP_RESUMED") }
        sensors.start()
        tick.removeCallbacks(gnssTick); tick.post(gnssTick)
        if (hasLocation) gps.start() else onGpsStatus(GpsStatusSample(stamps.now(), "gps", "NO_PERMISSION"))
    }

    @Volatile var closed = false; private set

    fun pauseCapture() {
        if (closed) return
        runCatching { sensors.stop() }; runCatching { gps.stop() }; ar.pause()
        tick.removeCallbacks(gnssTick)
        logTracking(TrackingState.STOPPED, "APP_PAUSED")
        if (!paused) { paused = true; streamState(listOf("imu", "gps", "arcore"), "STOPPED", "APP_PAUSED") }
        journal.flushAll()
        runCatching { journal.rotateAll() }
        mirror.syncAll()
        DiagLog.memory("pause de session"); DiagLog.mirrorNow()
    }

    /**
     * End of session. The caller must have paused the GLSurfaceView first ([ArTracker.rendering] is cleared here
     * too), so ARCore is closed while no frame is being drawn. Idempotent and never throws.
     */
    fun close(reason: String = "user") {
        if (closed) return
        ar.rendering = false
        tick.removeCallbacks(gnssTick)
        runCatching { pauseCapture() }
        closed = true
        ar.close()
        runCatching { journal.event(SessionStopped(Ids.new("ev"), stamps.now(), reason)) }
        runCatching { journal.close() }
        runCatching { featuresWriter?.close() }
        mirror.close()
        DiagLog.i("SESSION", "Fin de la session $sessionId ($reason), copie dans ${mirror.displayDir}")
        DiagLog.mirrorNow()
    }

    fun setArStatus(text: String) = _state.update { it.copy(arStatus = text) }

    fun notify(msg: String) = fail(msg)

    fun markArUnavailable(reason: String) = logTracking(TrackingState.UNAVAILABLE, reason)

    private fun onArFrame(pose: PoseSample?, state: TrackingState, reason: String?) {
        if (closed) return
        // Debug/CI (simuler_pose_ar): synthetic poses replace the real ARCore frames, also when ARCore runs without tracking.
        if (simThread != null && pose?.source != "SIMULATED_DEBUG") return
        logTracking(state, reason)
        if (pose != null) {
            // 10 Hz pose recording is enough for walking trajectories; every frame is still used by ARCore.
            if (pose.t.monotonicNs - lastPoseMono >= 100_000_000L) {
                lastPoseMono = pose.t.monotonicNs
                journal.pose(pose)
                synchronized(poses) { poses += pose }
                appendLive(pose)
            }
            _state.update { it.copy(lastPose = pose) }
        }
    }

    private fun logTracking(state: TrackingState, reason: String?) {
        if (state != lastTracking) {
            lastTracking = state
            val ev = TrackingStateChanged(Ids.new("ev"), stamps.now(), state, reason)
            journal.event(ev)
            if (state != TrackingState.TRACKING) {
                // explicit raw marker in the pose stream too (no pose values are valid while not tracking)
                _state.value.lastPose?.let { journal.pose(it.copy(t = stamps.now(), tracking = state, failureReason = reason)) }
            }
            _state.update { it.copy(tracking = state, trackingReason = reason, events = it.events + ev) }
        } else if (reason != _state.value.trackingReason) {
            _state.update { it.copy(trackingReason = reason) }
        }
    }

    private fun onGps(f: GpsFix) { if (closed) return; journal.gps(f); if (f.provider == "gps") lastGnssFix = f; _state.update { it.copy(lastGps = f) } }

    /** D7, once per second: satellites used, C/N0 of the 4 best, accuracy, age of the last GNSS fix; changes are journaled. */
    private fun updateGnss() {
        val f = lastGnssFix
        val age = f?.let { (AndroidClock.monotonicNs() - (it.t.sensorNs ?: it.t.monotonicNs)) / 1_000_000 }
        val obs = GnssObservation(gps.satUsed, gps.cn0Used, f?.horizontalAccuracyM?.toDouble(), age)
        if (gnss.update(System.currentTimeMillis(), obs)) {
            val why = gnss.reasons.joinToString().ifEmpty { "critères D7 respectés" }
            DiagLog.i("GNSS", "GNSS ${gnss.quality.label} ($why) ; satellites=${obs.satellitesUsed} C/N0=${obs.cn0UsedDbHz.sortedDescending().take(4)} précision=${obs.accuracyM} âge=${obs.fixAgeMs} ms")
            val ev = StreamStateEvent(Ids.new("ev"), stamps.now(), "gnss", gnss.quality.name, why)
            journal.event(ev); _state.update { it.copy(events = it.events + ev) }
        }
        _state.update { it.copy(gnssQuality = gnss.quality, gnssReasons = gnss.reasons) }
    }
    private fun onGpsStatus(s: GpsStatusSample) { if (closed) return; journal.gpsStatus(s); _state.update { it.copy(gpsStatus = it.gpsStatus + (s.provider to s.status)) } }

    // ------------------------------------------------------------------ derived display

    private fun activeSegment() = TrajectoryCorrector.activeAt(TrajectoryCorrector.timeline(_state.value.events), Long.MAX_VALUE)

    private fun appendLive(p: PoseSample) {
        val seg = activeSegment()
        val cur = seg?.transform?.apply(PoseGeometry.plane(p))?.let { Enu(it.x, it.y, PoseGeometry.height(p)) }
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPosLogMs >= 1000L) {
            // D2: source, active transform and segment number, once per second (ARCore plane = (x, -z) in metres, rotation in degrees).
            lastPosLogMs = nowMs
            val tl = TrajectoryCorrector.timeline(_state.value.events)
            val src = when { seg == null -> "non calibrée (GNSS ${gnss.quality.label}, indicatif)"; p.tracking == TrackingState.TRACKING -> "AR+calibrage"; else -> "AR figée (${p.tracking})" }
            DiagLog.i("POSITION", "source=$src segment=${seg?.let { s -> tl.indexOfLast { it.eventId == s.eventId } + 1 } ?: 0}/${tl.size} evt=${seg?.eventId} " +
                (seg?.transform?.let { t -> "rot=" + "%.2f".format(java.util.Locale.ROOT, Math.toDegrees(t.rotationRad)) + "° t=(" + "%.2f, %.2f".format(java.util.Locale.ROOT, t.tx, t.ty) + ") m " } ?: "") +
                "plan_ar=(" + "%.2f, %.2f".format(java.util.Locale.ROOT, p.p[0], -p.p[2]) + ") m" + (cur?.let { " site=(" + "%.2f, %.2f".format(java.util.Locale.ROOT, it.e, it.n) + ") m" } ?: ""))
        }
        _state.update { st ->
            var traj = st.trajectory
            if (cur != null && seg != null) {
                val pt = CorrectedPoint(p.t.monotonicNs, p.t.utcEpochMs, p.t.timeBaseId, cur, ltp.toLatLon(cur.copy(u = 0.0)), p.tracking, seg.eventId)
                val last = traj.lastOrNull()?.lastOrNull()
                val gap = last == null || p.t.monotonicNs - last.monotonicNs > 1_000_000_000L
                traj = if (gap) traj + listOf(listOf(pt)) else traj.dropLast(1) + listOf(traj.last() + pt)
            }
            val valid = cur != null && seg != null && p.tracking == TrackingState.TRACKING
            st.copy(poseCount = st.poseCount + 1, imuCount = imuCount, currentSite = cur, distanceSinceFixM = distanceSinceFix(traj, seg?.fromNs),
                lastArSite = if (valid) cur else st.lastArSite, lastArValidMs = if (valid) System.currentTimeMillis() else st.lastArValidMs,
                lastHeadingDeg = if (valid) headingOf(p, seg!!) else st.lastHeadingDeg)
        }
    }

    private fun headingOf(p: PoseSample, seg: TrajectoryCorrector.Segment): Double {
        val enuRad = PoseGeometry.forwardPlaneAngle(p) + seg.transform.rotationRad
        return ((90.0 - Math.toDegrees(enuRad)) % 360.0 + 360.0) % 360.0
    }

    fun isCalibrated() = activeSegment() != null

    /** After a calibration: where the phone is on the plan and where the camera looks (from the new transform). */
    private fun markCalibration(pose: PoseSample, label: String) {
        val seg = activeSegment() ?: return
        val xy = seg.transform.apply(PoseGeometry.plane(pose))
        val site = Enu(xy.x, xy.y, 0.0)
        _state.update { it.copy(calibMark = CalibMark(site, headingOf(pose, seg), label), lastArSite = site, lastArValidMs = System.currentTimeMillis(), lastHeadingDeg = headingOf(pose, seg)) }
    }

    private fun distanceSinceFix(traj: List<List<CorrectedPoint>>, fromNs: Long?): Double? {
        fromNs ?: return null
        return traj.sumOf { s -> TrajectoryCorrector.pathLengthM(s.filter { it.monotonicNs >= fromNs }) }
    }

    private fun split(points: List<CorrectedPoint>): List<List<CorrectedPoint>> {
        val out = ArrayList<MutableList<CorrectedPoint>>()
        for (p in points) {
            val last = out.lastOrNull()?.lastOrNull()
            if (last == null || p.monotonicNs - last.monotonicNs > 1_000_000_000L) out += mutableListOf(p) else out.last() += p
        }
        return out
    }

    /** Full recomputation from raw poses + events (after a calibration/relocalization). */
    private fun recompute() {
        val snapshot = synchronized(poses) { ArrayList(poses) }
        val events = _state.value.events
        val traj = TrajectoryCorrector.correct(snapshot, events, ltp)
        val firstCal = events.filterIsInstance<CalibrationEvent>().lastOrNull()
        val uncorrected = if (firstCal != null) TrajectoryCorrector.correct(snapshot, listOf(firstCal), ltp) else emptyList()
        val seg = activeSegment()
        val splitTraj = split(traj)
        _state.update { it.copy(trajectory = splitTraj, uncorrected = split(uncorrected), currentSite = traj.lastOrNull()?.site, distanceSinceFixM = distanceSinceFix(splitTraj, seg?.fromNs)) }
    }

    // Opened on the first record only: closing a session must never create an empty features.geojsonl.
    private var featuresWriter: GeoJsonSeqWriter? = null
    private val features: GeoJsonSeqWriter
        get() = featuresWriter ?: GeoJsonSeqWriter(File(repo.sessionDir(sessionId), GeoJsonSeqWriter.FILE), ltp, header.device.id, sessionId).also { featuresWriter = it }

    private fun record(e0: SessionEvent, msg: String? = null) {
        // Format 0.2: zones are written as ZoneEvent, also when edited from the generic shape tools.
        val e = if (e0 is ShapeEvent && e0.kind == "zone") app.lumisite.core.model.ZoneEvent.of(e0) else e0
        if (closed) return
        journal.event(e)
        // GeoJSONSeq written live (fsync per object) so the exchange file survives an abrupt stop.
        if (e is SurveyEvent || e is ShapeEvent || e is app.lumisite.core.model.ZoneEvent) runCatching {
            if (e is SurveyEvent) features.append(e, SurveyPositions.resolve(e, TrajectoryCorrector.timeline(_state.value.events + e)).site)
            if (e is ShapeEvent) features.appendShape(e)
            if (e is app.lumisite.core.model.ZoneEvent) features.appendShape(e.asShape())
        }.onFailure { _state.update { s -> s.copy(message = "Écriture GeoJSON impossible : ${it.message}") } }
        _state.update { it.copy(events = it.events + e, message = msg) }
        if (e is CalibrationEvent || e is RelocalizationEvent) recompute()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    private fun fail(msg: String) = _state.update { it.copy(message = msg) }

    private fun poseUnavailable(what: String): String {
        val st = _state.value
        return "$what impossible : pose AR indisponible (${st.tracking}${st.trackingReason?.let { " : $it" } ?: ""}) : éclairez la scène ou bougez lentement."
    }

    private fun trackedPose(): PoseSample? = _state.value.lastPose?.takeIf { _state.value.tracking == TrackingState.TRACKING }

    // ------------------------------------------------------------------ user actions

    fun calibrateOnePoint(ref: ReferencePoint, headingDeg: Double, headingSource: String, uncertaintyDeg: Double?) {
        val pose = trackedPose() ?: return fail("Calibration impossible : suivi AR non actif.")
        val e = Calibrator.onePointHeading(Ids.new("cal"), stamps.now(), LocalObservation(pose, ref), headingDeg, headingSource, uncertaintyDeg)
        record(e, "Calibration enregistrée (1 point + cap %.0f°).".format(headingDeg))
        markCalibration(pose, "Calibration (1 point + cap)")
    }

    fun captureCalibrationA(ref: ReferencePoint) {
        val pose = trackedPose() ?: return fail("Point A impossible : suivi AR non actif.")
        _state.update { it.copy(pendingCalibrationA = LocalObservation(pose, ref), message = "Point A capturé. Marchez jusqu'au point B.", calibMark = CalibMark(ref.site, null, "Calibration : point A")) }
    }

    fun calibrateTwoPoints(refB: ReferencePoint, fixScale: Boolean, knownDistanceM: Double?) {
        val a = _state.value.pendingCalibrationA ?: return fail("Capturez d'abord le point A.")
        val pose = trackedPose() ?: return fail("Point B impossible : suivi AR non actif.")
        try {
            val e = Calibrator.twoPoints(Ids.new("cal"), stamps.now(), a, LocalObservation(pose, refB), fixScale, knownDistanceM)
            _state.update { it.copy(pendingCalibrationA = null) }
            record(e, "Calibration 2 points : échelle carte/AR mesurée %.3f, résidu %.2f m.".format(e.measuredScale, e.residualM))
            markCalibration(pose, "Calibration (2 points), point B")
        } catch (ex: IllegalArgumentException) { fail(ex.message ?: "Erreur") }
    }

    // ------------------------------------------------------------------ calibration / relocalization tables (D3–D5)

    private fun activeControlEvents(): List<ControlPointEvent> {
        val evs = _state.value.events
        val cur = evs.subList(evs.indexOfLast { it is TimeBaseAdded } + 1, evs.size)
        val removed = cur.filterIsInstance<ControlPointRemoved>().map { it.pointId }.toSet()
        return cur.filterIsInstance<ControlPointEvent>().filter { it.id !in removed }
    }

    /** D6: current pose of each control point's ARCore anchor (falls back to the pose recorded at validation). */
    private val anchorPoses = java.util.concurrent.ConcurrentHashMap<String, PoseSample>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val objectGroups = mutableListOf<app.lumisite.core.position.AnchorGroups.Group>()

    private fun observationNow(e: ControlPointEvent): LocalObservation = e.observation.copy(pose = anchorPoses[e.id] ?: e.observation.pose)

    /** GL thread, at most once per second: anchor poses moved by ARCore → transform solved again if the live position moves > 10 cm. */
    private fun onAnchors(m: Map<String, Pair<List<Double>, List<Double>>>) = mainHandler.post {
        if (closed) return@post
        var moved = false
        for ((id, v) in m) {
            if (objectGroups.any { it.id == id }) continue
            val old = anchorPoses[id] ?: activeControlEvents().firstOrNull { it.id == id }?.observation?.pose ?: continue
            val upd = old.copy(p = v.first, q = v.second)
            if (PoseGeometry.plane(upd).let { (it - PoseGeometry.plane(old)).norm() } > 0.02) moved = true
            anchorPoses[id] = upd
        }
        if (!moved) return@post
        val seg = activeSegment() ?: return@post
        val pts = activeControlEvents().mapNotNull(::toControlPoint)
        val sol = CalibrationSolver.solve(pts, stamps.now().monotonicNs, seg.transform.rotationRad, solverSettings) ?: return@post
        val here = trackedPose()?.let { PoseGeometry.plane(it) } ?: return@post
        val shift = (sol.transform.apply(here) - seg.transform.apply(here)).norm()
        DiagLog.i("POSITION", "Ancres ARCore mises à jour : décalage de la position courante " + "%.2f".format(java.util.Locale.ROOT, shift) + " m")
        if (shift > 0.10) resolve(null, seg, note = "ancres ARCore")
    }

    private fun toControlPoint(e: ControlPointEvent): ControlPoint? {
        val pose = observationNow(e).pose ?: return null
        val site = e.observation.reference.site
        return ControlPoint(e.id, e.t.monotonicNs, PoseGeometry.plane(pose), Vec2(site.e, site.n), e.kind == "RELOCALIZATION",
            e.headingDeg?.let { Angles.wrapPi(Angles.headingDegToEnuRad(it) - PoseGeometry.forwardPlaneAngle(pose)) })
    }

    fun controlRows(kind: String): List<ControlRow> {
        val sol = _state.value.controlSolution
        return activeControlEvents().filter { it.kind == kind }.mapIndexed { i, e ->
            ControlRow(i + 1, e, sol?.residualsM?.get(e.id), when { sol == null -> "en attente"; e.id in sol.rejected -> "rejeté"; e.id == sol.anchorId -> "ancre"; else -> "retenu" })
        }
    }

    /** Session > Calibrer > « Valider le point » and Session > Recaler > « Je suis ici : recaler »: one more row, transform solved again. */
    fun addControlPoint(kind: String, ref: ReferencePoint, headingDeg: Double? = null, headingSource: String? = null, headingCorrectionDeg: Double? = null, note: String? = null) {
        val reloc = kind == "RELOCALIZATION"
        val st = _state.value
        val pose = trackedPose() ?: return fail(poseUnavailable(if (reloc) "Recalage" else "Calibrage"))
        val before = activeSegment()
        if (reloc && before == null) return fail("Recalage impossible : aucun calibrage actif.")
        val e = ControlPointEvent(Ids.new("cp"), stamps.now(), kind, LocalObservation(pose, ref), headingDeg, headingSource)
        record(e)
        ar.requestAnchor(e.id, pose)
        resolve(e, before, headingCorrectionDeg, note)
    }

    fun removeControlPoint(id: String) {
        val before = activeSegment()
        record(ControlPointRemoved(Ids.new("ev"), stamps.now(), id))
        ar.dropAnchor(id); anchorPoses.remove(id)
        resolve(null, before)
    }

    /** « Nouveau calibrage » empties both tables, « Nouveau recalage » only the relocalizations. */
    fun clearControlPoints(relocalizationsOnly: Boolean) {
        val before = activeSegment()
        activeControlEvents().filter { !relocalizationsOnly || it.kind == "RELOCALIZATION" }.forEach { record(ControlPointRemoved(Ids.new("ev"), stamps.now(), it.id)); ar.dropAnchor(it.id); anchorPoses.remove(it.id) }
        resolve(null, before)
    }

    private fun resolve(trigger: ControlPointEvent?, before: TrajectoryCorrector.Segment?, headingCorrectionDeg: Double? = null, note: String? = null) {
        val evs = activeControlEvents()
        val pts = evs.mapNotNull(::toControlPoint)
        val now = stamps.now()
        if (pts.isEmpty()) {
            _state.update { it.copy(controlSolution = null, message = if (before != null) "Tableau vide : la transformation précédente reste active jusqu'au prochain point." else null) }
            return
        }
        val sol = CalibrationSolver.solve(pts, now.monotonicNs, before?.transform?.rotationRad, solverSettings)
        if (sol == null) {
            _state.update { it.copy(controlSolution = null, message = "Point enregistré. Ajoutez un 2e point à plus de 2 m, ou saisissez le cap de visée du 1er point, pour calibrer.") }
            return
        }
        var tr = sol.transform
        val anchor = pts.firstOrNull { it.id == sol.anchorId }
        if (headingCorrectionDeg != null && anchor != null) tr = Similarity2D.fromPointAndRotation(anchor.local, anchor.target, tr.rotationRad - Math.toRadians(headingCorrectionDeg))
        val summary = "${pts.size} point(s), ${sol.rejected.size} rejeté(s), résidu RMS " + "%.2f".format(sol.rmsM) + " m (${sol.method})"
        _state.update { it.copy(controlSolution = sol) }
        if (trigger?.kind == "RELOCALIZATION" && before != null) {
            val local = PoseGeometry.plane(observationNow(trigger).pose!!); val b = before.transform.apply(local); val a = tr.apply(local)
            record(RelocalizationEvent(Ids.new("rel"), now, trigger.observation, Enu(b.x, b.y), Enu(a.x, a.y), (a - b).norm(), before.transform, tr,
                "MULTI_POINT_WEIGHTED", headingCorrectionDeg, notes = listOfNotNull(note, summary).joinToString(" · ")), "Recalage : position déplacée de " + "%.2f".format((a - b).norm()) + " m. $summary")
        } else {
            record(CalibrationEvent(Ids.new("cal"), now, CalibrationMethod.MULTI_POINT, evs.filter { it.id !in sol.rejected }.map(::observationNow),
                headingDeg = evs.firstNotNullOfOrNull { it.headingDeg }, headingSource = evs.firstNotNullOfOrNull { it.headingSource },
                transform = tr, scaleFixed = true, residualM = sol.rmsM, notes = "points=${pts.map { it.id }} rejetés=${sol.rejected}"), "Calibrage : $summary")
            trigger?.observation?.pose?.let { markCalibration(it, "Calibrage, point ${evs.count { e -> e.kind == "CALIBRATION" }}") }
        }
        DiagLog.i("POSITION", "Transformation recalculée : $summary rot=" + "%.2f".format(java.util.Locale.ROOT, Math.toDegrees(tr.rotationRad)) + "° t=(" + "%.2f, %.2f".format(java.util.Locale.ROOT, tr.tx, tr.ty) + ")")
    }

    private var simThread: Thread? = null

    /** Debug/CI only ([DebugFlags.simulatedPoses]): TRACKING poses walking a 6 m circle at 0.6 m/s, source SIMULATED_DEBUG in the journal. */
    fun startSimulatedPoses() {
        if (simThread != null || !app.lumisite.android.BuildConfig.DEBUG) return
        DiagLog.i("SESSION", "Poses AR SIMULÉES (débogage) : aucune mesure réelle")
        simThread = Thread({
            val t0 = AndroidClock.monotonicNs()
            while (!closed) {
                val a = (AndroidClock.monotonicNs() - t0) / 1e9 * 0.1
                onArFrame(PoseSample(stamps.now(), listOf(6 * kotlin.math.cos(a) - 6, 0.0, -6 * kotlin.math.sin(a)), listOf(0.0, kotlin.math.sin(a / 2), 0.0, kotlin.math.cos(a / 2)),
                    TrackingState.TRACKING, source = "SIMULATED_DEBUG"), TrackingState.TRACKING, null)
                Thread.sleep(100)
            }
        }, "lumisite-poses-simulees").apply { isDaemon = true; start() }
    }

    fun relocalize(ref: ReferencePoint, headingCorrectionDeg: Double?, note: String?) {
        val pose = trackedPose() ?: return fail(poseUnavailable("Recalage"))
        val seg = activeSegment() ?: return fail("Recalage impossible : aucune calibration initiale.")
        val e = Relocalizer.relocalize(Ids.new("rel"), stamps.now(), pose, ref, seg.transform, headingCorrectionDeg, note)
        record(e, "Recalage : écart estimé/recalé = %.2f m.".format(e.deltaM))
    }

    val catalog: EquipmentCatalog = CatalogStore.get(ctx)

    private fun equipmentPayload(fam: CatalogEntry, details: JsonObject?) = buildJsonObject {
        put("catalog_version", catalog.version)
        put("block_name", fam.blockName)
        if (details != null) put("details", details)
    }

    /** Records an equipment of the catalog. Returns false (and explains why) when no position can be computed. */
    /** Id of the last record created by [survey] (used to propose the photo right after the placement). */
    var lastSurveyId: String? = null
        private set

    fun survey(familyId: String, note: String?, manual: ReferencePoint?, details: JsonObject? = null): Boolean {
        val fam = try { catalog.resolve(familyId) } catch (e: IllegalArgumentException) { fail(e.message ?: "Famille inconnue"); return false }
        val st = _state.value
        val pose = trackedPose()
        val seg = activeSegment()
        val aim = if (pose != null && seg != null) ar.aim.value else null
        if (manual == null && aim == app.lumisite.core.compute.AimResult.Grazing) { fail("Visée rasante refusée : orientez l'appareil vers le sol."); return false }
        val hit = aim as? app.lumisite.core.compute.AimResult.Hit
        val est = if (pose != null && seg != null) hit?.let { app.lumisite.core.compute.Aim.toSite(it.point, seg.transform) }
            ?: seg.transform.apply(PoseGeometry.plane(pose)).let { Enu(it.x, it.y, PoseGeometry.height(pose)) } else null
        if (manual == null && est == null) { fail(NO_POSITION); return false }
        val source = if (manual != null) "MANUAL_MAP" else hit?.source ?: "AR_POSE"
        if (manual == null && hit != null && hit.source != "ESTIMATED") ar.lastObjectY = hit.point.y
        val e = SurveyEvent(
            id = Ids.new("srv"), t = stamps.now(), kind = fam.blockName, note = note?.ifBlank { null }, pose = pose ?: st.lastPose,
            tracking = st.tracking, estimatedSite = est, positionSource = source, manualSite = manual?.site, lastGps = st.lastGps,
            payloadSchema = EquipmentCatalog.PAYLOAD_SCHEMA, payload = equipmentPayload(fam, details),
            dxf = if (fam.isBlock) DxfHint(layer = fam.defaultLayer, blockName = fam.blockName) else null,
            aimSession = if (manual == null) hit?.point?.let { listOf(it.x, it.y, it.z) } else null,
        )
        undoStack.addLast(e.id)
        lastSurveyId = e.id
        record(e, "Relevé « ${fam.label} » enregistré (${if (manual != null) "position manuelle" else "position AR"}).")
        if (manual == null && pose != null) {
            val at = hit?.point?.let { listOf(it.x, it.y, it.z) } ?: pose.p
            val c = Vec2(at[0], -at[2])
            val g = app.lumisite.core.position.AnchorGroups.assign(objectGroups, c)
                ?: app.lumisite.core.position.AnchorGroups.Group(Ids.new("grp"), c).also {
                    objectGroups += it
                    ar.requestAnchor(it.id, pose.copy(p = at, q = listOf(0.0, 0.0, 0.0, 1.0)))
                }
            DiagLog.i("ANCRES", "Relevé ${e.id} rattaché à l'ancre de groupe ${g.id} (${objectGroups.size} groupe(s), 8 m max)")
        }
        return true
    }

    /**
     * Edits a record by appending a new survey event that supersedes it (journal stays append-only).
     * [moveTo] sets a manual position; [familyId]/[note] change the family/comment; [delete] removes it.
     */
    /** Returns the id of the superseding record (null when nothing was recorded). */
    fun editSurvey(id: String, moveTo: Enu? = null, familyId: String? = null, note: String? = null, delete: Boolean = false, attach: Attachment? = null, replacePath: String? = null): String? {
        val events = _state.value.events
        val old = SurveyPositions.current(events).firstOrNull { it.id == id } ?: return null.also { fail("Relevé introuvable.") }
        val fam = familyId?.let { try { catalog.resolve(it) } catch (e: IllegalArgumentException) { return null.also { fail(e.message ?: "Famille inconnue") } } }
        val pos = moveTo ?: SurveyPositions.resolveAll(events).first { it.event.id == id }.site
        val moved = moveTo != null
        val e = old.copy(
            id = Ids.new("srv"), t = stamps.now(), poseT = old.poseT ?: old.t, supersedes = old.id, deleted = if (delete) true else null,
            note = note?.let { it.ifBlank { null } } ?: old.note,
            positionSource = if (moved) "MANUAL_EDIT" else old.positionSource,
            manualSite = if (moved) pos else old.manualSite,
            kind = fam?.blockName ?: old.kind,
            payloadSchema = if (fam != null) EquipmentCatalog.PAYLOAD_SCHEMA else old.payloadSchema,
            payload = if (fam != null) equipmentPayload(fam, old.payload?.get("details") as? JsonObject) else old.payload,
            dxf = if (fam != null) (if (fam.isBlock) DxfHint(layer = fam.defaultLayer, blockName = fam.blockName) else null) else old.dxf,
            attachments = if (attach != null) old.attachments.filterNot { it.file == replacePath } + attach else old.attachments,
        )
        record(e, when { attach != null -> "Pièce jointe ajoutée."; delete -> "Relevé supprimé."; moved -> "Relevé déplacé."; else -> "Relevé modifié." })
        undoStack.addLast(e.id)
        return e.id
    }

    private val undoStack = ArrayDeque<String>()

    fun currentShapes(): List<ShapeEvent> = Shapes.current(_state.value.events)

    fun addShape(kind: String, geometryType: String, points: List<Enu>, radiusM: Double? = null, label: String? = null,
                 comment: String? = null, annotationType: String? = null, style: ShapeStyle = ShapeStyle()): String {
        val bad = if (kind == "zone") app.lumisite.core.survey.Zones.problem(points) else null
        if (bad != null) {
            _state.update { it.copy(message = "Zone refusée : $bad. Le tracé est conservé : corrigez-le.") }
            return ""
        }
        val e = ShapeEvent(Ids.new("shp"), stamps.now(), kind, geometryType, points, radiusM, label, comment, annotationType, style,
            zoneKind = if (kind == "zone") "ARCHITECTURAL" else null, source = if (kind == "zone") "MANUAL_MAP" else null)
        record(e, "${if (kind == "annotation") annotationType ?: "Annotation" else "Forme"} enregistrée."); undoStack.addLast(e.id)
        return e.id
    }

    /** Appends a new version of a shape (append-only). Returns the id of the new version. */
    fun editShape(id: String, delete: Boolean = false, change: (ShapeEvent) -> ShapeEvent = { it }): String? {
        val old = currentShapes().firstOrNull { it.id == id } ?: run { fail("Forme introuvable."); return null }
        val e = change(old).copy(id = Ids.new("shp"), t = stamps.now(), supersedes = old.id, deleted = if (delete) true else null)
        val bad = if (e.kind == "zone" && !delete) app.lumisite.core.survey.Zones.problem(e.points, e.holes.orEmpty()) else null
        if (bad != null) { fail("Modification refusée : $bad."); return null }
        record(e, if (delete) "Forme supprimée." else "Forme modifiée."); undoStack.addLast(e.id)
        return e.id
    }

    fun duplicateShape(id: String): String? {
        val old = currentShapes().firstOrNull { it.id == id } ?: return null
        val e = Shapes.translate(old, 0.5, -0.5).copy(id = Ids.new("shp"), t = stamps.now(), supersedes = null, deleted = null)
        record(e, "Forme dupliquée."); undoStack.addLast(e.id)
        return e.id
    }

    /** Undoes the last creation or edit of this session by appending the previous state (nothing is erased). */
    fun undo() {
        val id = undoStack.removeLastOrNull() ?: return fail("Rien à annuler.")
        val ev = _state.value.events
        val head = Shapes.head(ev, id)
        when (val done = ev.firstOrNull { it.id == id }) {
            is ShapeEvent -> {
                val prev = done.supersedes?.let { p -> ev.firstOrNull { it.id == p } as? ShapeEvent }
                record((prev ?: done).copy(id = Ids.new("shp"), t = stamps.now(), supersedes = head, deleted = if (prev == null) true else prev.deleted), "Annulé.")
            }
            is app.lumisite.core.model.ZoneEvent -> {
                val prev = done.supersedes?.let { p -> ev.firstOrNull { it.id == p } }
                    ?.let { (it as? app.lumisite.core.model.ZoneEvent) ?: (it as? ShapeEvent)?.let(app.lumisite.core.model.ZoneEvent::of) }
                record((prev ?: done).copy(id = Ids.new("zon"), t = stamps.now(), supersedes = head, deleted = if (prev == null) true else prev.deleted), "Annulé.")
            }
            is SurveyEvent -> {
                val prev = done.supersedes?.let { p -> ev.firstOrNull { it.id == p } as? SurveyEvent }
                record((prev ?: done).copy(id = Ids.new("srv"), t = stamps.now(), supersedes = head, deleted = if (prev == null) true else prev.deleted), "Annulé.")
            }
            else -> fail("Rien à annuler.")
        }
    }

    fun attachmentFile(prefix: String, ext: String): File {
        val d = File(repo.sessionDir(sessionId), "attachments").apply { mkdirs() }
        return File(d, "${prefix}_${(d.list()?.count { it.startsWith(prefix + "_") } ?: 0) + 1}.$ext")
    }

    fun stampNow() = stamps.now()

    /** Returns the id of the superseding record. [replacePath] drops that attachment from the record (its file stays on disk). */
    fun attach(surveyId: String, file: File, mediaType: String, role: String?, startUtcMs: Long? = null, monotonicNs: Long? = null, replacePath: String? = null): String? {
        val rel = file.relativeTo(repo.sessionDir(sessionId)).invariantSeparatorsPath
        return editSurvey(surveyId, attach = Attachment(rel, mediaType, sha256(file), role, startUtcMs, monotonicNs), replacePath = replacePath)
    }

    fun sessionRelPath(file: File): String = file.relativeTo(repo.sessionDir(sessionId)).invariantSeparatorsPath

    /** Current heading of the camera in the site frame (degrees from north, clockwise), when tracked and calibrated. */
    fun currentHeadingDeg(): Double? {
        val pose = trackedPose() ?: return null
        val seg = activeSegment() ?: return null
        val enuRad = PoseGeometry.forwardPlaneAngle(pose) + seg.transform.rotationRad
        return ((90.0 - Math.toDegrees(enuRad)) % 360.0 + 360.0) % 360.0
    }

    fun syncManual(code: String) {
        val now = stamps.now()
        record(SyncEvent(Ids.new("syn"), now, code, SyncMethod.MANUAL_TAP, SyncRole.PARTICIPANT, now.monotonicNs, notes = "Button press time; includes human reaction time"),
            "Top synchro '$code' enregistré.")
    }

    fun ownSyncId() = app.lumisite.core.sync.IdFrame.idOf(header.device.id)

    /**
     * Records one SyncEvent per identified chirp of the round. syncCode = "AC-<emitter hex>-<counter>" is the same on
     * every device that heard that chirp, so SyncEstimator pairs devices without any typed code (BeepBeep principle).
     * Returns N = number of distinct emitters recognised (own included); N counts only if the own chirp was heard.
     */
    fun syncRound(label: String, round: app.lumisite.android.capture.AudioSync.Round, counter: Int): Int {
        val own = "%04X".format(ownSyncId())
        val ok = round.heard.filter { it.frameOk && it.emitterHex != null }
        val ids = ok.map { it.emitterHex!! }.distinct()
        val localIdx = ok.indexOfFirst { it.emitterHex == own }.takeIf { it >= 0 }
        val peers = ok.indices.filter { it != localIdx }
        val now = stamps.now()
        ok.forEach { h ->
            (SyncEvent(Ids.new("syn"), now, "AC-${h.emitterHex}-${h.counter}", SyncMethod.AUDIO_CHIRP,
                if (h.emitterHex == own) SyncRole.EMITTER else SyncRole.RECEIVER, h.eventMonotonicNs, h.detection,
                notes = "experimental; propagation and input latency not compensated",
                protocol = app.lumisite.core.sync.IdFrame.PROTOCOL, heardIds = ids, localChirpIndex = localIdx,
                peerChirpIndices = peers, emitCounter = counter, label = label.ifBlank { null }).let { record(it, "Synchro acoustique : ${ids.size} identifiant(s)") })
        }
        journal.flushAll()
        return if (localIdx != null) ids.size else 0
    }

    /** Calibration label shown on the camera image: residual (m) and measured scale of the last calibration. */
    fun calibrationLabel(): String {
        val cal = _state.value.events.filterIsInstance<CalibrationEvent>().lastOrNull() ?: return "Non calibré"
        return "Calibré · résidu %.2f m%s".format(cal.residualM ?: 0.0, cal.measuredScale?.let { " · échelle %.3f".format(it) } ?: "")
    }

    fun syncEvents() = _state.value.events.filterIsInstance<SyncEvent>()
    fun deviceId() = header.device.id
    fun timeBaseId() = stamps.timeBase.id
    fun sessionDir() = repo.sessionDir(sessionId)
    fun flush() = journal.flushAll()
}
