package app.lumisite.android.capture

import android.app.Activity
import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import app.lumisite.android.data.DiagLog
import app.lumisite.core.model.PoseSample
import app.lumisite.core.model.TrackingState
import app.lumisite.core.time.StampFactory
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.LightEstimate
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import app.lumisite.core.compute.Aim
import app.lumisite.core.compute.AimResult
import app.lumisite.core.compute.ArGeometry
import app.lumisite.core.compute.ArMeasure
import app.lumisite.core.compute.ArSiteOverlays
import app.lumisite.core.compute.ArSymbols
import app.lumisite.core.catalog.LsCatalog
import app.lumisite.core.compute.ArPlaneDiagnose
import app.lumisite.core.compute.PlaneFilter
import app.lumisite.core.compute.SupportFromTilt
import app.lumisite.core.compute.V3
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.EnumSet
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Pose tracking with ARCore (visual-inertial odometry). The camera feed is only used by ARCore and drawn on
 * screen; no image is stored. Emits camera poses in the ARCore session frame.
 *
 * State mapping (documented in docs/SESSION_FORMAT.md):
 *  TRACKING -> TRACKING; PAUSED before first TRACKING -> INITIALIZING;
 *  PAUSED for < [degradedWindowMs] after tracking -> DEGRADED; longer -> LOST; STOPPED -> STOPPED.
 */
