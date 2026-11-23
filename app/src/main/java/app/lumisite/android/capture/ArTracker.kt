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
    var session: Session? = null; private set
    var lastError: String? = null; private set
    private fun setError(msg: String) { lastError = msg; DiagLog.e("ARCORE", msg) }

    /** Debug ARCore recording / playback (task 2): set from launch extras, see [ArDebug]. */
    val playback = MutableStateFlow(false)
    val recording = MutableStateFlow(false)
    private var datasetApplied = false
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
    @Volatile var configNote: String = ""; private set
    private var autoTorchChecked = false

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val lineRenderer = LineRenderer()
    private var planes: List<Plane> = emptyList()
    private var frameNo = 0L

    /** D6: ARCore anchors (control points, object groups), created / detached on the GL thread, poses reported at most once per second. */
    /** v176c: what is drawn at an anchor: calibration flag, luminaire symbol, nothing (object groups). */
    enum class AnchorKind { FLAG, LUMINAIRE, GROUP }
    private val anchorAdds = java.util.concurrent.ConcurrentLinkedQueue<Triple<String, PoseSample, AnchorKind>>()
    private val anchorDrops = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val anchors = HashMap<String, com.google.ar.core.Anchor>()
    private val anchorKinds = HashMap<String, AnchorKind>()
    /** v176c « Contours » switch (stripped AR lines), plane counters (sol, murs, plafond) and surface chosen at the survey (SOL / MUR / PLAFOND, null = all). */
    val contoursOn = MutableStateFlow(true)
    val planeCounts = MutableStateFlow(Triple(0, 0, 0))
    /** v176g § 2a: surface deduced from the camera tilt (±35°, hysteresis 5°), replaces the Sol / Mur / Plafond chips. */
    val support = MutableStateFlow(app.lumisite.core.compute.Support.MUR)
    /** v176g § 2f: ceiling height of the site (m) for the virtual ceiling; floor height (session frame) once a floor is known. */
    @Volatile var ceilingHeightM: Double? = null
    val floorY = MutableStateFlow<Double?>(null)
    /** v176g § 2d: raw ARCore plane outlines only as a diagnostic option; room edges (plane intersections) by default. */
    val rawPolygons = MutableStateFlow(false)
    /** v176g § 2e: directions (rad, session x/z frame) of the site walls once calibrated; walls aligned within 10° are preferred. */
    @Volatile var siteWallDirsRad: List<Double> = emptyList()
    private var planeInfos: List<Pair<Plane, app.lumisite.core.compute.ArPlanes.Info>> = emptyList()
    private var roomEdges: List<app.lumisite.core.compute.ArPlanes.Edge> = emptyList()
    private var lastAnchorReportMs = 0L
    @Volatile var onAnchors: ((Map<String, Pair<List<Double>, List<Double>>>) -> Unit)? = null
    fun requestAnchor(id: String, pose: PoseSample, kind: AnchorKind = AnchorKind.GROUP) { anchorAdds += Triple(id, pose, kind) }
    fun dropAnchor(id: String) { anchorDrops += id }
    /** v176g § 2c: anchor recreated from the plan (resumed session, calibration restored); ignored if [id] already has one. */
    private val anchorEnsure = java.util.concurrent.ConcurrentLinkedQueue<Triple<String, V3, AnchorKind>>()
    fun ensureAnchor(id: String, at: V3, kind: AnchorKind) { anchorEnsure += Triple(id, at, kind) }

    private fun updateAnchors(s: Session, nowMs: Long) {
        while (true) {
            val (id, p, kind) = anchorAdds.poll() ?: break
            runCatching {
                anchors[id] = s.createAnchor(Pose(p.p.map { it.toFloat() }.toFloatArray(), p.q.map { it.toFloat() }.toFloatArray()))
                anchorKinds[id] = kind
            }.onFailure { setError("ancre $id : ${it.message}") }
        }
        while (true) {
            val (id, at, kind) = anchorEnsure.poll() ?: break
            if (id in anchors) continue
            runCatching {
                anchors[id] = s.createAnchor(Pose.makeTranslation(at.x.toFloat(), at.y.toFloat(), at.z.toFloat()))
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
            // 30 fps, no depth sensor, largest image size first.
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
                depthMode = Config.DepthMode.DISABLED
            }
            s.configure(cfg)
            configNote = "caméra ${s.cameraConfig.imageSize.width}×${s.cameraConfig.imageSize.height}, profondeur ${s.config.depthMode}"
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


    private fun updateAim(s: Session, frame: Frame, state: TrackingState) {
        val cam = frame.camera
        if (!autoTorchChecked && state == TrackingState.TRACKING) { autoTorchChecked = true; if (lowLight(frame)) main.post { setTorch(true) } }
        if (state != TrackingState.TRACKING) { aim.value = AimResult.NoTarget; return }
        // Plane list sampled every 10 frames (getAllTrackables is costly); tracking state re-checked every frame.
        if (frameNo++ % 10 == 0L) {
            planes = s.getAllTrackables(Plane::class.java).filter { it.subsumedBy == null }
            planeInfos = planes.filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING }.mapNotNull { pl -> runCatching { pl to info(pl) }.getOrNull() }
            roomEdges = app.lumisite.core.compute.ArPlanes.roomEdges(planeInfos.map { it.second })
            val fy = app.lumisite.core.compute.ArPlanes.floorY(planeInfos.map { it.second })
            if (floorY.value != fy) floorY.value = fy
        }
        val planes = planes.filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING }
        val counts = Triple(planes.count { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }, planes.count { it.type == Plane.Type.VERTICAL },
            planes.count { it.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING })
        if (planeCounts.value != counts) planeCounts.value = counts
        if (planeCount.value != counts.first) planeCount.value = counts.first
        val p = cam.pose
        val q = listOf(p.qx().toDouble(), p.qy().toDouble(), p.qz().toDouble(), p.qw().toDouble())
        val sup = app.lumisite.core.compute.SupportFromTilt.classify(app.lumisite.core.position.Tilt.fromArQuaternion(q.toDoubleArray()), support.value)
        if (support.value != sup) support.value = sup
        // Floor aim: the floor (± 10 cm, ≥ 0.25 m²), never furniture; walls aligned on the site walls when calibrated.
        val infos = planeInfos.filter { it.first.trackingState == com.google.ar.core.TrackingState.TRACKING }
        val keep = HashSet<Plane>()
        keep += app.lumisite.core.compute.ArPlanes.floorCandidates(infos.map { it.second }).mapNotNull { i -> infos.firstOrNull { it.second === i }?.first }
        keep += app.lumisite.core.compute.ArPlanes.alignedWalls(infos.filter { it.second.kind == app.lumisite.core.compute.ArPlanes.Kind.WALL }.map { it.second }, siteWallDirsRad)
            .mapNotNull { i -> infos.firstOrNull { it.second === i }?.first }
        keep += infos.filter { it.second.kind == app.lumisite.core.compute.ArPlanes.Kind.CEILING }.map { it.first }
        val ap = planes.filter { it in keep }.map { pl ->
            val c = pl.centerPose
            val n = c.yAxis
            val src = when (pl.type) { Plane.Type.VERTICAL -> "AR_WALL"; Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "AR_CEILING"; else -> "AR_PLANE" }
            Aim.Plane(V3(c.tx().toDouble(), c.ty().toDouble(), c.tz().toDouble()), V3(n[0].toDouble(), n[1].toDouble(), n[2].toDouble()), src) { p ->
                pl.isPoseInPolygon(Pose.makeTranslation(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))
            }
        }
        aim.value = Aim.resolveSupport(V3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble()), Aim.forward(q), ap, sup, lastObjectY, true, floorY.value, ceilingHeightM)
    }

    private fun info(pl: Plane): app.lumisite.core.compute.ArPlanes.Info {
        val c = pl.centerPose; val n = c.yAxis
        val m = FloatArray(16); c.toMatrix(m, 0)
        val poly = pl.polygon; poly.rewind()
        val xz = DoubleArray(poly.limit()) { poly.get(it).toDouble() }.toList()
        val kind = when (pl.type) { Plane.Type.VERTICAL -> app.lumisite.core.compute.ArPlanes.Kind.WALL; Plane.Type.HORIZONTAL_DOWNWARD_FACING -> app.lumisite.core.compute.ArPlanes.Kind.CEILING; else -> app.lumisite.core.compute.ArPlanes.Kind.FLOOR }
        return app.lumisite.core.compute.ArPlanes.Info(System.identityHashCode(pl).toString(), kind, V3(c.tx().toDouble(), c.ty().toDouble(), c.tz().toDouble()),
            V3(n[0].toDouble(), n[1].toDouble(), n[2].toDouble()), app.lumisite.core.compute.ArGeometry.polygonToWorld(DoubleArray(16) { m[it].toDouble() }, xz))
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
        runCatching { updateAim(s, frame, state) }.onFailure { aim.value = AimResult.NoTarget }
        runCatching { updateAnchors(s, nowMs) }
        // v176g § 2c: anchors drawn while tracking is degraded too (last known pose, grey); only STOPPED anchors disappear.
        if (state != TrackingState.STOPPED && state != TrackingState.UNAVAILABLE) runCatching {
            val tracking = state == TrackingState.TRACKING
            lineRenderer.draw(cam,
                if (contoursOn.value && tracking && rawPolygons.value) planes.filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING } else emptyList(),
                if (contoursOn.value && tracking) roomEdges else emptyList(),
                anchors.filter { (id, a) -> a.trackingState != com.google.ar.core.TrackingState.STOPPED && anchorKinds[id] != AnchorKind.GROUP }
                    .map { (id, a) -> Triple(anchorKinds.getValue(id), V3(a.pose.tx().toDouble(), a.pose.ty().toDouble(), a.pose.tz().toDouble()),
                        tracking && a.trackingState == com.google.ar.core.TrackingState.TRACKING) })
        }
        onFrame(pose, state, reason)
    }
}

