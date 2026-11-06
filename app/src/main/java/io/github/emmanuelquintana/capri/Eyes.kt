package io.github.emmanuelquintana.capri

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * "Capri, mira mi pantalla": her eyes on your screen, with Android's screen capture. Android asks
 * you first, every time she starts looking (that's how it works, and the screen-cast mark shows
 * while she can see). Once: one picture, then she lets go. Live (Settings, or her menu): she keeps
 * the capture until you turn it off, and looks now and then or when the screen changes a lot.
 * The pictures stay in memory, small (720 px wide), and only go to the AI behind her chat.
 * Apps that protect their screen (banks, passwords) come out black: Android blanks them.
 */
class Eyes(private val service: CapriService) {
    private val thread = HandlerThread("capri-eyes").apply { start() }
    private val handler = Handler(thread.looper)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    @Volatile var live = false
        private set
    private var lastPrint: IntArray? = null // a tiny picture of what she saw last time

    val seeing get() = projection != null

    /** One picture now, as base64 JPEG. Asks you first if she can't see yet. Blocking: never on the main thread. */
    fun snap(): String {
        if (!service.getSystemService(PowerManager::class.java)!!.isInteractive) throw IllegalStateException("screen off")
        if (projection == null && !start()) throw IllegalStateException("not allowed")
        val bmp = frame() ?: throw IllegalStateException("no picture")
        lastPrint = print(bmp)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        if (!live) stop()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Live on: asks you now (Android's dialog), keeps seeing until off. False if you said no. */
    fun startLive(): Boolean {
        live = true
        if (projection != null) return true
        val ok = start()
        if (!ok) live = false
        return ok
    }

    fun stopLive() {
        live = false
        stop()
    }

    /** How different the screen is from her last look, 0..1 (live only; 0 when she can't tell). */
    fun changed(): Double {
        val before = lastPrint ?: return 1.0
        val bmp = frame() ?: return 0.0
        val now = print(bmp)
        bmp.recycle()
        var diff = 0L
        for (i in now.indices) diff += kotlin.math.abs(now[i] - before[i])
        return diff / (now.size * 255.0)
    }

    // ---------- the capture ----------

    private fun start(): Boolean {
        val latch = CountDownLatch(1)
        var granted: Intent? = null
        var code = Activity.RESULT_CANCELED
        pending = { c, data ->
            code = c
            granted = data
            latch.countDown()
        }
        service.startActivity(Intent(service, LookActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (!latch.await(90, TimeUnit.SECONDS) || code != Activity.RESULT_OK || granted == null) {
            pending = null
            return false
        }
        pending = null
        // Android wants her service marked as capturing before the capture starts
        val done = CountDownLatch(1)
        android.os.Handler(service.mainLooper).post {
            projecting = true
            service.voiceChanged() // startForeground again, now with the capture's type
            done.countDown()
        }
        done.await(3, TimeUnit.SECONDS)
        val mpm = service.getSystemService(MediaProjectionManager::class.java)!!
        val p = runCatching { mpm.getMediaProjection(code, granted!!) }.onFailure { Diag.add("W", "eyes: ${it.message}") }.getOrNull()
        if (p == null) {
            release()
            return false
        }
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // you stopped it (the cast mark, or the system did): her live look ends with it
                val wasLive = live
                live = false
                handler.post { release() }
                if (wasLive) service.overlay?.emit("look:stopped", "{}")
            }
        }, handler)
        val m = service.resources.displayMetrics
        val w = 720
        val h = (m.heightPixels.toLong() * w / m.widthPixels).toInt()
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        projection = p
        reader = r
        display = runCatching {
            p.createVirtualDisplay("capri-eyes", w, h, m.densityDpi * w / m.widthPixels, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
        }.onFailure { Diag.add("W", "eyes: ${it.message}") }.getOrNull()
        if (display == null) {
            stop()
            return false
        }
        Diag.add("I", "eyes open${if (live) " (live)" else ""}")
        return true
    }

    // the latest frame (waits a moment for the first one)
    private fun frame(): Bitmap? {
        val r = reader ?: return null
        for (i in 0 until 30) {
            val img = runCatching { r.acquireLatestImage() }.getOrNull()
            if (img != null) {
                try {
                    val plane = img.planes[0]
                    val pad = (plane.rowStride - plane.pixelStride * img.width) / plane.pixelStride
                    val wide = Bitmap.createBitmap(img.width + pad, img.height, Bitmap.Config.ARGB_8888)
                    wide.copyPixelsFromBuffer(plane.buffer)
                    if (pad == 0) return wide
                    return Bitmap.createBitmap(wide, 0, 0, img.width, img.height).also { wide.recycle() }
                } finally {
                    img.close()
                }
            }
            Thread.sleep(100)
        }
        return null
    }

    // 16x16 grays: enough to tell another app from the same one
    private fun print(bmp: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        val out = IntArray(256)
        for (y in 0 until 16) for (x in 0 until 16) {
            val c = small.getPixel(x, y)
            out[y * 16 + x] = ((c shr 16 and 255) + (c shr 8 and 255) + (c and 255)) / 3
        }
        small.recycle()
        return out
    }

    private fun stop() {
        projection?.let { runCatching { it.stop() } }
        release()
    }

    private fun release() {
        display?.release()
        display = null
        reader?.close()
        reader = null
        val had = projection != null
        projection = null
        if (had || projecting) android.os.Handler(service.mainLooper).post {
            projecting = false
            service.voiceChanged() // her service without the capture's type again
        }
    }

    fun destroy() {
        live = false
        stop()
        thread.quitSafely()
    }

    companion object {
        /** Her service is capturing the screen right now (its foreground type says so). */
        @Volatile var projecting = false
            private set
        @Volatile private var pending: ((Int, Intent?) -> Unit)? = null

        fun answered(code: Int, data: Intent?) {
            pending?.invoke(code, data)
        }
    }
}

/** Android's "start recording or casting?" dialog, with nothing of the app around it. */
class LookActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val mpm = getSystemService(MediaProjectionManager::class.java)!!
        runCatching { startActivityForResult(mpm.createScreenCaptureIntent(), 1) }.onFailure {
            Eyes.answered(RESULT_CANCELED, null)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Eyes.answered(resultCode, data)
        finish()
    }
}
