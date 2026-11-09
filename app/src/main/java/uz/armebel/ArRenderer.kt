package uz.armebel

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Barcha metodlar GL oqimida chaqiriladi (GLSurfaceView.queueEvent orqali). */
class ArRenderer(
    private val rotationProvider: () -> Int,
    private val onStatus: (String) -> Unit,
    private val onShot: (Bitmap) -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var session: Session? = null
    var currentModel = 0

    private class Placed(var anchor: Anchor, val model: Model, var scale: Float = 1f, var rotY: Float = 0f)

    private val objects = ArrayList<Placed>()
    private var selected = -1
    private var dragging = false
    private var lastFrame: Frame? = null

    private var viewW = 1
    private var viewH = 1
    private var viewportChanged = true
    @Volatile private var shotRequested = false
    private var lastStatus = ""

    private var texId = 0
    private var bgProg = 0; private var bgPos = 0; private var bgTex = 0; private var bgSampler = 0
    private var objProg = 0; private var aPos = 0; private var aNor = 0
    private var uMvp = 0; private var uModel = 0; private var uColor = 0; private var uLit = 0

    private lateinit var quad: FloatBuffer
    private lateinit var quadTex: FloatBuffer
    private lateinit var cube: FloatBuffer

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val base = FloatArray(16)
    private val m = FloatArray(16)
    private val mvp = FloatArray(16)

    // ---------- GL hayot sikli ----------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        texId = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        bgProg = program(BG_VS, BG_FS)
        bgPos = GLES20.glGetAttribLocation(bgProg, "a_Position")
        bgTex = GLES20.glGetAttribLocation(bgProg, "a_TexCoord")
        bgSampler = GLES20.glGetUniformLocation(bgProg, "sTexture")

        objProg = program(OBJ_VS, OBJ_FS)
        aPos = GLES20.glGetAttribLocation(objProg, "a_Position")
        aNor = GLES20.glGetAttribLocation(objProg, "a_Normal")
        uMvp = GLES20.glGetUniformLocation(objProg, "u_MVP")
        uModel = GLES20.glGetUniformLocation(objProg, "u_Model")
        uColor = GLES20.glGetUniformLocation(objProg, "u_Color")
        uLit = GLES20.glGetUniformLocation(objProg, "u_Lit")

        quad = fb(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        quadTex = fb(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
        cube = buildCube()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewW = width
        viewH = height
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        render()
        if (shotRequested) {
            shotRequested = false
            val buf = ByteBuffer.allocateDirect(viewW * viewH * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, viewW, viewH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val bmp = Bitmap.createBitmap(viewW, viewH, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            val flip = android.graphics.Matrix().apply { preScale(1f, -1f) }
            onShot(Bitmap.createBitmap(bmp, 0, 0, viewW, viewH, flip, false))
        }
    }

    private fun render() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (viewportChanged) {
            s.setDisplayGeometry(rotationProvider(), viewW, viewH)
            viewportChanged = false
        }
        s.setCameraTextureName(texId)
        val frame = try { s.update() } catch (e: Exception) { return }
        lastFrame = frame
        if (frame.hasDisplayGeometryChanged()) {
            quad.rewind(); quadTex.rewind()
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
                Coordinates2d.TEXTURE_NORMALIZED, quadTex
            )
            quad.rewind(); quadTex.rewind()
        }
        drawBackground()

        val cam = frame.camera
        if (cam.trackingState != TrackingState.TRACKING) {
            status("Kuzatuv yo'qoldi. Telefonni sekin harakatlantiring…")
            return
        }
        cam.getProjectionMatrix(proj, 0, 0.05f, 50f)
        cam.getViewMatrix(view, 0)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        drawScene(s)
    }

    // ---------- Chizish ----------
    private fun drawBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(bgProg)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(bgSampler, 0)
        GLES20.glEnableVertexAttribArray(bgPos)
        GLES20.glEnableVertexAttribArray(bgTex)
        GLES20.glVertexAttribPointer(bgPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(bgTex, 2, GLES20.GL_FLOAT, false, 0, quadTex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(bgPos)
        GLES20.glDisableVertexAttribArray(bgTex)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawBox(model: FloatArray, color: Int, alpha: Float, lit: Float) {
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform4f(uColor, Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f, alpha)
        GLES20.glUniform1f(uLit, lit)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
    }

    private fun drawScene(s: Session) {
        GLES20.glUseProgram(objProg)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        cube.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, cube)
        cube.position(3)
        GLES20.glVertexAttribPointer(aNor, 3, GLES20.GL_FLOAT, false, 24, cube)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNor)

        // 1) Obyektlar (shaffof emas)
        for (o in objects) {
            if (o.anchor.trackingState != TrackingState.TRACKING) continue
            o.anchor.pose.toMatrix(base, 0)
            Matrix.rotateM(base, 0, o.rotY, 0f, 1f, 0f)
            Matrix.scaleM(base, 0, o.scale, o.scale, o.scale)
            for (p in o.model.parts) {
                System.arraycopy(base, 0, m, 0, 16)
                Matrix.translateM(m, 0, p.x, p.y, p.z)
                Matrix.scaleM(m, 0, p.w, p.h, p.d)
                drawBox(m, p.color, 1f, 1f)
            }
        }

        // 2) Shaffof elementlar: tanlov belgisi va aniqlangan sirtlar
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        if (selected in objects.indices) {
            val o = objects[selected]
            if (o.anchor.trackingState == TrackingState.TRACKING) {
                o.anchor.pose.toMatrix(m, 0)
                val sz = (o.model.radius * 2f + 0.12f) * o.scale
                Matrix.scaleM(m, 0, sz, 0.006f, sz)
                drawBox(m, 0xFFFFC107.toInt(), 0.55f, 0f)
            }
        }
        val planes = s.getAllTrackables(Plane::class.java).filter {
            it.trackingState == TrackingState.TRACKING && it.subsumedBy == null &&
                it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
        }
        for (pl in planes) {
            pl.centerPose.toMatrix(m, 0)
            Matrix.scaleM(m, 0, pl.extentX, 0.004f, pl.extentZ)
            drawBox(m, 0xFF29B6F6.toInt(), 0.22f, 0f)
        }
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNor)

        status(
            when {
                planes.isEmpty() -> "Telefonni sekin aylantirib, pol yoki stolni skanerlang…"
                objects.isEmpty() -> "Sirt topildi. Buyum qo'yish uchun sirtga bosing"
                selected in objects.indices ->
                    "Tanlangan: ${objects[selected].model.name} • surib ko'chiring, ikki barmoq bilan kattalashtiring"
                else -> "Buyumni tanlash uchun unga bosing"
            }
        )
    }

    // ---------- Boshqaruv (GL oqimida) ----------
    fun onTap(x: Float, y: Float) {
        val idx = findObjectAt(x, y)
        if (idx >= 0) { selected = idx; lastStatus = ""; return }
        val hit = planeHit(x, y) ?: return
        if (objects.size >= 25) objects.removeAt(0).anchor.detach()
        objects.add(Placed(hit.createAnchor(), CATALOG[currentModel]))
        selected = objects.lastIndex
        lastStatus = ""
    }

    fun onDown(x: Float, y: Float) {
        dragging = selected in objects.indices && distanceTo(objects[selected], x, y) < 220f
    }

    fun onDrag(x: Float, y: Float) {
        if (!dragging || selected !in objects.indices) return
        val hit = planeHit(x, y) ?: return
        val o = objects[selected]
        val old = o.anchor
        o.anchor = hit.createAnchor()
        old.detach()
    }

    fun scaleSelected(f: Float) {
        if (selected !in objects.indices) return
        val o = objects[selected]
        o.scale = (o.scale * f).coerceIn(0.3f, 3f)
    }

    fun rotateSelected(deg: Float) {
        if (selected in objects.indices) objects[selected].rotY += deg
    }

    fun deleteSelected() {
        if (selected !in objects.indices) return
        objects.removeAt(selected).anchor.detach()
        selected = objects.lastIndex
        lastStatus = ""
    }

    fun clearAll() {
        objects.forEach { it.anchor.detach() }
        objects.clear()
        selected = -1
        lastStatus = ""
    }

    fun requestScreenshot() { shotRequested = true }

    // ---------- Yordamchi ----------
    private fun planeHit(x: Float, y: Float): HitResult? {
        val f = lastFrame ?: return null
        for (h in f.hitTest(x, y)) {
            val t = h.trackable
            if (t is Plane && t.isPoseInPolygon(h.hitPose) && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING) return h
        }
        return null
    }

    private fun project(wx: Float, wy: Float, wz: Float): FloatArray? {
        val o = FloatArray(4)
        Matrix.multiplyMV(o, 0, vp, 0, floatArrayOf(wx, wy, wz, 1f), 0)
        if (o[3] <= 0f) return null
        return floatArrayOf((o[0] / o[3] * 0.5f + 0.5f) * viewW, (1f - (o[1] / o[3] * 0.5f + 0.5f)) * viewH)
    }

    private fun distanceTo(o: Placed, x: Float, y: Float): Float {
        val p = o.anchor.pose
        val s = project(p.tx(), p.ty() + 0.4f * o.scale, p.tz()) ?: return Float.MAX_VALUE
        return Math.hypot((s[0] - x).toDouble(), (s[1] - y).toDouble()).toFloat()
    }

    private fun findObjectAt(x: Float, y: Float): Int {
        var best = -1
        var bestD = 160f
        for (i in objects.indices) {
            val d = distanceTo(objects[i], x, y)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun status(t: String) {
        if (t != lastStatus) { lastStatus = t; onStatus(t) }
    }

    private fun fb(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

    private fun buildCube(): FloatBuffer {
        val normals = arrayOf(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f)
        )
        fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
            a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
        )
        val out = ArrayList<Float>()
        val signs = arrayOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
        for (n in normals) {
            val helper = if (Math.abs(n[1]) > 0.5f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
            val u = cross(n, helper)
            val v = cross(n, u)
            val corners = signs.map { (a, b) ->
                floatArrayOf(
                    n[0] * 0.5f + u[0] * 0.5f * a + v[0] * 0.5f * b,
                    n[1] * 0.5f + u[1] * 0.5f * a + v[1] * 0.5f * b,
                    n[2] * 0.5f + u[2] * 0.5f * a + v[2] * 0.5f * b
                )
            }
            for (i in intArrayOf(0, 1, 2, 0, 2, 3)) {
                out.addAll(corners[i].toList()); out.addAll(n.toList())
            }
        }
        return fb(out.toFloatArray())
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun program(vs: String, fs: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        private const val BG_VS = """
            attribute vec4 a_Position; attribute vec2 a_TexCoord; varying vec2 v_TexCoord;
            void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }"""
        private const val BG_FS = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float; varying vec2 v_TexCoord; uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, v_TexCoord); }"""
        private const val OBJ_VS = """
            uniform mat4 u_MVP; uniform mat4 u_Model; uniform vec4 u_Color; uniform float u_Lit;
            attribute vec4 a_Position; attribute vec3 a_Normal; varying vec4 v_Color;
            void main() {
                vec3 n = normalize((u_Model * vec4(a_Normal, 0.0)).xyz);
                float d = max(dot(n, normalize(vec3(0.3, 1.0, 0.5))), 0.0);
                float sh = 0.45 + 0.55 * d;
                v_Color = vec4(mix(u_Color.rgb, u_Color.rgb * sh, u_Lit), u_Color.a);
                gl_Position = u_MVP * a_Position;
            }"""
        private const val OBJ_FS = """
            precision mediump float; varying vec4 v_Color;
            void main() { gl_FragColor = v_Color; }"""
    }
}
