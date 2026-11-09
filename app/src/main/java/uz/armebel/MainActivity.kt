package uz.armebel

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

class MainActivity : Activity() {

    private lateinit var surface: GLSurfaceView
    private lateinit var renderer: ArRenderer
    private lateinit var statusView: TextView
    private var session: Session? = null
    private var installRequested = true
    private val modelButtons = ArrayList<Button>()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        renderer = ArRenderer(
            rotationProvider = { windowManager.defaultDisplay.rotation },
            onStatus = { t -> runOnUiThread { statusView.text = t } },
            onShot = { bmp -> saveBitmap(bmp) }
        )
        surface = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                val x = e.x; val y = e.y
                surface.queueEvent { renderer.onDown(x, y) }
                return true
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val x = e.x; val y = e.y
                surface.queueEvent { renderer.onTap(x, y) }
                return true
            }
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (e2.pointerCount == 1) {
                    val x = e2.x; val y = e2.y
                    surface.queueEvent { renderer.onDrag(x, y) }
                }
                return true
            }
        })
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val f = d.scaleFactor
                surface.queueEvent { renderer.scaleSelected(f) }
                return true
            }
        })
        surface.setOnTouchListener { _, e ->
            scale.onTouchEvent(e)
            if (!scale.isInProgress) gesture.onTouchEvent(e)
            true
        }

        // ---- Interfeys ----
        val root = FrameLayout(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f
            setBackgroundColor(0x99000000.toInt())
            setPadding(px(12), px(36), px(12), px(8))
            text = "AR tayyorlanmoqda…"
        }
        root.addView(statusView, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; textSize = 12f; isAllCaps = false
            setOnClickListener { onClick() }
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(0x66000000) }
        val items = listOf(
            btn("⟲") { surface.queueEvent { renderer.rotateSelected(-15f) } },
            btn("⟳") { surface.queueEvent { renderer.rotateSelected(15f) } },
            btn("O'chirish") { surface.queueEvent { renderer.deleteSelected() } },
            btn("Tozalash") { surface.queueEvent { renderer.clearAll() } },
            btn("Skrinshot") { surface.queueEvent { renderer.requestScreenshot() } }
        )
        items.forEach { controls.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }

        val catalogRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(px(4), px(4), px(4), px(8)) }
        CATALOG.forEachIndexed { i, model ->
            val b = Button(this).apply {
                text = model.name; textSize = 13f; isAllCaps = false
                setTextColor(if (Color.luminance(model.swatch) > 0.6f) Color.BLACK else Color.WHITE)
                setBackgroundColor(model.swatch)
                alpha = 0.6f
                setOnClickListener { chooseModel(i) }
            }
            modelButtons.add(b)
            catalogRow.addView(b, LinearLayout.LayoutParams(px(96), px(64)).apply { setMargins(px(4), 0, px(4), 0) })
        }
        val scroller = HorizontalScrollView(this).apply {
            setBackgroundColor(0x66000000)
            isHorizontalScrollBarEnabled = false
            addView(catalogRow)
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(controls, ViewGroup.LayoutParams(-1, -2))
            addView(scroller, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        chooseModel(0)
    }

    private fun chooseModel(i: Int) {
        renderer.currentModel = i
        modelButtons.forEachIndexed { idx, b -> b.alpha = if (idx == i) 1f else 0.6f }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (session == null) {
                when (ArCoreApk.getInstance().requestInstall(this, installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = false; return }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
                    return
                }
                val s = Session(this)
                val config = Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    lightEstimationMode = Config.LightEstimationMode.DISABLED
                }
                s.configure(config)
                session = s
                renderer.session = s
            }
            session?.resume()
            surface.onResume()
        } catch (e: Exception) {
            statusView.text = "ARCore xatosi: ${e.javaClass.simpleName}. Qurilma ARCore'ni qo'llab-quvvatlamasligi mumkin."
        }
    }

    override fun onPause() {
        super.onPause()
        surface.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "AR uchun kamera ruxsati kerak", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveBitmap(bmp: Bitmap) {
        Thread {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "AR_${System.currentTimeMillis()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ARMebel")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            val ok = uri != null && contentResolver.openOutputStream(uri)?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            } == true
            runOnUiThread {
                Toast.makeText(this, if (ok) "Skrinshot Pictures/ARMebel papkasiga saqlandi" else "Saqlab bo'lmadi", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
