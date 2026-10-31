package app.lumisite.android

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
)

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
    val ar = ArTracker(ctx, { stamps }, ::onArFrame)
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
        if (hasLocation) gps.start() else onGpsStatus(GpsStatusSample(stamps.now(), "gps", "NO_PERMISSION"))
    }

    @Volatile var closed = false; private set

    fun pauseCapture() {
        if (closed) return
        runCatching { sensors.stop() }; runCatching { gps.stop() }; ar.pause()
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
        runCatching { pauseCapture() }
        closed = true
        ar.close()
        runCatching { journal.event(SessionStopped(Ids.new("ev"), stamps.now(), reason)) }
        runCatching { journal.close() }
        runCatching { featuresWriter?.close() }
        mirror.syncAll()
        DiagLog.i("SESSION", "Fin de la session $sessionId ($reason), copie dans ${mirror.displayDir}")
        DiagLog.mirrorNow()
    }

    fun setArStatus(text: String) = _state.update { it.copy(arStatus = text) }

    fun notify(msg: String) = fail(msg)

    fun markArUnavailable(reason: String) = logTracking(TrackingState.UNAVAILABLE, reason)

    private fun onArFrame(pose: PoseSample?, state: TrackingState, reason: String?) {
        if (closed) return
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

    private fun onGps(f: GpsFix) { if (closed) return; journal.gps(f); _state.update { it.copy(lastGps = f) } }
    private fun onGpsStatus(s: GpsStatusSample) { if (closed) return; journal.gpsStatus(s); _state.update { it.copy(gpsStatus = it.gpsStatus + (s.provider to s.status)) } }

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

    val catalog: EquipmentCatalog = CatalogStore.get(ctx)

    private fun equipmentPayload(fam: CatalogEntry, details: JsonObject?) = buildJsonObject {
        put("catalog_version", catalog.version)
        put("block_name", fam.blockName)
        if (details != null) put("details", details)
    }

    /** Records an equipment of the catalog. Returns false (and explains why) when no position can be computed. */
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
        record(e, "Relevé « ${fam.label} » enregistré (${if (manual != null) "position manuelle" else "position AR"}).")
        return true
    }

    /**
     * Edits a record by appending a new survey event that supersedes it (journal stays append-only).
     * [moveTo] sets a manual position; [familyId]/[note] change the family/comment; [delete] removes it.
     */
    fun editSurvey(id: String, moveTo: Enu? = null, familyId: String? = null, note: String? = null, delete: Boolean = false, attach: Attachment? = null) {
        val events = _state.value.events
        val old = SurveyPositions.current(events).firstOrNull { it.id == id } ?: return fail("Relevé introuvable.")
        val fam = familyId?.let { try { catalog.resolve(it) } catch (e: IllegalArgumentException) { return fail(e.message ?: "Famille inconnue") } }
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
            attachments = if (attach != null) old.attachments + attach else old.attachments,
        )
        record(e, when { attach != null -> "Pièce jointe ajoutée."; delete -> "Relevé supprimé."; moved -> "Relevé déplacé."; else -> "Relevé modifié." })
        undoStack.addLast(e.id)
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

    fun attach(surveyId: String, file: File, mediaType: String, role: String?, startUtcMs: Long? = null, monotonicNs: Long? = null) {
        val rel = file.relativeTo(repo.sessionDir(sessionId)).invariantSeparatorsPath
        editSurvey(surveyId, attach = Attachment(rel, mediaType, sha256(file), role, startUtcMs, monotonicNs))
    }

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