class ArTracker(
    private val ctx: Context,
    private val stamps: () -> StampFactory?,
    private val onFrame: (pose: PoseSample?, state: TrackingState, reason: String?) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile var session: Session? = null; private set
    @Volatile var lastError: String? = null; private set
    private fun setError(msg: String) { lastError = msg; DiagLog.e("ARCORE", msg) }

    /** Debug ARCore recording / playback (task 2): set from launch extras, see [ArDebug]. */
    val playback = MutableStateFlow(false)
    val recording = MutableStateFlow(false)
    @Volatile private var datasetApplied = false
    private val degradedWindowMs = 3_000L
    private var everTracked = false
    private var pausedSinceMs: Long? = null
    private var viewportW = 0; private var viewportH = 0; private var viewportChanged = false
    private val bg = BackgroundRenderer()

    /** Aim from the camera centre, recomputed every frame on the GL thread (lot 10). */
    val aim = MutableStateFlow<AimResult?>(null)
    val planeCount = MutableStateFlow(0)
    val torchOn = MutableStateFlow(false)
    /** Height (session frame) of the last object placed by aiming: second level of the aim fallback chain. */
    @Volatile var lastObjectY: Double? = null
    /** 2f: hauteur sous plafond du site (Session > Calibrer); virtual ceiling of the PLAFOND aim when ARCore sees none. */
    @Volatile var ceilingHeightM: Double? = null
    /** 2f: lowest detected floor plane (session frame y), refreshed by [updateAim]; null when no floor plane is known. */
    @Volatile var floorY: Double? = null; private set
    @Volatile var configNote: String = ""; private set
    private var autoTorchChecked = false

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val lineRenderer = LineRenderer()
    private var planes: List<Plane> = emptyList()
    private var subsumedCount = 0
    private var frameNo = 0L

    /** D6: ARCore anchors (control points, object groups), created / detached on the GL thread, poses reported at most once per second. */
    /** v176c: what is drawn at an anchor: calibration flag, luminaire symbol, nothing (object groups). */
    enum class AnchorKind { FLAG, LUMINAIRE, GROUP }
    data class Mark(val id: String, val kind: AnchorKind, val at: V3, val live: Boolean)
    private val anchorAdds = java.util.concurrent.ConcurrentLinkedQueue<Triple<String, PoseSample, AnchorKind>>()
    private val anchorDrops = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val anchors = HashMap<String, com.google.ar.core.Anchor>()
    private val anchorKinds = HashMap<String, AnchorKind>()
    /** v176c « Contours » switch (stripped AR lines), plane counters (sol, murs, plafond). build 199 (point 6): the aimed
     *  surface (SOL / MUR / PLAFOND) is derived from the camera tilt, no selector — the flow only feeds the display line. */
    /** Contours du site / bâtiment / zones du relevé, recalés par la calibration. Affichés par défaut. */
    val contoursOn = MutableStateFlow(true)
    /** Arêtes des plans ARCore (option, désactivée par défaut — retour 22/11). */
    val arCoreEdgesOn = MutableStateFlow(false)
    /** build 199 (point 2d) : polygones ARCore bruts en blanc, Réglages > Diagnostic. */
    val rawPolygonsOn = MutableStateFlow(false)
    @Volatile var catalog: LsCatalog? = null
    @Volatile var siteContourRings: List<List<V3>> = emptyList()
    private val luminaireMeta = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String?>>()
    fun setLuminaireMeta(id: String, symbolId: String?, shortLabel: String?) { luminaireMeta[id] = symbolId to shortLabel }
    val measureA = MutableStateFlow<V3?>(null)
    val measureB = MutableStateFlow<V3?>(null)
    fun setMeasure(a: V3, b: V3) { measureA.value = a; measureB.value = b }
    fun clearMeasure() { measureA.value = null; measureB.value = null }
    data class ScreenLabel(val xPx: Float, val yPx: Float, val text: String)
    val screenLabels = MutableStateFlow<List<ScreenLabel>>(emptyList())
    val planeCounts = MutableStateFlow(Triple(0, 0, 0))
    /** Build 248 : diagnostic snapshot (rate-limited to 500 ms) publiant état tracking, depth, plans et polygones. */
    data class ArPlaneStats(
        val trackingState: String,
        val failureReason: String?,
        val depthSupported: Boolean,
        val depthActive: String,
        val totalPlanes: Int,
        val trackingPlanes: Int,
        val subsumedPlanes: Int,
        val byType: Map<String, Int>,
        val byState: Map<String, Int>,
        val validPolygons: Int,
        val invalidPolygons: Int,
        val minAreaM2: Double,
        val maxAreaM2: Double,
        val avgAreaM2: Double,
        val rendererGotPolygons: Boolean,
        val cameraYM: Double,
        val floorYM: Double?,
    )
    val planeStats = MutableStateFlow<ArPlaneStats?>(null)
    val aimSurface = MutableStateFlow<String?>("MUR")
    /** build 199 (point 5): plane counters published at most every 500 ms so the line under the camera stays stable. */
    private var lastCountsMs = 0L
    private var lastStatsMs = 0L
    private var lastStatsLogMs = 0L
    @Volatile private var depthSupported = false
    /** Depth mode is fixed at configure() time; cached so publishStats() never calls Session.getConfig() per frame. */
    @Volatile private var depthModeName = "UNKNOWN"
    /** Last classified support (GL thread), for the tilt hysteresis. */
    private var lastSupport: SupportFromTilt.Support? = null
    /** Tilt fallback when the AR pose is unavailable (sensor rotation vector), set by the session controller. */
    var sensorTiltDeg: (() -> Double?)? = null
    private var lastAnchorReportMs = 0L
    @Volatile var onAnchors: ((Map<String, Pair<List<Double>, List<Double>>>) -> Unit)? = null
    fun requestAnchor(id: String, pose: PoseSample, kind: AnchorKind = AnchorKind.GROUP) { anchorAdds += Triple(id, pose, kind) }
    fun dropAnchor(id: String) { anchorDrops += id }

    private fun updateAnchors(s: Session, nowMs: Long) {
        while (true) {
            val (id, p, kind) = anchorAdds.poll() ?: break
            runCatching {
                anchors.remove(id)?.let { old -> runCatching { old.detach() } }
                anchors[id] = s.createAnchor(Pose(p.p.map { it.toFloat() }.toFloatArray(), p.q.map { it.toFloat() }.toFloatArray()))
                anchorKinds[id] = kind
            }.onFailure { setError("ancre $id : ${it.message}") }
        }
        while (true) { val id = anchorDrops.poll() ?: break; anchorKinds.remove(id); anchors.remove(id)?.let { a -> runCatching { a.detach() } } }
        if (anchors.isEmpty() || nowMs - lastAnchorReportMs < 1_000) return
        lastAnchorReportMs = nowMs
        val out = anchors.filterValues { it.trackingState == com.google.ar.core.TrackingState.TRACKING }.mapValues { (_, a) ->
            val q = a.pose
            listOf(q.tx().toDouble(), q.ty().toDouble(), q.tz().toDouble()) to listOf(q.qx().toDouble(), q.qy().toDouble(), q.qz().toDouble(), q.qw().toDouble())
        }
        if (out.isNotEmpty()) onAnchors?.invoke(out)
    }

    /** Torch on/off, applied from the caller thread (never session.configure() inside onDrawFrame). Off on pause. */
    fun setTorch(on: Boolean) {
        val s = session ?: return
        runCatching { applyTorchInternal(s, on); torchOn.value = on }.onFailure { setError("torche : ${it.message}") }
    }

    companion object {
        fun availability(ctx: Context): ArCoreApk.Availability = ArCoreApk.getInstance().checkAvailability(ctx)

        /** Returns true when ARCore is installed and ready; may launch the Play Store install flow. */
        fun ensureInstalled(activity: Activity, userRequested: Boolean): Boolean = try {
            ArCoreApk.getInstance().requestInstall(activity, userRequested) == ArCoreApk.InstallStatus.INSTALLED
        } catch (e: Exception) { false }
    }

    fun create(): Boolean {
        if (session != null) return true
        return try {
            val s = Session(ctx)
            // Pixel 7 (et autres compatibles) : active la Depth API en mode AUTOMATIC sans exiger de capteur ToF.
            // isDepthModeSupported est appelé avant configure() ; en cas d'échec on garde DISABLED.
            depthSupported = runCatching { s.isDepthModeSupported(Config.DepthMode.AUTOMATIC) }.getOrDefault(false)
            // 30 fps, no depth sensor required, largest image size first.
            runCatching {
                val filter = CameraConfigFilter(s)
                    .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
                    .setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE))
                s.getSupportedCameraConfigs(filter).maxByOrNull { it.imageSize.width * it.imageSize.height }?.let { s.cameraConfig = it }
            }
            val cfg = Config(s).apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                depthMode = if (depthSupported) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            }
            s.configure(cfg)
            depthModeName = s.config.depthMode.name
            configNote = "caméra ${s.cameraConfig.imageSize.width}×${s.cameraConfig.imageSize.height}, profondeur $depthModeName (depthSupported=$depthSupported)"
            DiagLog.i("ARCORE", "Session créée : $configNote")
            session = s; lastError = null; true
        } catch (e: Exception) {
            setError("${e.javaClass.simpleName}: ${e.message}"); false
        }
    }

    /** Set to false before tearing down: the GL thread then stops calling into the ARCore session. */
    @Volatile var rendering = true

    fun resume() {
        if (!rendering) return
        val s = session ?: return
        if (!datasetApplied) {
            datasetApplied = true
            // Playback must be set while the session is paused, before resume(); recording uses setMp4DatasetUri.
            ArDebug.playbackPath?.let { path ->
                try {
                    s.setPlaybackDatasetUri(android.net.Uri.fromFile(java.io.File(path)))
                    playback.value = true
                    DiagLog.i("ARCORE", "PLAYBACK du jeu de données $path")
                } catch (e: Exception) { setError("playback $path : ${e.javaClass.simpleName} ${e.message}") }
            }
            if (!playback.value) ArDebug.recordPath?.let { path ->
                try {
                    java.io.File(path).parentFile?.mkdirs()
                    s.startRecording(com.google.ar.core.RecordingConfig(s).setMp4DatasetUri(android.net.Uri.fromFile(java.io.File(path))).setAutoStopOnPause(false))
                    recording.value = true
                    DiagLog.i("ARCORE", "Enregistrement ARCore vers $path")
                } catch (e: Exception) { setError("enregistrement $path : ${e.javaClass.simpleName} ${e.message}") }
            }
        }
        try { s.resume() } catch (e: Exception) { setError("resume: ${e.message}") }
    }

    fun pause() {
        // Flash back to OFF in the config too, otherwise ARCore relights it on resume while the UI says "off".
        session?.let { s -> runCatching { applyTorchInternal(s, false) } }
        torchOn.value = false
        try { session?.pause() } catch (e: Exception) { setError("pause: ${e.message}") }
    }

    /** Call only once the GLSurfaceView is paused (no concurrent onDrawFrame). Never throws. */
    fun close() {
        rendering = false
        val s = session ?: return
        session = null
        if (recording.value) { runCatching { s.stopRecording() }; recording.value = false }
        try { s.pause() } catch (_: Exception) {}
        try { s.close() } catch (e: Exception) { setError("close: ${e.message}") }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        bg.create()
        lineRenderer.create()
        session?.setCameraTextureName(bg.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportW = width; viewportH = height; viewportChanged = true
    }


    private fun updateAim(s: Session, frame: Frame, state: TrackingState, reason: String?) {
        val cam = frame.camera
        if (!autoTorchChecked && state == TrackingState.TRACKING) { autoTorchChecked = true; if (lowLight(frame)) main.post { setTorch(true) } }
        if (state != TrackingState.TRACKING) {
            aim.value = AimResult.NoTarget
            updateCounters(state, Triple(0, 0, 0))
            publishStats(state, reason, emptyList(), 0, ArPlaneDiagnose.stats(emptyList()), cameraY = 0.0, floorY = null)
            return
        }
        // Plane list sampled every 10 frames (getAllTrackables is costly); tracking state re-checked every frame.
        if (frameNo++ % 10 == 0L) {
            val all = s.getAllTrackables(Plane::class.java)
            planes = all.filter { it.subsumedBy == null }
            subsumedCount = all.count { it.subsumedBy != null }
        }
        val trackingPlanes = planes.filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING }
        val p = cam.pose
        val cameraY = p.ty().toDouble()
        val descs = trackingPlanes.map { pl ->
            val c = pl.centerPose
            val src = when (pl.type) { Plane.Type.VERTICAL -> "AR_WALL"; Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "AR_CEILING"; else -> "AR_PLANE" }
            val area = runCatching {
                val poly = pl.polygon
                if (poly == null) 0.0 else { poly.rewind(); ArGeometry.polygonAreaM2((0 until poly.limit()).map { poly.get(it).toDouble() }) }
            }.getOrDefault(0.0)
            PlaneFilter.Desc(V3(c.tx().toDouble(), c.ty().toDouble(), c.tz().toDouble()), src, area, pl)
        }
        // Sol = plan le plus bas dans la bande 1,2–1,6 m sous l'appareil ; une table plus haute n'est pas le sol.
        floorY = PlaneFilter.floorLevel(descs, cameraY)
        val nFloor = descs.count { PlaneFilter.roleOf(it, floorY, cameraY) == PlaneFilter.Role.FLOOR }
        val counts = Triple(nFloor, trackingPlanes.count { it.type == Plane.Type.VERTICAL },
            trackingPlanes.count { it.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING })
        updateCounters(state, counts)
        val diagnoseDescs = planes.map { pl ->
            val poly = pl.polygon
            val area = runCatching {
                if (poly == null) 0.0 else { poly.rewind(); ArGeometry.polygonAreaM2((0 until poly.limit()).map { poly.get(it).toDouble() }) }
            }.getOrDefault(0.0)
            ArPlaneDiagnose.PlaneDesc(pl.type.name, pl.trackingState.name, area, if (poly == null) 0 else poly.limit() / 2)
        }
        val diagnoseStats = ArPlaneDiagnose.stats(diagnoseDescs)
        publishStats(state, reason, planes, subsumedCount, diagnoseStats, cameraY, floorY)
        val q = listOf(p.qx().toDouble(), p.qy().toDouble(), p.qz().toDouble(), p.qw().toDouble())
        // build 199 (point 6): aimed support from the camera elevation — ARCore pose quaternion (Tilt.fromArQuaternion,
        // same convention as SupportFromTilt.elevationFromForward), sensor rotation matrix as fallback. Hysteresis inside classify.
        val elev = runCatching { app.lumisite.core.position.Tilt.fromArQuaternion(q.toDoubleArray()) }.getOrNull() ?: sensorTiltDeg?.invoke()
        val support = elev?.let { SupportFromTilt.classify(it, lastSupport) } ?: lastSupport ?: SupportFromTilt.Support.MUR
        lastSupport = support
        if (aimSurface.value != support.name) aimSurface.value = support.name
        val only = SupportFromTilt.planeSource(support)
        // build 199 (point 2e): PlaneFilter drops detection dust (< 0,25 m²) and furniture (horizontal planes
        // 0,3–2,0 m above the lowest one = the floor) so a table no longer beats the floor in SOL aim.
        val ap = PlaneFilter.forAim(descs, cameraY).map { d ->
            val pl = d.payload; val n = pl.centerPose.yAxis
            Aim.Plane(d.point, V3(n[0].toDouble(), n[1].toDouble(), n[2].toDouble()), d.source) { p ->
                pl.isPoseInPolygon(Pose.makeTranslation(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))
            }
        }.filter { it.source == only }
        // 2f: visée PLAFOND — plafond virtuel à « sol + hauteur sous plafond » quand la hauteur est connue
        // (sol détecté, sinon l'estimation 1,4 m sous l'appareil ; la source du point reste AR_CEILING_ESTIMATED).
        val origin = V3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble())
        val dir = Aim.forward(q)
        val floorLevel = floorY ?: (origin.y - Aim.DEFAULT_CAMERA_HEIGHT_M)
        val virtualCeilingY = if (support == SupportFromTilt.Support.PLAFOND)
            ceilingHeightM?.let { floorLevel + it } else null
        // Retour terrain 22/11 : si ARCore n'a pas de plan mur/plafond, estimation d'après l'inclinaison
        // (source explicite AR_WALL_ESTIMATE / AR_CEILING_ESTIMATED), au lieu de « position à compléter ».
        val extras = when (support) {
            SupportFromTilt.Support.MUR -> listOf(Aim.wallEstimate(origin, dir))
            SupportFromTilt.Support.PLAFOND ->
                if (virtualCeilingY == null) listOf(Aim.ceilingEstimate(origin, floorLevel)) else emptyList()
            else -> emptyList()
        }
        aim.value = Aim.resolve(origin, dir, ap, lastObjectY, true,
            fallbacks = support == SupportFromTilt.Support.SOL, virtualCeilingY = virtualCeilingY, extraPlanes = extras)
    }

    private fun updateCounters(state: TrackingState, counts: Triple<Int, Int, Int>) {
        val effective = if (state == TrackingState.TRACKING) counts else Triple(0, 0, 0)
        val countsNowMs = SystemClock.elapsedRealtime()
        if (countsNowMs - lastCountsMs >= 500L) {
            lastCountsMs = countsNowMs
            if (planeCounts.value != effective) planeCounts.value = effective
            if (planeCount.value != effective.first) planeCount.value = effective.first
        }
    }

    private fun publishStats(state: TrackingState, reason: String?, allPlanes: List<Plane>, subsumed: Int, st: ArPlaneDiagnose.Stats, cameraY: Double, floorY: Double?) {
        // Rate-limit before building the snapshot: avoid allocating maps/formatted strings on every GL frame.
        val nowMs = SystemClock.elapsedRealtime()
        val publish = nowMs - lastStatsMs >= 500L
        val log = nowMs - lastStatsLogMs >= 2_000L
        if (!publish && !log) return
        if (publish) lastStatsMs = nowMs
        if (log) lastStatsLogMs = nowMs
        val snapshot = ArPlaneStats(
            trackingState = state.name,
            failureReason = reason,
            depthSupported = depthSupported,
            depthActive = depthModeName,
            totalPlanes = allPlanes.size,
            trackingPlanes = st.trackingPlanes,
            subsumedPlanes = subsumed,
            byType = st.byType,
            byState = st.byState,
            validPolygons = st.validPolygons,
            invalidPolygons = st.invalidPolygons,
            minAreaM2 = st.minAreaM2,
            maxAreaM2 = st.maxAreaM2,
            avgAreaM2 = st.avgAreaM2,
            rendererGotPolygons = st.rendererGotPolygons,
            cameraYM = cameraY,
            floorYM = floorY,
        )
        if (publish) planeStats.value = snapshot
        if (log) DiagLog.i("ARCORE_DIAG", "tracking=${snapshot.trackingState}${snapshot.failureReason?.let { " reason=$it" } ?: ""} " +
                "depth=${snapshot.depthActive} supported=${snapshot.depthSupported} " +
                "planes=${snapshot.totalPlanes} tracking=${snapshot.trackingPlanes} subsumed=${snapshot.subsumedPlanes} " +
                "type=${snapshot.byType} state=${snapshot.byState} " +
                "polyValid=${snapshot.validPolygons} polyInvalid=${snapshot.invalidPolygons} " +
                "area=${"%.2f".format(snapshot.minAreaM2)}..${"%.2f".format(snapshot.maxAreaM2)} avg=${"%.2f".format(snapshot.avgAreaM2)} " +
                "cameraY=${"%.2f".format(snapshot.cameraYM)} floorY=${snapshot.floorYM?.let { "%.2f".format(it) } ?: "null"} " +
                "rendererGotPolygons=${snapshot.rendererGotPolygons}")
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay?.rotation ?: Surface.ROTATION_0

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!rendering) return
        val s = session ?: return
        if (viewportChanged) { s.setDisplayGeometry(displayRotation(), viewportW, viewportH); viewportChanged = false }
        s.setCameraTextureName(bg.textureId)
        val frame: Frame = try { s.update() } catch (e: Exception) { setError("update: ${e.message}"); return }
        bg.draw(frame)
        val cam = frame.camera
        val nowMs = SystemClock.elapsedRealtime()
        val reason = cam.trackingFailureReason.name.takeIf { it != "NONE" }
        val state = when (cam.trackingState) {
            com.google.ar.core.TrackingState.TRACKING -> { everTracked = true; pausedSinceMs = null; TrackingState.TRACKING }
            com.google.ar.core.TrackingState.PAUSED -> {
                if (!everTracked) TrackingState.INITIALIZING else {
                    val since = pausedSinceMs ?: nowMs.also { pausedSinceMs = it }
                    if (nowMs - since < degradedWindowMs) TrackingState.DEGRADED else TrackingState.LOST
                }
            }
            else -> TrackingState.STOPPED
        }
        val f = stamps()
        val pose = if (state == TrackingState.TRACKING && f != null) {
            val p = cam.pose
            PoseSample(
                f.now(sensorNs = frame.timestamp),
                listOf(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble()),
                listOf(p.qx().toDouble(), p.qy().toDouble(), p.qz().toDouble(), p.qw().toDouble()),
                state, null,
            )
        } else null
        runCatching { updateAim(s, frame, state, reason) }.onFailure { aim.value = AimResult.NoTarget }
        runCatching { updateAnchors(s, nowMs) }
        // 2c: anchors (calibration flags, luminaires) stay drawn — grey, at their last known pose — while the tracking is
        // PAUSED/degraded; only STOPPED removes them. Plane outlines are only drawn while TRACKING (they would freeze too).
        if (state == TrackingState.STOPPED) {
            if (screenLabels.value.isNotEmpty()) screenLabels.value = emptyList()
        } else runCatching {
            val live = state == TrackingState.TRACKING
            val za = cam.pose.zAxis
            val forward = V3(-za[0].toDouble(), -za[1].toDouble(), -za[2].toDouble())
            val labels = lineRenderer.draw(
                cam, viewportW, viewportH,
                if (arCoreEdgesOn.value && live) planes.filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING } else emptyList(),
                anchors.filter { (id, a) -> a.trackingState != com.google.ar.core.TrackingState.STOPPED && anchorKinds[id] != AnchorKind.GROUP }
                    .map { (id, a) -> Mark(id, anchorKinds.getValue(id), V3(a.pose.tx().toDouble(), a.pose.ty().toDouble(), a.pose.tz().toDouble()),
                        live && a.trackingState == com.google.ar.core.TrackingState.TRACKING) },
                rawPolygonsOn.value,
                if (contoursOn.value) siteContourRings else emptyList(),
                measureA.value, measureB.value,
                catalog, luminaireMeta, forward,
            )
            if (screenLabels.value != labels) screenLabels.value = labels
        }
        onFrame(pose, state, reason)
    }
}

