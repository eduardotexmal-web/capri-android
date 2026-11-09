package io.github.emmanuelquintana.capri

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The pieces of her HUD the app's screens are made of (MainActivity, OnboardingActivity): her
 * fonts, plates, buttons, fields, the icons of the pill bar, and the way things arrive on screen
 * (typed out letter by letter, glitched in).
 */
class Ui(private val a: Activity) {
    val head: Typeface = a.resources.getFont(R.font.chakrapetch_bold)
    val body: Typeface = a.resources.getFont(R.font.chakrapetch_semibold)
    val mono: Typeface = a.resources.getFont(R.font.jetbrains_mono)
    private val density = a.resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())

    fun dp(v: Float) = v * density
    fun dpi(v: Int) = (v * density).toInt()

    fun text(t: CharSequence, size: Float, color: Int, face: Typeface = body) = TextView(a).apply {
        text = t
        textSize = size
        setTextColor(color)
        typeface = face
    }

    /** "// SECTION" in cyan mono, the way her panels label things. */
    fun label(t: String, color: Int = Hud.GREEN) = text("// " + t.uppercase(), 12f, color, mono).apply { letterSpacing = 0.12f }

    fun col(pad: Int = 0) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(pad), dpi(pad), dpi(pad), dpi(pad))
    }

    fun row() = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    fun block(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(top) }

    /** A plate: ink inside, a thin frame, the top right corner cut. */
    fun plate(frame: Int = Hud.LINE_STRONG, fill: Int = Hud.INK_2, echo: Int = 0) =
        Hud.Plate(fill, frame, echo, cut = dp(10f), stroke = dp(1f), offset = dp(4f))

    fun card(frame: Int = Hud.LINE_STRONG, echo: Int = 0) = col().apply {
        background = plate(frame, echo = echo)
        setPadding(dpi(14), dpi(12), dpi(14 + if (echo != 0) 4 else 0), dpi(14 + if (echo != 0) 4 else 0))
    }

    /** Her buttons: yellow (the main one, her red behind it), her green, or outlined. */
    fun button(t: String, kind: Kind = Kind.MAIN, onTap: () -> Unit) = text(t.uppercase(), if (kind == Kind.MAIN) 17f else 13f, Hud.INK, if (kind == Kind.MAIN) head else mono).apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.08f
        isClickable = true
        when (kind) {
            Kind.MAIN -> {
                background = Hud.Plate(Hud.YELLOW, Hud.MANE, Hud.MANE, cut = dp(14f), stroke = 0f, offset = dp(5f))
                setPadding(0, dpi(14), dpi(5), dpi(19))
            }
            Kind.GREEN -> {
                background = Hud.Plate(Hud.GREEN, Hud.YELLOW, cut = dp(8f), stroke = 0f, offset = 0f)
                setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
            }
            Kind.LINE -> {
                setTextColor(Hud.YELLOW)
                background = Hud.Plate(Hud.INK, Hud.YELLOW, cut = dp(8f), stroke = dp(1f), offset = 0f)
                setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
            }
        }
        setOnClickListener { onTap() }
    }

    enum class Kind { MAIN, GREEN, LINE }

    /** The status and navigation bars' heights (and the keyboard's), as they change: Android 15 draws apps under them. */
    fun underSystemBars(root: View, apply: (top: Int, bottom: Int) -> Unit) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                apply(bars.top, maxOf(bars.bottom, ime.bottom)) // the keyboard too, when it's up
            } else {
                @Suppress("DEPRECATION")
                apply(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
    }

    /** A line to type into, in her style: a yellow underline, mono, a tag. */
    fun field(hint: String, secret: Boolean = false) = EditText(a).apply {
        this.hint = hint
        setHintTextColor(Hud.PAPER_3)
        setTextColor(Hud.PAPER)
        typeface = body
        textSize = 17f
        isSingleLine = true
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        background = Underline()
        setPadding(dpi(4), dpi(10), dpi(4), dpi(12))
    }

    private inner class Underline : Drawable() {
        private val p = Paint().apply { color = Hud.YELLOW }
        override fun draw(c: Canvas) {
            c.drawRect(bounds.left.toFloat(), bounds.bottom - dp(2f), bounds.right.toFloat(), bounds.bottom.toFloat(), p)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    fun face(name: String): Bitmap? = runCatching {
        val file = if (name == "grin") "capri-face.png" else "face-$name.png"
        a.assets.open("capri/assets/capri/$file").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    // ---------- arriving on screen ----------

    /** Her line typed out a letter at a time, as on her panel. */
    fun type(view: TextView, line: CharSequence, step: Long = 22, done: () -> Unit = {}) {
        view.tag = line
        var i = 0
        view.text = ""
        val tick = object : Runnable {
            override fun run() {
                if (view.tag !== line) return // another line took its place
                i = minOf(line.length, i + 1)
                view.text = line.subSequence(0, i)
                if (i < line.length) main.postDelayed(this, step) else done()
            }
        }
        main.postDelayed(tick, step)
    }

    /** In with a glitch: a jolt sideways, flickering, then still. */
    fun glitchIn(v: View, delay: Long = 0) {
        v.alpha = 0f
        v.translationX = dp(18f)
        v.animate().setStartDelay(delay).setDuration(320).alpha(1f).translationX(0f)
            .setInterpolator(GlitchInterpolator()).start()
    }

    /** Steps instead of a glide (her panels' steps(3)), with a kick back near the end. */
    private class GlitchInterpolator : android.animation.TimeInterpolator {
        private val d = DecelerateInterpolator(1.6f)
        override fun getInterpolation(t: Float): Float {
            val s = (kotlin.math.floor(t * 6) / 6).toFloat()
            val k = d.getInterpolation(s)
            return if (t in 0.62f..0.74f) k * 1.08f else k
        }
    }

    /** A bar that fills, for her boot. */
    fun fill(bar: Bar, ms: Long, done: () -> Unit) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            addUpdateListener { bar.k = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = done()
            })
            start()
        }
    }

    class Bar(private val color: Int, private val back: Int) : Drawable() {
        private val p = Paint()
        var k = 0f
            set(v) { field = v; invalidateSelf() }

        override fun draw(c: Canvas) {
            val b = bounds
            p.color = back
            c.drawRect(b, p)
            p.color = color
            // in segments, like her ticker
            val seg = b.height() * 1.4f
            var x = b.left.toFloat()
            val end = b.left + b.width() * k
            while (x < end) {
                c.drawRect(x, b.top.toFloat(), minOf(x + seg * 0.8f, end), b.bottom.toFloat(), p)
                x += seg
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    // ---------- the pill bar's icons ----------

    /** Drawn, not from a font: a spark (what's new), a shield (permissions), sliders (settings), two people (community). */
    class Icon(private val kind: String, var color: Int) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun draw(c: Canvas) {
            val b = RectF(bounds)
            val s = minOf(b.width(), b.height())
            val cx = b.centerX()
            val cy = b.centerY()
            p.color = color
            path.reset()
            when (kind) {
                "spark" -> {
                    p.style = Paint.Style.FILL
                    val r = s * 0.46f
                    val w = s * 0.13f
                    path.moveTo(cx, cy - r); path.lineTo(cx + w, cy - w); path.lineTo(cx + r, cy)
                    path.lineTo(cx + w, cy + w); path.lineTo(cx, cy + r); path.lineTo(cx - w, cy + w)
                    path.lineTo(cx - r, cy); path.lineTo(cx - w, cy - w); path.close()
                    c.drawPath(path, p)
                    c.drawCircle(cx + r * 0.78f, cy - r * 0.78f, s * 0.07f, p)
                }
                "shield" -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = s * 0.1f
                    p.strokeJoin = Paint.Join.MITER
                    val w = s * 0.36f
                    path.moveTo(cx, cy - s * 0.44f)
                    path.lineTo(cx + w, cy - s * 0.3f); path.lineTo(cx + w, cy + s * 0.02f)
                    path.lineTo(cx, cy + s * 0.44f); path.lineTo(cx - w, cy + s * 0.02f)
                    path.lineTo(cx - w, cy - s * 0.3f); path.close()
                    c.drawPath(path, p)
                    path.reset()
                    path.moveTo(cx - s * 0.15f, cy); path.lineTo(cx - s * 0.03f, cy + s * 0.12f); path.lineTo(cx + s * 0.17f, cy - s * 0.1f)
                    c.drawPath(path, p)
                }
                "mic" -> {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = s * 0.1f
                    p.strokeCap = Paint.Cap.SQUARE
                    val w = s * 0.14f
                    c.drawRoundRect(cx - w, cy - s * 0.44f, cx + w, cy + s * 0.08f, w, w, p)
                    c.drawArc(cx - s * 0.3f, cy - s * 0.3f, cx + s * 0.3f, cy + s * 0.22f, 20f, 140f, false, p)
                    c.drawLine(cx, cy + s * 0.22f, cx, cy + s * 0.42f, p)
                    c.drawLine(cx - s * 0.16f, cy + s * 0.42f, cx + s * 0.16f, cy + s * 0.42f, p)
                }
                "people" -> { // two heads and shoulders: the community
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = s * 0.09f
                    p.strokeCap = Paint.Cap.ROUND
                    c.drawCircle(cx - s * 0.13f, cy - s * 0.17f, s * 0.15f, p)
                    c.drawArc(cx - s * 0.42f, cy + s * 0.06f, cx + s * 0.16f, cy + s * 0.64f, 200f, 140f, false, p)
                    c.drawCircle(cx + s * 0.2f, cy - s * 0.22f, s * 0.12f, p)
                    c.drawArc(cx + s * 0.02f, cy - s * 0.02f, cx + s * 0.46f, cy + s * 0.42f, 215f, 125f, false, p)
                }
                else -> { // sliders
                    p.style = Paint.Style.FILL
                    val h = s * 0.08f
                    for ((i, k) in listOf(0.3f, 0.7f, 0.45f).withIndex()) {
                        val y = cy + (i - 1) * s * 0.3f
                        c.drawRect(cx - s * 0.42f, y - h / 2, cx + s * 0.42f, y + h / 2, p)
                        c.drawRect(cx - s * 0.42f + s * 0.84f * k - s * 0.08f, y - s * 0.12f, cx - s * 0.42f + s * 0.84f * k + s * 0.08f, y + s * 0.12f, p)
                    }
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /** A capsule (the pill bar and its selected tab). */
    class Pill(private val fill: Int, private val frame: Int, private val stroke: Float) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun draw(c: Canvas) {
            val r = RectF(bounds)
            r.inset(stroke / 2, stroke / 2)
            val rad = r.height() / 2
            p.style = Paint.Style.FILL
            p.color = fill
            c.drawRoundRect(r, rad, rad, p)
            if (stroke > 0) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = stroke
                p.color = frame
                c.drawRoundRect(r, rad, rad, p)
            }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
