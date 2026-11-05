package io.github.emmanuelquintana.capri

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Her window, as main.js's createOverlay(): full screen, transparent, always on top, and the
 * touches pass through it to your apps (FLAG_NOT_TOUCHABLE, as setIgnoreMouseEvents on the
 * desktop). Her renderer, copied unchanged from capri-desktop, runs in it.
 *
 * Where she can be touched (her body, her panel, her biochip), small invisible windows sit on top
 * (TouchPads) and hand the finger over to her page: the rest of the screen stays yours.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
class Overlay(private val context: Context, private val bridge: HostBridge) {
    private val wm = context.getSystemService(WindowManager::class.java)!!
    private val main = Handler(Looper.getMainLooper())
    private var params: WindowManager.LayoutParams? = null
    var webView: WebView? = null
        private set
    private val pads = TouchPads()
    private var paused = false

    fun show() {
        if (webView != null) return
        val web = WebView(context)
        webView = web
        web.setBackgroundColor(Color.TRANSPARENT)
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.isHapticFeedbackEnabled = false
        // a long press is her right-click (touch.js): no text selection, no system menu
        web.isLongClickable = false
        web.setOnLongClickListener { true }
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // her sounds play without a tap first
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100 // her type stays her size, whatever the phone's font scale
        }
        web.addJavascriptInterface(bridge, "CapriNative")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.host != HOST) return null
                return serve(request.url.path ?: "/")
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.host != HOST

            override fun onPageFinished(view: WebView, url: String) {
                bridge.log.add("I", "page loaded")
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                // her page crashed or was killed for memory: a fresh one, not the whole app going down
                bridge.log.add("E", "renderer gone (crash: ${detail.didCrash()})")
                main.post { destroy(); main.postDelayed({ show() }, 500) }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val line = "${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})"
                val error = m.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                Log.println(if (error) Log.ERROR else Log.DEBUG, "capri-js", line)
                if (error || m.messageLevel() == ConsoleMessage.MessageLevel.WARNING) bridge.log.add(if (error) "E" else "W", line)
                return true
            }
        }
        // Back while typing to her: Escape, which closes her chat as on the desktop
        web.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                web.evaluateJavascript(ESCAPE, null)
                true
            } else {
                false
            }
        }

        web.setOnTouchListener { _, ev -> outside(ev) }

        val p = baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            // Android 12+ lets touches through another app's window only if it's at most 80 % opaque
            // (untrusted touch occlusion). Her pixels stay as drawn; the window as a whole is 0.8.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alpha = MAX_PASSTHROUGH_ALPHA
        }
        params = p
        // shown from the start: a hidden WebView gets no animation frames, and her page waits on them
        wm.addView(web, p)
        watchKeyboard(web)
        web.loadUrl("https://$HOST/capri/src/renderer/index.html")
    }

    /** The screen turned: her window laid out again at the new size (it fills the screen either way). */
    fun turned() = main.post {
        val web = webView ?: return@post
        val p = params ?: return@post
        try { wm.updateViewLayout(web, p) } catch (_: Exception) {}
    }

    fun ready() {
        bridge.log.add("I", "her page is up")
    }

    // ---------- the keyboard ----------

    // How tall the keyboard is now (px), 0 when it's down. Her page moves up above it, and no touch
    // pad stays over it: a pad over her body would otherwise eat the keys under her (the L, say).
    private var ime = 0
    private var lastDpr = 1.0

    private var typing = false
    private var lastRegions = JSONArray()
    private var imeInsets = 0
    private var imeFrame = 0

    private fun watchKeyboard(web: WebView) {
        // the keyboard as the window's insets (Android 11+)...
        web.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                imeInsets = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
                setIme(maxOf(imeInsets, imeFrame))
            }
            v.onApplyWindowInsets(insets)
        }
        // ...and as what's left of the screen above it (older phones, and some that don't send an
        // overlay its insets): whichever says the keyboard is taller wins
        web.viewTreeObserver.addOnGlobalLayoutListener {
            val r = android.graphics.Rect()
            web.getWindowVisibleDisplayFrame(r)
            val screen = maxOf(context.resources.displayMetrics.heightPixels, web.height)
            val gone = screen - r.bottom
            imeFrame = if (gone > screen / 6) gone else 0
            setIme(maxOf(imeInsets, imeFrame))
        }
    }

    private fun setIme(h: Int) {
        val focusable = params?.let { it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0 } ?: false
        // the keyboard is surely up while she takes it; if Android doesn't say how tall, about 42 %
        // of the screen (her window is drawn above the keyboard, so guessing short would leave her on the keys)
        val tall = maxOf(context.resources.displayMetrics.heightPixels, webView?.height ?: 0)
        val now = if (!focusable) 0 else if (h > 0) h else (tall * 0.42).toInt()
        Log.d("capri", "keyboard: insets $imeInsets frame $imeFrame focusable $focusable -> $now")
        if (now == ime) return
        ime = now
        pads.reapply()
        val css = now / context.resources.displayMetrics.density
        emit("ime:change", JSONObject().put("height", css.toDouble()).toString())
    }

    /**
     * Typing to her: the overlay takes the keyboard while her chat is open, and gives it back.
     * Android hands the keys to the window that was last touched, and a touch on her pads (their own
     * windows) never touches hers: so while she has the keyboard her window is touched directly (the
     * pads step aside), and a tap away from her and her chat closes it, as Escape would.
     */
    fun setFocusable(on: Boolean) = main.post {
        val web = webView ?: return@post
        val p = params ?: return@post
        Log.d("capri", "focus $on")
        val touch = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val focus = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        p.flags = if (on) p.flags and touch.inv() and focus.inv() else p.flags or touch or focus
        typing = on
        wm.updateViewLayout(web, p)
        pads.reapply()
        if (on) web.requestFocus()
        setIme(maxOf(imeInsets, imeFrame))
    }

    /** While she has the keyboard: a finger down outside all she can be touched by closes her chat. */
    private fun outside(ev: MotionEvent): Boolean {
        if (!typing || ev.actionMasked != MotionEvent.ACTION_DOWN) return false
        val d = lastDpr
        for (i in 0 until lastRegions.length()) {
            val r = lastRegions.optJSONArray(i) ?: continue
            if (ev.x >= r.optDouble(0) * d && ev.x <= r.optDouble(2) * d && ev.y >= r.optDouble(1) * d && ev.y <= r.optDouble(3) * d) return false
        }
        webView?.evaluateJavascript(LEAVE, null)
        return true
    }

    /** Where her page can be touched, in CSS px (touch.js): [[left, top, right, bottom], ...]. */
    fun setRegions(rects: JSONArray, dpr: Double) = main.post {
        Log.d("capri", "regions $dpr $rects") // logcat only: where she can be touched (CI taps her by it)
        pads.update(rects, dpr)
    }

    fun emit(channel: String, json: String) {
        val js = "window.__capri&&window.__capri.emit(${JSONObject.quote(channel)},${JSONObject.quote(json)})"
        main.post { webView?.evaluateJavascript(js, null) }
    }

    fun reply(id: String, ok: Boolean, json: String) {
        if (id.isEmpty()) return
        val js = "window.__capri&&window.__capri.reply(${JSONObject.quote(id)},$ok,${JSONObject.quote(json)})"
        main.post { webView?.evaluateJavascript(js, null) }
    }

    /** Screen off: nothing to draw for anyone. Her timers and her WebGL stop; the hook server doesn't. */
    fun pause() = main.post {
        if (paused) return@post
        paused = true
        webView?.onPause()
    }

    fun resume() = main.post {
        if (!paused) return@post
        paused = false
        webView?.onResume()
    }

    fun destroy() = main.post {
        pads.clear()
        webView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            it.destroy()
        }
        webView = null
    }

    private fun baseParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0) // the whole screen, status bar included
    }

    // ---------- her files ----------

    private fun serve(path: String): WebResourceResponse {
        return try {
            val stream = if (path.startsWith("/usersounds/")) {
                val name = File(path.removePrefix("/usersounds/")).name // no way out of her folder
                FileInputStream(File(bridge.store.soundsDir, name))
            } else {
                val rel = path.removePrefix("/")
                if (rel.contains("..") || !(rel.startsWith("capri/") || rel.startsWith("android/"))) throw SecurityException(rel)
                context.assets.open(rel)
            }
            WebResourceResponse(mime(path), if (mime(path).startsWith("text/") || path.endsWith(".js")) "utf-8" else null, stream).apply {
                responseHeaders = mapOf("Cache-Control" to "no-cache")
            }
        } catch (e: Exception) {
            WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), "".byteInputStream())
        }
    }

    private fun mime(path: String) = when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "woff2" -> "font/woff2"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        else -> "application/octet-stream"
    }

    // ---------- touches ----------

    /** Invisible windows over the parts of her page that can be touched, forwarding to the WebView. */
    private inner class TouchPads {
        private val views = ArrayList<View>()
        private val rects = ArrayList<IntArray>()

        private var last: JSONArray = JSONArray()

        fun reapply() = update(last, lastDpr)

        fun update(list: JSONArray, dpr: Double) {
            last = list
            lastDpr = dpr
            lastRegions = list
            // the keyboard's top, in screen px: nothing of hers is touchable below it
            val floor = if (ime > 0) maxOf(context.resources.displayMetrics.heightPixels, webView?.height ?: 0) - ime else Int.MAX_VALUE
            val want = ArrayList<IntArray>()
            // while she has the keyboard her own window takes the touches (setFocusable): no pads
            for (i in 0 until if (typing) 0 else minOf(list.length(), MAX_PADS)) {
                val r = list.optJSONArray(i) ?: continue
                val l = (r.optDouble(0) * dpr).toInt()
                val t = (r.optDouble(1) * dpr).toInt()
                val rr = (r.optDouble(2) * dpr).toInt()
                val b = minOf((r.optDouble(3) * dpr).toInt(), floor)
                if (rr - l < 4 || b - t < 4) continue
                want.add(intArrayOf(l, t, rr - l, b - t))
            }
            while (views.size < want.size) add()
            for (i in views.indices) {
                val v = views[i]
                val p = v.layoutParams as WindowManager.LayoutParams
                if (i < want.size) {
                    val r = want[i]
                    if (rects.getOrNull(i)?.contentEquals(r) == true && v.visibility == View.VISIBLE) continue
                    p.x = r[0]; p.y = r[1]; p.width = r[2]; p.height = r[3]
                    p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    v.visibility = View.VISIBLE
                    if (i < rects.size) rects[i] = r else rects.add(r)
                } else {
                    if (v.visibility == View.GONE) continue
                    p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    p.width = 1; p.height = 1
                    v.visibility = View.GONE
                    if (i < rects.size) rects[i] = IntArray(4)
                }
                try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
            }
        }

        private fun add() {
            val v = View(context)
            v.setOnTouchListener { pad, ev -> forward(pad, ev) }
            val p = baseParams(1, 1).apply {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            v.visibility = View.GONE
            wm.addView(v, p)
            views.add(v)
        }

        // The finger on a pad, handed to her page at the same place on screen. Placed from the
        // event's raw position, so a pad that moves under the finger mid-drag changes nothing.
        private fun forward(pad: View, ev: MotionEvent): Boolean {
            val web = webView ?: return false
            val at = IntArray(2)
            web.getLocationOnScreen(at)
            val copy = MotionEvent.obtain(ev)
            copy.offsetLocation(ev.rawX - ev.x - at[0], ev.rawY - ev.y - at[1])
            web.dispatchTouchEvent(copy)
            copy.recycle()
            return true
        }

        fun clear() {
            for (v in views) try { wm.removeView(v) } catch (_: Exception) {}
            views.clear()
            rects.clear()
        }
    }

    companion object {
        // a tap away from her while she has the keyboard: her chat (or setup question) closes, and
        // if somehow none is open, the keyboard is given back anyway (never a screen you can't touch)
        private const val LEAVE =
            "(function(){var p=window.dev&&window.dev.panel;if(p&&p.current&&p.current.chat&&!p.el.hidden){p.hide()}else if(window.host){window.host.focusOverlay(false)}})()"
        private const val ESCAPE =
            "document.activeElement && document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true}))"
        // the host Android reserves for an app's own files in a WebView (never a real site)
        const val HOST = "appassets.androidplatform.net"
        private const val MAX_PADS = 8
        private const val MAX_PASSTHROUGH_ALPHA = 0.8f
    }
}