/** Minimal external-OES camera background renderer (adapted from the ARCore samples, Apache-2.0). */
private fun ArTracker.lowLight(frame: Frame): Boolean {
    val le = frame.lightEstimate
    if (le.state != LightEstimate.State.VALID) return false
    // pixelIntensity is 0 (black) … 1 (saturated). HDR main-light units were far too low in a bright
    // office, so the torch came on by default (retour terrain 22/11, build 230).
    @Suppress("DEPRECATION")
    return le.pixelIntensity < 0.12f
}

private fun ArTracker.applyTorchInternal(s: Session, on: Boolean) {
    val c = s.config
    c.flashMode = if (on) Config.FlashMode.TORCH else Config.FlashMode.OFF
    s.configure(c)
}

private class BackgroundRenderer {
    var textureId = -1; private set
    private var program = 0
    private var posAttr = 0; private var texAttr = 0
    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val tex: FloatBuffer = floatBuffer(FloatArray(8))

    fun create() {
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); textureId = t[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        val vs = """attribute vec4 a_Position; attribute vec2 a_TexCoord; varying vec2 v_TexCoord;
            void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }"""
        val fs = """#extension GL_OES_EGL_image_external : require
            precision mediump float; varying vec2 v_TexCoord; uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, v_TexCoord); }"""
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(program)
        posAttr = GLES20.glGetAttribLocation(program, "a_Position")
        texAttr = GLES20.glGetAttribLocation(program, "a_TexCoord")
    }

    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quad.position(0); tex.position(0)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, tex)
        }
        if (frame.timestamp == 0L) return
        quad.position(0); tex.position(0)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glVertexAttribPointer(posAttr, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(texAttr, 2, GLES20.GL_FLOAT, false, 0, tex)
        GLES20.glEnableVertexAttribArray(posAttr); GLES20.glEnableVertexAttribArray(texAttr)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posAttr); GLES20.glDisableVertexAttribArray(texAttr)
        GLES20.glDepthMask(true); GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun shader(type: Int, src: String): Int = GLES20.glCreateShader(type).also {
        GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
    }

    private fun floatBuffer(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }
}

