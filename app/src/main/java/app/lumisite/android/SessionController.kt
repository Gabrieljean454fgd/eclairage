package app.lumisite.android

import android.content.Context
import app.lumisite.android.capture.ArTracker
import app.lumisite.android.capture.GpsRecorder
import app.lumisite.android.capture.SensorRecorder
import app.lumisite.android.data.AndroidClock
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
)

/**
 * Orchestrates capture for one field session: journal (append-only, local), AR poses, IMU, GPS, and the
 * user events (calibration, relocalization, survey, sync). Business rules live in :core.
 */
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
        journal.imu(s); imuCount++
        if (imuCount % 50 == 0L) _state.update { it.copy(imuCount = imuCount) }
    }
    private val gps = GpsRecorder(ctx, { stamps }, ::onGps, ::onGpsStatus)
    val ar = ArTracker(ctx, { stamps }, ::onArFrame)

    init {
        val tbRef = TimeBase(Ids.new("tb"), DeviceInfo.deviceId(ctx), TimeBase.ANDROID_ELAPSED_REALTIME, AndroidClock.utcEpochMs(), AndroidClock.monotonicNs(),
            notes = "SystemClock.elapsedRealtimeNanos(); UTC from System.currentTimeMillis() captured at the same instant")
        stamps = StampFactory(AndroidClock, tbRef)
        val dir = repo.sessionDir(sessionId)
        if (!resume) {
            val arSupport = runCatching { ArTracker.availability(ctx).name }.getOrDefault("UNKNOWN")
            header = SessionHeader(
                id = sessionId, siteId = site.id, name = "Session ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date())}",
                createdUtcMs = AndroidClock.utcEpochMs(), device = DeviceInfo.describe(ctx, arSupport), timeBases = listOf(tbRef),
                localFrame = CrsInfo("AR_SESSION", "ARCORE_WORLD", "ARCore session world frame: right-handed, +Y up (gravity), metres; origin = device pose at AR session start. Horizontal plane mapping: (x, -z)."),
            )
            journal = SessionJournal(dir, header)
            val started = SessionStarted(Ids.new("ev"), stamps.now())
            journal.event(started)
            _state.update { it.copy(events = listOf(started)) }
        } else {
            val data = SessionReader.read(dir, includeImu = false)
            header = data.header
            poses += data.poses
            journal = SessionJournal(dir)
            val resumed = TimeBaseAdded(Ids.new("ev"), stamps.now(), tbRef, "session resumed")
            journal.event(resumed)
            // The AR session frame of a resumed session is new: previous transforms do not apply to it.
            _state.update { it.copy(events = data.events + resumed, poseCount = data.poses.size, message = "Session reprise : nouveau repère AR, recalibrez avant de poursuivre.") }
            recompute()
        }
    }

    // ------------------------------------------------------------------ capture lifecycle

    fun startCapture(hasLocation: Boolean) {
        sensors.start()
        if (hasLocation) gps.start() else onGpsStatus(GpsStatusSample(stamps.now(), "gps", "NO_PERMISSION"))
    }

    fun pauseCapture() {
        sensors.stop(); gps.stop(); ar.pause()
        logTracking(TrackingState.STOPPED, "APP_PAUSED")
        journal.flushAll()
    }

    fun close() {
        pauseCapture()
        journal.event(SessionStopped(Ids.new("ev"), stamps.now(), "user"))
        journal.close(); ar.close()
    }

    fun setArStatus(text: String) = _state.update { it.copy(arStatus = text) }

    fun notify(msg: String) = fail(msg)

    fun markArUnavailable(reason: String) = logTracking(TrackingState.UNAVAILABLE, reason)

    private fun onArFrame(pose: PoseSample?, state: TrackingState, reason: String?) {
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

    private fun onGps(f: GpsFix) { journal.gps(f); _state.update { it.copy(lastGps = f) } }
    private fun onGpsStatus(s: GpsStatusSample) { journal.gpsStatus(s); _state.update { it.copy(gpsStatus = it.gpsStatus + (s.provider to s.status)) } }

    // ------------------------------------------------------------------ derived display

    private fun activeSegment() = TrajectoryCorrector.activeAt(TrajectoryCorrector.timeline(_state.value.events), Long.MAX_VALUE)

    private fun appendLive(p: PoseSample) {
        val seg = activeSegment()
        val cur = seg?.transform?.apply(PoseGeometry.plane(p))?.let { Enu(it.x, it.y, PoseGeometry.height(p)) }
        _state.update { st ->
            var traj = st.trajectory
            if (cur != null && seg != null) {
                val pt = CorrectedPoint(p.t.monotonicNs, p.t.utcEpochMs, p.t.timeBaseId, cur, ltp.toLatLon(cur.copy(u = 0.0)), p.tracking, seg.eventId)
                val last = traj.lastOrNull()?.lastOrNull()
                val gap = last == null || p.t.monotonicNs - last.monotonicNs > 1_000_000_000L
                traj = if (gap) traj + listOf(listOf(pt)) else traj.dropLast(1) + listOf(traj.last() + pt)
            }
            st.copy(poseCount = st.poseCount + 1, imuCount = imuCount, currentSite = cur, distanceSinceFixM = distanceSinceFix(traj, seg?.fromNs))
        }
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

    private fun record(e: SessionEvent, msg: String? = null) {
        journal.event(e)
        _state.update { it.copy(events = it.events + e, message = msg) }
        if (e is CalibrationEvent || e is RelocalizationEvent) recompute()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    private fun fail(msg: String) = _state.update { it.copy(message = msg) }

    private fun trackedPose(): PoseSample? = _state.value.lastPose?.takeIf { _state.value.tracking == TrackingState.TRACKING }

    // ------------------------------------------------------------------ user actions

    fun calibrateOnePoint(ref: ReferencePoint, headingDeg: Double, headingSource: String, uncertaintyDeg: Double?) {
        val pose = trackedPose() ?: return fail("Calibration impossible : suivi AR non actif.")
        val e = Calibrator.onePointHeading(Ids.new("cal"), stamps.now(), LocalObservation(pose, ref), headingDeg, headingSource, uncertaintyDeg)
        record(e, "Calibration enregistrée (1 point + cap %.0f°).".format(headingDeg))
    }

    fun captureCalibrationA(ref: ReferencePoint) {
        val pose = trackedPose() ?: return fail("Point A impossible : suivi AR non actif.")
        _state.update { it.copy(pendingCalibrationA = LocalObservation(pose, ref), message = "Point A capturé. Marchez jusqu'au point B.") }
    }

    fun calibrateTwoPoints(refB: ReferencePoint, fixScale: Boolean, knownDistanceM: Double?) {
        val a = _state.value.pendingCalibrationA ?: return fail("Capturez d'abord le point A.")
        val pose = trackedPose() ?: return fail("Point B impossible : suivi AR non actif.")
        try {
            val e = Calibrator.twoPoints(Ids.new("cal"), stamps.now(), a, LocalObservation(pose, refB), fixScale, knownDistanceM)
            _state.update { it.copy(pendingCalibrationA = null) }
            record(e, "Calibration 2 points : échelle carte/AR mesurée %.3f, résidu %.2f m.".format(e.measuredScale, e.residualM))
        } catch (ex: IllegalArgumentException) { fail(ex.message ?: "Erreur") }
    }

    fun relocalize(ref: ReferencePoint, headingCorrectionDeg: Double?, note: String?) {
        val pose = trackedPose() ?: return fail("Recalage impossible : suivi AR non actif.")
        val seg = activeSegment() ?: return fail("Recalage impossible : aucune calibration initiale.")
        val e = Relocalizer.relocalize(Ids.new("rel"), stamps.now(), pose, ref, seg.transform, headingCorrectionDeg, note)
        record(e, "Recalage : écart estimé/recalé = %.2f m.".format(e.deltaM))
    }

    fun survey(kind: String, note: String?, manual: ReferencePoint?) {
        val st = _state.value
        val pose = trackedPose()
        val seg = activeSegment()
        val est = if (pose != null && seg != null) seg.transform.apply(PoseGeometry.plane(pose)).let { Enu(it.x, it.y, PoseGeometry.height(pose)) } else null
        val source = when { manual != null -> "MANUAL_MAP"; pose != null -> "AR_POSE"; else -> "NONE" }
        val e = SurveyEvent(
            id = Ids.new("srv"), t = stamps.now(), kind = kind, note = note?.ifBlank { null }, pose = pose ?: st.lastPose,
            tracking = st.tracking, estimatedSite = est, positionSource = source, manualSite = manual?.site, lastGps = st.lastGps,
            dxf = DxfHint(layer = "LUMI_${kind}", blockName = "LUMI_${kind}_GENERIC"),
        )
        record(e, "Relevé $kind enregistré (${source}).")
    }

    fun syncManual(code: String) {
        val now = stamps.now()
        record(SyncEvent(Ids.new("syn"), now, code, SyncMethod.MANUAL_TAP, SyncRole.PARTICIPANT, now.monotonicNs, notes = "Button press time; includes human reaction time"),
            "Top synchro '$code' enregistré.")
    }

    fun syncAudio(code: String, role: SyncRole, eventNs: Long, det: AudioDetection, trusted: Boolean, msg: String) {
        record(SyncEvent(Ids.new("syn"), stamps.now(), code, SyncMethod.AUDIO_CHIRP, role, eventNs, det,
            notes = if (trusted) "experimental" else "experimental; low-confidence detection"), "Synchro audio '$code' : $msg")
    }

    fun syncEvents() = _state.value.events.filterIsInstance<SyncEvent>()
    fun deviceId() = header.device.id
    fun timeBaseId() = stamps.timeBase.id
    fun sessionDir() = repo.sessionDir(sessionId)
    fun flush() = journal.flushAll()
}
