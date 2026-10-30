package app.lumisite.android.capture

import android.app.Activity
import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
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
    private val planeRenderer = PlaneRenderer()
    private var planes: List<Plane> = emptyList()
    private var frameNo = 0L

    /** Torch on/off, applied from the caller thread (never session.configure() inside onDrawFrame). Off on pause. */
    fun setTorch(on: Boolean) {
        val s = session ?: return
        runCatching { applyTorchInternal(s, on); torchOn.value = on }.onFailure { lastError = "torche : ${it.message}" }
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
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                depthMode = Config.DepthMode.DISABLED
            }
            s.configure(cfg)
            configNote = "caméra ${s.cameraConfig.imageSize.width}×${s.cameraConfig.imageSize.height}, profondeur ${s.config.depthMode}"
            session = s; lastError = null; true
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"; false
        }
    }

    /** Set to false before tearing down: the GL thread then stops calling into the ARCore session. */
    @Volatile var rendering = true

    fun resume() {
        if (!rendering) return
        try { session?.resume() } catch (e: Exception) { lastError = "resume: ${e.message}" }
    }

    fun pause() {
        // Flash back to OFF in the config too, otherwise ARCore relights it on resume while the UI says "off".
        session?.let { s -> runCatching { applyTorchInternal(s, false) } }
        torchOn.value = false
        try { session?.pause() } catch (e: Exception) { lastError = "pause: ${e.message}" }
    }

    /** Call only once the GLSurfaceView is paused (no concurrent onDrawFrame). Never throws. */
    fun close() {
        rendering = false
        val s = session ?: return
        session = null
        try { s.pause() } catch (_: Exception) {}
        try { s.close() } catch (e: Exception) { lastError = "close: ${e.message}" }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        bg.create()
        planeRenderer.create()
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
            planes = s.getAllTrackables(Plane::class.java).filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.subsumedBy == null }
        }
        val planes = planes.filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING }
        if (planeCount.value != planes.size) planeCount.value = planes.size
        val ap = planes.map { pl ->
            val c = pl.centerPose
            Aim.Plane(V3(c.tx().toDouble(), c.ty().toDouble(), c.tz().toDouble()), Aim.UP, "AR_PLANE") { p ->
                pl.isPoseInPolygon(Pose.makeTranslation(p.x.toFloat(), p.y.toFloat(), p.z.toFloat()))
            }
        }
        val p = cam.pose
        val q = listOf(p.qx().toDouble(), p.qy().toDouble(), p.qz().toDouble(), p.qw().toDouble())
        aim.value = Aim.resolve(V3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble()), Aim.forward(q), ap, lastObjectY, true)
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
        val frame: Frame = try { s.update() } catch (e: Exception) { lastError = "update: ${e.message}"; return }
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
        if (state == TrackingState.TRACKING) runCatching { planeRenderer.draw(cam, planes) }
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

/** Detected horizontal planes drawn as translucent cyan polygons (as in the ARCore samples), so the user sees what is aimed at. */
private class PlaneRenderer {
    private var program = 0; private var aPos = 0; private var uMvp = 0; private var uColor = 0
    private val proj = FloatArray(16); private val view = FloatArray(16); private val model = FloatArray(16)
    private val vp = FloatArray(16); private val mvp = FloatArray(16)

    fun create() {
        fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, "uniform mat4 u_Mvp; attribute vec2 a_Pos; void main() { gl_Position = u_Mvp * vec4(a_Pos.x, 0.0, a_Pos.y, 1.0); }"))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, "precision mediump float; uniform vec4 u_Color; void main() { gl_FragColor = u_Color; }"))
            GLES20.glLinkProgram(it)
        }
        aPos = GLES20.glGetAttribLocation(program, "a_Pos"); uMvp = GLES20.glGetUniformLocation(program, "u_Mvp"); uColor = GLES20.glGetUniformLocation(program, "u_Color")
    }

    fun draw(cam: com.google.ar.core.Camera, planes: List<Plane>) {
        if (program == 0 || planes.isEmpty()) return
        cam.getProjectionMatrix(proj, 0, 0.05f, 100f); cam.getViewMatrix(view, 0)
        android.opengl.Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniform4f(uColor, 0f, 0.9f, 1f, 0.25f)
        for (pl in planes) {
            val poly = pl.polygon ?: continue
            val n = poly.limit() / 2
            if (n < 3) continue
            pl.centerPose.toMatrix(model, 0)
            android.opengl.Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            poly.rewind()
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, poly)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, n)
            GLES20.glDisableVertexAttribArray(aPos)
        }
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}