/** Minimal external-OES camera background renderer (adapted from the ARCore samples, Apache-2.0). */
private fun ArTracker.lowLight(frame: Frame): Boolean {
    val le = frame.lightEstimate
    if (le.state != LightEstimate.State.VALID) return false
    val i = le.environmentalHdrMainLightIntensity
    return 0.2126 * i[0] + 0.7152 * i[1] + 0.0722 * i[2] < 0.15 // threshold to tune on site
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
 * v176c stripped AR (decision 3): lines only, no fill, no point cloud, no text. Plane outlines (GL_LINE_LOOP), vertical
 * wall edges, orange calibration flags and luminaire symbols at local anchors. Behaviour read in ARCore's hello_ar_java
 * PlaneRenderer (Apache-2.0, see NOTICE), reimplemented with app.lumisite.core.compute.ArGeometry.
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

    fun draw(cam: com.google.ar.core.Camera, planes: List<Plane>, edges: List<app.lumisite.core.compute.ArPlanes.Edge>, marks: List<Triple<ArTracker.AnchorKind, V3, Boolean>>) {
        if (program == 0 || (planes.isEmpty() && edges.isEmpty() && marks.isEmpty())) return
        cam.getProjectionMatrix(proj, 0, 0.05f, 100f); cam.getViewMatrix(view, 0)
        android.opengl.Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glLineWidth(3f)
        GLES20.glEnableVertexAttribArray(aPos)
        for (pl in planes) {
            val poly = pl.polygon ?: continue
            poly.rewind()
            val xz = DoubleArray(poly.limit()) { poly.get(it).toDouble() }.toList()
            if (xz.size < 6) continue
            pl.centerPose.toMatrix(model, 0)
            val world = app.lumisite.core.compute.ArGeometry.polygonToWorld(DoubleArray(16) { model[it].toDouble() }, xz)
            lines(GLES20.GL_LINE_LOOP, world, 1f, 1f, 1f)
            if (pl.type == Plane.Type.VERTICAL) segments(app.lumisite.core.compute.ArGeometry.verticalEdges(world), 1f, 1f, 1f)
        }
        // Room edges: skirting green, vertical corners blue, cornice violet.
        for (e in edges) when (e.kind) {
            app.lumisite.core.compute.ArPlanes.EdgeKind.PLINTHE -> segments(listOf(e.a to e.b), 0.2f, 0.85f, 0.3f)
            app.lumisite.core.compute.ArPlanes.EdgeKind.ANGLE -> segments(listOf(e.a to e.b), 0.3f, 0.6f, 1f)
            app.lumisite.core.compute.ArPlanes.EdgeKind.CORNICHE -> segments(listOf(e.a to e.b), 0.7f, 0.4f, 1f)
        }
        for ((kind, at, live) in marks) when (kind) {
            ArTracker.AnchorKind.FLAG -> if (live) segments(app.lumisite.core.compute.ArGeometry.flag(at), 1f, 0.6f, 0f) else segments(app.lumisite.core.compute.ArGeometry.flag(at), 0.6f, 0.6f, 0.6f)
            ArTracker.AnchorKind.LUMINAIRE -> if (live) segments(app.lumisite.core.compute.ArGeometry.luminaire(at), 1f, 0.85f, 0f) else segments(app.lumisite.core.compute.ArGeometry.luminaire(at), 0.6f, 0.6f, 0.6f)
            ArTracker.AnchorKind.GROUP -> {}
        }
        GLES20.glDisableVertexAttribArray(aPos)
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