/**
 * v176c stripped AR (decision 3): lines only, no fill, no point cloud, no text. build 199 (point 2d): the lines are
 * the intersections of detected planes — plinthes sol∩mur, angles mur∩mur, mur∩plafond — bounded by both polygons
 * and coloured by type (sol vert, murs bleu, plafond violet), plus orange calibration flags and luminaire symbols at
 * local anchors. Raw ARCore polygon outlines only when the diagnostic setting is on. Behaviour read in ARCore's
 * hello_ar_java PlaneRenderer (Apache-2.0, see NOTICE), reimplemented with app.lumisite.core.compute.ArGeometry.
 */
private class LineRenderer {
    private var program = 0; private var aPos = 0; private var uMvp = 0; private var uColor = 0
    private val proj = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private val model = FloatArray(16)

    fun create() {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, "uniform mat4 u_Mvp; attribute vec3 a_Pos; void main() { gl_Position = u_Mvp * vec4(a_Pos, 1.0); }"))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; uniform vec4 u_Color; void main() { gl_FragColor = u_Color; }"))
            GLES20.glLinkProgram(it)
        }
        aPos = GLES20.glGetAttribLocation(program, "a_Pos"); uMvp = GLES20.glGetUniformLocation(program, "u_Mvp"); uColor = GLES20.glGetUniformLocation(program, "u_Color")
    }

    private fun lines(mode: Int, pts: List<V3>, r: Float, g: Float, b: Float) {
        if (pts.size < 2) return
        val buf = ByteBuffer.allocateDirect(pts.size * 12).order(ByteOrder.nativeOrder()).asFloatBuffer()
        pts.forEach { buf.put(it.x.toFloat()); buf.put(it.y.toFloat()); buf.put(it.z.toFloat()) }
        buf.position(0)
        GLES20.glUniform4f(uColor, r, g, b, 1f)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(mode, 0, pts.size)
    }

    private fun segments(s: List<Pair<V3, V3>>, r: Float, g: Float, b: Float) = lines(GLES20.GL_LINES, s.flatMap { listOf(it.first, it.second) }, r, g, b)

    /** build 199 (point 2d): edge colour by plane type — sol vert, murs bleu, plafond violet. An edge takes the
     *  colour of its higher plane (plafond > mur > sol): plinthe sol∩mur en bleu, angle mur∩mur en bleu,
     *  rencontre mur∩plafond en violet. */
    private fun rank(t: Plane.Type) = when (t) { Plane.Type.HORIZONTAL_DOWNWARD_FACING -> 2; Plane.Type.VERTICAL -> 1; else -> 0 }
    private fun color(t: Plane.Type) = when (t) {
        Plane.Type.HORIZONTAL_DOWNWARD_FACING -> Triple(0.75f, 0.4f, 1f)
        Plane.Type.VERTICAL -> Triple(0.25f, 0.55f, 1f)
        else -> Triple(0.2f, 0.9f, 0.35f)
    }

    fun draw(
        cam: com.google.ar.core.Camera, widthPx: Int, heightPx: Int,
        planes: List<Plane>, marks: List<ArTracker.Mark>, rawPolygons: Boolean,
        siteRings: List<List<V3>>, measureA: V3?, measureB: V3?,
        catalog: LsCatalog?, luminaireMeta: Map<String, Pair<String?, String?>>, forward: V3,
    ): List<ArTracker.ScreenLabel> {
        val labels = ArrayList<ArTracker.ScreenLabel>()
        if (program == 0) return labels
        val hasMeasure = measureA != null && measureB != null
        if (planes.isEmpty() && marks.isEmpty() && siteRings.isEmpty() && !hasMeasure) return labels
        cam.getProjectionMatrix(proj, 0, 0.05f, 100f); cam.getViewMatrix(view, 0)
        android.opengl.Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        val viewProj = DoubleArray(16) { vp[it].toDouble() }
        fun project(p: V3) = ArGeometry.project(viewProj, p, widthPx, heightPx)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glLineWidth(3f)
        GLES20.glEnableVertexAttribArray(aPos)
        val wps = planes.mapNotNull { pl ->
            val poly = pl.polygon ?: return@mapNotNull null
            poly.rewind()
            val xz = (0 until poly.limit()).map { poly.get(it).toDouble() }
            if (xz.size < 6) return@mapNotNull null
            pl.centerPose.toMatrix(model, 0)
            val c = pl.centerPose; val n = c.yAxis
            Triple(ArGeometry.PlaneEq(V3(c.tx().toDouble(), c.ty().toDouble(), c.tz().toDouble()), V3(n[0].toDouble(), n[1].toDouble(), n[2].toDouble())),
                ArGeometry.polygonToWorld(DoubleArray(16) { model[it].toDouble() }, xz), pl.type)
        }
        for (i in wps.indices) for (j in i + 1 until wps.size) {
            val (eqA, polyA, ta) = wps[i]; val (eqB, polyB, tb) = wps[j]
            val seg = ArGeometry.edgeSegment(eqA, polyA, eqB, polyB) ?: continue
            val (r, g, b) = color(if (rank(ta) >= rank(tb)) ta else tb)
            segments(listOf(seg), r, g, b)
        }
        if (rawPolygons) for ((_, world, t) in wps) {
            lines(GLES20.GL_LINE_LOOP, world, 1f, 1f, 1f)
            if (t == Plane.Type.VERTICAL) segments(ArGeometry.verticalEdges(world), 1f, 1f, 1f)
        }
        // Contours du site / bâtiment / zones, recalés (cyan).
        for (ring in siteRings) {
            if (ring.size >= 2) lines(GLES20.GL_LINE_STRIP, ring, 0.2f, 0.95f, 0.95f)
        }
        val (right, up) = ArSymbols.billboardAxes(forward)
        for (mark in marks) {
            val grey = !mark.live
            val dim = if (grey) 0.55f else 1f
            when (mark.kind) {
                ArTracker.AnchorKind.FLAG -> segments(ArGeometry.flag(mark.at), dim, if (grey) 0.55f else 0.6f, if (grey) 0.55f else 0f)
                ArTracker.AnchorKind.LUMINAIRE -> {
                    val meta = luminaireMeta[mark.id]
                    val segs = if (catalog != null && meta != null) {
                        val prims = app.lumisite.core.catalog.LsSymbols.glyph(meta.first)
                        ArSymbols.segments(prims, mark.at, right, up)
                    } else ArGeometry.luminaire(mark.at)
                    segments(segs, dim, if (grey) 0.55f else 0.85f, if (grey) 0.55f else 0f)
                    val text = meta?.second?.takeIf { it.isNotBlank() }
                    if (text != null) project(mark.at + V3(0.0, 0.18, 0.0))?.let { (x, y) ->
                        labels += ArTracker.ScreenLabel(x.toFloat(), y.toFloat(), text)
                    }
                }
                ArTracker.AnchorKind.GROUP -> {}
            }
        }
        if (measureA != null && measureB != null) {
            GLES20.glLineWidth(5f)
            segments(ArMeasure.line(measureA, measureB), 1f, 0.92f, 0.2f)
            GLES20.glLineWidth(3f)
            val (at, text) = ArMeasure.label(measureA, measureB)
            project(at)?.let { (x, y) -> labels += ArTracker.ScreenLabel(x.toFloat(), y.toFloat(), text) }
        }
        GLES20.glDisableVertexAttribArray(aPos)
        return labels
    }
}

