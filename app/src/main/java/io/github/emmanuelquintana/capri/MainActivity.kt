package io.github.emmanuelquintana.capri

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The door in, dressed as her HUD: who she is, the two permissions she needs (being on top of your
 * apps, her notification), Start, and a log of what went wrong lately. Everything else is in her
 * own menu, on her.
 */
class MainActivity : Activity() {
    private lateinit var headFont: Typeface
    private lateinit var bodyFont: Typeface
    private lateinit var monoFont: Typeface

    private lateinit var statusDot: View
    private lateinit var statusWord: TextView
    private lateinit var statusText: TextView
    private lateinit var overlayStep: Step
    private lateinit var notifyStep: Step
    private lateinit var notesStep: Step
    private lateinit var startBtn: TextView
    private lateinit var logText: TextView
    private lateinit var aiText: TextView
    private lateinit var updateText: TextView
    private lateinit var updateBtn: TextView
    private var release: Updater.Release? = null
    private var downloading = false

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dpi(v: Int) = (v * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Hud.INK
        window.navigationBarColor = Hud.INK
        headFont = resources.getFont(R.font.chakrapetch_bold)
        bodyFont = resources.getFont(R.font.chakrapetch_semibold)
        monoFont = resources.getFont(R.font.jetbrains_mono)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20), dpi(28), dpi(20), dpi(24))
        }

        // header: her face in a cut frame, the project line in cyan, her name in yellow
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val face = FrameLayout(this).apply {
            background = Hud.Plate(Hud.INK_2, Hud.YELLOW, Hud.CYAN, cut = dp(12f), stroke = dp(2f), offset = dp(4f))
            setPadding(dpi(4), dpi(4), dpi(8), dpi(8))
            addView(ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                runCatching { assets.open("capri/assets/capri/capri-face.png").use { setImageBitmap(BitmapFactory.decodeStream(it)) } }
            }, FrameLayout.LayoutParams(dpi(72), dpi(72)))
        }
        header.addView(face)
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), 0, 0, 0)
            addView(text("/// PROJECT CAPRICORN", 11f, Hud.CYAN, monoFont).apply { letterSpacing = 0.12f })
            addView(text("CAPRI", 44f, Hud.YELLOW, headFont).apply { letterSpacing = 0.06f; includeFontPadding = false })
            addView(text("ANDROID // v${BuildConfig.VERSION_NAME}", 10f, Hud.PAPER_3, monoFont).apply { letterSpacing = 0.1f })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(header)

        col.addView(text(getString(R.string.main_intro), 15f, Hud.PAPER_2, bodyFont).apply {
            setLineSpacing(0f, 1.2f)
        }, block(top = 20))

        // status plate
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Hud.Plate(Hud.INK_2, Hud.LINE_STRONG, cut = dp(10f), stroke = dp(1f), offset = 0f)
            setPadding(dpi(14), dpi(12), dpi(14), dpi(14))
        }
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusDot = View(this)
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dpi(10), dpi(10)).apply { rightMargin = dpi(10) })
        statusWord = text("", 13f, Hud.PAPER, monoFont).apply { letterSpacing = 0.14f }
        statusRow.addView(statusWord)
        status.addView(statusRow)
        statusText = text("", 14f, Hud.PAPER_2, bodyFont)
        status.addView(statusText, block(top = 6))
        col.addView(status, block(top = 20))

        // the permissions, as numbered steps
        overlayStep = Step("01", getString(R.string.main_overlay)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        notifyStep = Step("02", getString(R.string.main_notify)) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        col.addView(overlayStep.view, block(top = 14))
        notesStep = Step("03", getString(R.string.main_notes)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        col.addView(notifyStep.view, block(top = 10))
        col.addView(notesStep.view, block(top = 10))

        // start / stop: the big yellow plate
        startBtn = text("", 20f, Hud.INK, headFont).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            isClickable = true
            setPadding(0, dpi(16), dpi(6), dpi(20))
            setOnClickListener {
                if (CapriService.running) CapriService.send(this@MainActivity, CapriService.ACTION_QUIT) else CapriService.start(this@MainActivity)
                postDelayed({ refresh() }, 700)
            }
        }
        col.addView(startBtn, block(top = 22))

        // her AI: who answers her, and her own setup panel (no terminal)
        val ai = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Hud.Plate(Hud.INK_2, Hud.CYAN, cut = dp(10f), stroke = dp(1f), offset = 0f)
            setPadding(dpi(14), dpi(12), dpi(14), dpi(14))
        }
        ai.addView(text("// " + getString(R.string.main_ai).uppercase(), 12f, Hud.CYAN, monoFont).apply { letterSpacing = 0.12f })
        aiText = text("", 16f, Hud.PAPER, headFont)
        ai.addView(aiText, block(top = 4))
        ai.addView(text(getString(R.string.main_ai_how), 13f, Hud.PAPER_2, bodyFont).apply { setLineSpacing(0f, 1.15f) }, block(top = 4))
        ai.addView(text(getString(R.string.main_ai_pick).uppercase() + " ›", 13f, Hud.INK, monoFont).apply {
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
            background = Hud.Plate(Hud.CYAN, Hud.YELLOW, cut = dp(8f), stroke = 0f, offset = 0f)
            isClickable = true
            setOnClickListener { pickAi() }
        }, block(top = 12))
        col.addView(ai, block(top = 18))

        // updates, from the repo's releases
        updateText = text("", 13f, Hud.PAPER_2, monoFont).apply { letterSpacing = 0.04f }
        updateBtn = text("", 13f, Hud.INK, monoFont).apply {
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setPadding(dpi(12), dpi(10), dpi(12), dpi(10))
            background = Hud.Plate(Hud.YELLOW, Hud.CYAN, cut = dp(8f), stroke = 0f, offset = 0f)
            isClickable = true
            visibility = View.GONE
            setOnClickListener { update() }
        }
        val upd = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Hud.Plate(Hud.INK_2, Hud.LINE_STRONG, cut = dp(10f), stroke = dp(1f), offset = 0f)
            setPadding(dpi(14), dpi(12), dpi(14), dpi(14))
            addView(text("// " + getString(R.string.update_head).uppercase(), 12f, Hud.CYAN, monoFont).apply { letterSpacing = 0.12f })
            addView(updateText, block(top = 6))
            addView(updateBtn, block(top = 10))
        }
        col.addView(upd, block(top = 18))

        // the log: what went wrong lately
        val logHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        logHead.addView(text("// " + getString(R.string.main_log).uppercase(), 12f, Hud.CYAN, monoFont).apply { letterSpacing = 0.12f },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        logHead.addView(text(getString(R.string.main_copy).uppercase(), 11f, Hud.YELLOW, monoFont).apply {
            letterSpacing = 0.12f
            setPadding(dpi(12), dpi(6), dpi(12), dpi(6))
            background = Hud.Plate(Hud.INK, Hud.YELLOW, cut = dp(6f), stroke = dp(1f), offset = 0f)
            isClickable = true
            setOnClickListener { copyLog() }
        })
        col.addView(logHead, block(top = 26))
        logText = text("", 11f, Hud.PAPER_2, monoFont).apply {
            setLineSpacing(0f, 1.15f)
            setTextIsSelectable(true)
            background = Hud.Plate(Hud.INK_2, Hud.LINE_STRONG, cut = dp(10f), stroke = dp(1f), offset = 0f)
            setPadding(dpi(12), dpi(10), dpi(12), dpi(12))
        }
        col.addView(logText, block(top = 8))

        col.addView(text(getString(R.string.main_license), 11f, Hud.PAPER_3, bodyFont).apply {
            setLineSpacing(0f, 1.2f)
        }, block(top = 24))

        setContentView(ScrollView(this).apply {
            background = Hud.Backdrop(dp(3f))
            isFillViewport = true
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        Diag.onChange = { runOnUiThread { showLog() } }
        refresh()
        checkUpdate()
    }

    override fun onPause() {
        super.onPause()
        Diag.onChange = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = refresh()

    private fun notifyOk() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val running = CapriService.running
        overlayStep.set(overlayOk)
        notifyStep.set(notifyOk())
        notesStep.set(CapriNotifications.allowed(this))
        statusDot.background = Hud.Plate(if (running) Hud.CYAN else Hud.RED, 0, cut = 0f, stroke = 0f, offset = 0f)
        statusWord.text = getString(if (running) R.string.main_status_on else R.string.main_status_off)
        statusWord.setTextColor(if (running) Hud.CYAN else Hud.RED)
        statusText.text = getString(if (running) R.string.main_running else R.string.main_idle)
        startBtn.text = getString(if (running) R.string.main_stop else R.string.main_start).uppercase()
        startBtn.isEnabled = overlayOk
        startBtn.alpha = if (overlayOk) 1f else 0.35f
        startBtn.setTextColor(if (running) Hud.YELLOW else Hud.INK)
        startBtn.background = if (running) Hud.Plate(Hud.INK, Hud.YELLOW, Hud.RED, cut = dp(16f), stroke = dp(2f), offset = dp(5f))
        else Hud.Plate(Hud.YELLOW, Hud.YELLOW, Hud.CYAN, cut = dp(16f), stroke = 0f, offset = dp(5f))
        aiText.text = when (val p = Store(this).configString("provider") ?: "pollinations") {
            "pollinations" -> getString(R.string.main_ai_free)
            "ollama" -> getString(R.string.main_ai_pc)
            "custom" -> getString(R.string.main_ai_custom)
            else -> AI_NAMES[p] ?: p
        }
        showLog()
    }

    private fun checkUpdate() {
        if (downloading) return
        if (release == null) updateText.text = getString(R.string.update_checking)
        Thread {
            val r = Updater.latest()
            runOnUiThread { showUpdate(r) }
        }.start()
    }

    private fun showUpdate(r: Updater.Release?) {
        if (downloading) return
        release = r?.takeIf { Updater.isNewer(it.tag) }
        val mine = BuildConfig.VERSION_NAME
        updateText.text = when {
            r == null -> getString(R.string.update_offline, mine)
            release != null -> getString(R.string.update_new, r.tag.removePrefix("v"), mine)
            else -> getString(R.string.update_latest, mine)
        }
        updateText.setTextColor(if (release != null) Hud.YELLOW else Hud.PAPER_2)
        updateBtn.visibility = if (release != null) View.VISIBLE else View.GONE
        updateBtn.text = getString(R.string.update_go).uppercase() + " ›"
    }

    /** Download it and hand it to Android's installer (after letting Capri install apps, once). */
    private fun update() {
        val r = release ?: return
        if (downloading) return
        if (!packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.update_allow, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        downloading = true
        updateBtn.isEnabled = false
        Thread {
            try {
                var shown = -1
                val apk = Updater.download(this, r) { k ->
                    val pct = (k * 100).toInt()
                    if (pct != shown) {
                        shown = pct
                        runOnUiThread { updateBtn.text = getString(R.string.update_downloading, pct) }
                    }
                }
                Updater.install(this, apk)
                runOnUiThread { updateBtn.text = getString(R.string.update_installing) }
            } catch (e: Exception) {
                Diag.add("E", "update: ${e.message}")
                runOnUiThread { Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show() }
            } finally {
                downloading = false
                runOnUiThread { updateBtn.isEnabled = true }
            }
        }.start()
    }

    /** Her setup panel, over whatever is on screen: she starts first if she isn't on yet. */
    private fun pickAi() {
        if (!Settings.canDrawOverlays(this)) return
        if (CapriService.running) CapriService.send(this, CapriService.ACTION_SETUP)
        else CapriService.start(this, CapriService.ACTION_SETUP)
        moveTaskToBack(true)
    }

    private fun showLog() {
        val lines = Diag.all()
        logText.text = if (lines.isEmpty()) getString(R.string.main_log_empty) else lines.takeLast(30).joinToString("\n")
        logText.setTextColor(if (lines.any { it.contains(" E ") }) Hud.PAPER else Hud.PAPER_2)
    }

    private fun copyLog() {
        val all = "Capri ${BuildConfig.VERSION_NAME} / Android ${Build.VERSION.RELEASE} (${Build.MODEL})\n" + Diag.all().joinToString("\n")
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Capri", all))
        Toast.makeText(this, R.string.main_copied, Toast.LENGTH_SHORT).show()
    }

    /** A permission row: number, what it lets her do, and a mark (granted) or a GRANT plate. */
    private inner class Step(num: String, title: String, private val onTap: () -> Unit) {
        val view = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(14), dpi(12), dpi(12), dpi(12))
        }
        private val numView = text(num, 13f, Hud.CYAN, monoFont)
        private val titleView = text(title, 15f, Hud.PAPER, bodyFont).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        private val mark = text("", 11f, Hud.INK, monoFont).apply {
            letterSpacing = 0.1f
            setPadding(dpi(10), dpi(6), dpi(12), dpi(6))
        }

        init {
            view.addView(numView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dpi(12) })
            view.addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            view.addView(mark, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dpi(10) })
        }

        fun set(ok: Boolean) {
            view.background = Hud.Plate(Hud.INK_2, if (ok) Hud.LINE_STRONG else Hud.YELLOW, cut = dp(10f), stroke = dp(1f), offset = 0f)
            view.isClickable = !ok
            view.setOnClickListener(if (ok) null else View.OnClickListener { onTap() })
            numView.setTextColor(if (ok) Hud.PAPER_3 else Hud.CYAN)
            titleView.setTextColor(if (ok) Hud.PAPER_3 else Hud.PAPER)
            mark.text = if (ok) "✓ " + getString(R.string.main_granted) else getString(R.string.main_grant) + " ›"
            mark.setTextColor(if (ok) Hud.CYAN else Hud.INK)
            mark.background = if (ok) null else Hud.Plate(Hud.YELLOW, Hud.YELLOW, cut = dp(6f), stroke = 0f, offset = 0f)
        }
    }

    private fun text(t: String, size: Float, color: Int, face: Typeface) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        typeface = face
    }

    private fun block(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dpi(top) }

    companion object {
        private val AI_NAMES = mapOf(
            "groq" to "Groq", "gemini" to "Google Gemini", "openrouter" to "OpenRouter",
            "openai" to "OpenAI", "mistral" to "Mistral", "deepseek" to "DeepSeek", "claude" to "Claude Code",
        )
    }
}