/**
 * Debug-only ARCore dataset recording and playback (task 2, tests on the development Pixel 7):
 *   adb shell am start -n app.lumisite.android/.MainActivity --es arcore_playback /sdcard/Android/data/app.lumisite.android/files/arcore/x.mp4
 *   adb shell am start -n app.lumisite.android/.MainActivity --es arcore_record auto   (→ files/arcore/rec-<date>.mp4)
 * Ignored in non-debug builds. Playback shows a visible « PLAYBACK » badge on the Session screen.
 */
object ArDebug {
    @Volatile var playbackPath: String? = null
    @Volatile var recordPath: String? = null

    fun fromIntent(ctx: Context, intent: android.content.Intent?, debug: Boolean) {
        if (!debug || intent == null) return
        intent.getStringExtra("arcore_playback")?.takeIf { it.isNotBlank() }?.let { playbackPath = it }
        intent.getStringExtra("arcore_record")?.takeIf { it.isNotBlank() }?.let { v ->
            recordPath = if (v == "auto") java.io.File(ctx.getExternalFilesDir("arcore"),
                "rec-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())}.mp4").path else v
        }
        if (playbackPath != null || recordPath != null) DiagLog.i("ARCORE", "Mode debug : playback=$playbackPath record=$recordPath")
    }
}

/** Réglages > Diagnostic : préférences persistées, relues à l'ouverture de chaque session. */
object ArDiagnose {
    private const val FILE = "lumisite_ar"
    /** « Polygones ARCore bruts » : affiche les contours de polygones en blanc sous les arêtes, pour diagnostiquer la détection. */
    fun rawPolygons(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("polygones_bruts", false)
    fun setRawPolygons(ctx: Context, on: Boolean) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("polygones_bruts", on).apply()
}
