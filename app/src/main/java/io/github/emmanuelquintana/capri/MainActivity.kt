package io.github.emmanuelquintana.capri

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The app's screen, dressed as her HUD, with a pill bar at the bottom: What's new (her status,
 * Start, updates and what each version brought), Permissions (what each is for) and Settings
 * (what she calls you, her AI, the log). The first time, her welcome comes first.
 */
class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private var current = 0

    // What's new
    private lateinit var statusDot: View
    private lateinit var statusWord: TextView
    private lateinit var statusText: TextView
    private lateinit var startBtn: TextView
    private lateinit var updateText: TextView
    private lateinit var updateBtn: TextView
    private var release: Updater.Release? = null
    private var downloading = false

    // Permissions
    private val permRows = mutableListOf<() -> Unit>()

    // Settings
    private lateinit var nameField: android.widget.EditText
    private lateinit var aiText: TextView
    private lateinit var logText: TextView

    private class Tab(val icon: Ui.Icon, val label: TextView, val button: LinearLayout, val page: View)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OnboardingActivity.needed(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        window.statusBarColor = Hud.INK
        window.navigationBarColor = Hud.INK
        ui = Ui(this)

        val root = FrameLayout(this).apply { background = Hud.Backdrop(ui.dp(3f)) }
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val pages = listOf(newsPage(), permsPage(), settingsPage())
        val bar = ui.row().apply {
            background = Ui.Pill(Hud.INK, Hud.YELLOW, ui.dp(1.5f))
            setPadding(ui.dpi(6), ui.dpi(6), ui.dpi(6), ui.dpi(6))
            elevation = ui.dp(6f)
        }
        val labels = listOf(R.string.tab_news to "spark", R.string.tab_perms to "shield", R.string.tab_settings to "sliders")
        for ((i, pair) in labels.withIndex()) {
            val (label, kind) = pair
            val icon = Ui.Icon(kind, Hud.PAPER_2)
            val btn = ui.row().apply {
                gravity = Gravity.CENTER
                setPadding(ui.dpi(14), ui.dpi(10), ui.dpi(14), ui.dpi(10))
                isClickable = true
                setOnClickListener { show(i) }
            }
            btn.addView(View(this).apply { background = icon }, LinearLayout.LayoutParams(ui.dpi(20), ui.dpi(20)))
            val text = ui.text(getString(label), 13f, Hud.PAPER_2, ui.head).apply { setPadding(ui.dpi(8), 0, 0, 0) }
            btn.addView(text)
            bar.addView(btn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            content.addView(pages[i], FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            tabs.add(Tab(icon, text, btn, pages[i]))
        }
        root.addView(bar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = ui.dpi(16)
        })
        setContentView(root)
        show(savedInstanceState?.getInt("tab") ?: 0, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", current)
    }

    /** One tab on: its icon and label in ink on a yellow capsule, the others quiet. */
    private fun show(i: Int, animate: Boolean = true) {
        current = i
        hideKeyboard()
        for ((j, t) in tabs.withIndex()) {
            val on = i == j
            t.button.background = if (on) Ui.Pill(Hud.YELLOW, 0, 0f) else null
            t.icon.color = if (on) Hud.INK else Hud.PAPER_2
            t.icon.invalidateSelf()
            t.label.setTextColor(if (on) Hud.INK else Hud.PAPER_2)
            // only the chosen one shows its name: the pill stays short on a narrow phone
            t.label.visibility = if (on) View.VISIBLE else View.GONE
            t.page.visibility = if (on) View.VISIBLE else View.GONE
        }
        if (animate) ui.glitchIn(tabs[i].page)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (!::ui.isInitialized) return
        Diag.onChange = { runOnUiThread { showLog() } }
        refresh()
        checkUpdate()
    }

    override fun onPause() {
        super.onPause()
        Diag.onChange = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = refresh()

    private fun page(build: LinearLayout.() -> Unit): View {
        val col = ui.col().apply {
            setPadding(ui.dpi(20), ui.dpi(24), ui.dpi(20), ui.dpi(110)) // room for the pill bar
            build()
        }
        return ScrollView(this).apply { isFillViewport = true; addView(col) }
    }

    // ---------- What's new ----------

    private fun newsPage() = page {
        // header: her face in a cut frame, the project line in cyan, her name in yellow
        val header = ui.row()
        val face = FrameLayout(this@MainActivity).apply {
            background = Hud.Plate(Hud.INK_2, Hud.YELLOW, Hud.CYAN, cut = ui.dp(12f), stroke = ui.dp(2f), offset = ui.dp(4f))
            setPadding(ui.dpi(4), ui.dpi(4), ui.dpi(8), ui.dpi(8))
            addView(ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                ui.face("grin")?.let { setImageBitmap(it) }
            }, FrameLayout.LayoutParams(ui.dpi(72), ui.dpi(72)))
        }
        header.addView(face)
        header.addView(ui.col().apply {
            setPadding(ui.dpi(16), 0, 0, 0)
            addView(ui.text("/// PROJECT CAPRICORN", 11f, Hud.CYAN, ui.mono).apply { letterSpacing = 0.12f })
            addView(ui.text("CAPRI", 44f, Hud.YELLOW, ui.head).apply { letterSpacing = 0.06f; includeFontPadding = false })
            addView(ui.text("ANDROID // v${BuildConfig.VERSION_NAME}", 10f, Hud.PAPER_3, ui.mono).apply { letterSpacing = 0.1f })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(header)

        // status and Start
        val status = ui.card()
        val statusRow = ui.row()
        statusDot = View(this@MainActivity)
        statusRow.addView(statusDot, LinearLayout.LayoutParams(ui.dpi(10), ui.dpi(10)).apply { rightMargin = ui.dpi(10) })
        statusWord = ui.text("", 13f, Hud.PAPER, ui.mono).apply { letterSpacing = 0.14f }
        statusRow.addView(statusWord)
        status.addView(statusRow)
        statusText = ui.text("", 14f, Hud.PAPER_2, ui.body)
        status.addView(statusText, ui.block(6))
        addView(status, ui.block(20))
        startBtn = ui.button("", Ui.Kind.MAIN) {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                Toast.makeText(this@MainActivity, R.string.ob_need_overlay, Toast.LENGTH_LONG).show()
                show(1)
                return@button
            }
            if (CapriService.running) CapriService.send(this@MainActivity, CapriService.ACTION_QUIT) else CapriService.start(this@MainActivity)
            startBtn.postDelayed({ refresh() }, 700)
        }
        addView(startBtn, ui.block(14))

        // updates
        updateText = ui.text("", 13f, Hud.PAPER_2, ui.mono).apply { letterSpacing = 0.02f; setLineSpacing(0f, 1.15f) }
        updateBtn = ui.button(getString(R.string.update_go), Ui.Kind.CYAN) { update() }.apply { visibility = View.GONE }
        addView(ui.card().apply {
            addView(ui.label(getString(R.string.update_head)))
            addView(updateText, ui.block(6))
            addView(updateBtn, ui.block(10))
        }, ui.block(22))

        // what each version brought (CHANGELOG.md)
        addView(ui.label(getString(R.string.news_head)), ui.block(26))
        for ((i, v) in changelog().take(6).withIndex()) {
            val card = ui.card(if (i == 0) Hud.YELLOW else Hud.LINE_STRONG, if (i == 0) Hud.CYAN else 0)
            card.addView(ui.text("v" + v.first, 12f, if (i == 0) Hud.YELLOW else Hud.PAPER_3, ui.mono).apply { letterSpacing = 0.08f })
            for (item in v.second) card.addView(bullet(item, i == 0), ui.block(8))
            addView(card, ui.block(10))
        }

        addView(ui.text(getString(R.string.main_license), 11f, Hud.PAPER_3, ui.body).apply { setLineSpacing(0f, 1.2f) }, ui.block(24))
    }

    /** "- **Title:** the rest" → a spark, the title in her display face, the rest as body text. */
    private fun bullet(item: String, fresh: Boolean): TextView {
        val clean = item.replace("`", "").replace(Regex("\\*([^*]+)\\*"), "$1")
        val m = Regex("^\\*\\*(.+?)\\*\\*\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(clean)
        val s = SpannableStringBuilder("✦ ")
        s.setSpan(ForegroundColorSpan(if (fresh) Hud.CYAN else Hud.PAPER_3), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (m != null) {
            val start = s.length
            s.append(m.groupValues[1].replace("**", ""))
            s.setSpan(FaceSpan(ui.head), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(Hud.PAPER), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.append(" ").append(m.groupValues[2].replace("**", ""))
        } else s.append(clean.replace("**", ""))
        return ui.text(s, 14f, Hud.PAPER_2, ui.body).apply { setLineSpacing(0f, 1.18f) }
    }

    /** CHANGELOG.md, from the APK: [(version, [items])], newest first. */
    private fun changelog(): List<Pair<String, List<String>>> = runCatching {
        val out = mutableListOf<Pair<String, MutableList<String>>>()
        assets.open("CHANGELOG.md").bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trimEnd()
                when {
                    line.startsWith("## ") -> out.add(line.removePrefix("## ").trim() to mutableListOf())
                    out.isEmpty() -> Unit
                    line.startsWith("- ") -> out.last().second.add(line.removePrefix("- "))
                    line.isNotBlank() && out.last().second.isNotEmpty() -> {
                        val items = out.last().second
                        items[items.size - 1] = items.last() + " " + line.trim()
                    }
                }
            }
        }
        out.map { it.first to it.second.toList() }
    }.getOrDefault(emptyList())

    // ---------- Permissions ----------

    private fun permsPage() = page {
        addView(ui.label(getString(R.string.tab_perms)))
        addView(ui.text(getString(R.string.perms_intro), 15f, Hud.PAPER_2, ui.body).apply { setLineSpacing(0f, 1.2f) }, ui.block(8))
        for ((i, p) in Permissions.all(this@MainActivity).withIndex()) {
            val (view, refresh) = Permissions.row(this@MainActivity, ui, p)
            addView(view, ui.block(if (i == 0) 18 else 12))
            permRows.add(refresh)
        }
    }

    // ---------- Settings ----------

    private fun settingsPage() = page {
        // what she calls you
        addView(ui.card(Hud.YELLOW).apply {
            addView(ui.label(getString(R.string.settings_name)))
            nameField = ui.field(getString(R.string.ob_name_hint)).apply {
                setText(CapriSettings.name(this@MainActivity))
                filters = arrayOf(android.text.InputFilter.LengthFilter(24))
                setOnEditorActionListener { _, _, _ -> saveName(); true }
            }
            addView(nameField, ui.block(6))
            addView(ui.button(getString(R.string.settings_save), Ui.Kind.CYAN) { saveName() }, ui.block(12))
        })

        // her AI
        addView(ui.card(Hud.CYAN).apply {
            addView(ui.label(getString(R.string.settings_ai)))
            aiText = ui.text("", 17f, Hud.PAPER, ui.head)
            addView(aiText, ui.block(6))
            addView(ui.text(getString(R.string.main_ai_how), 13f, Hud.PAPER_2, ui.body).apply { setLineSpacing(0f, 1.15f) }, ui.block(4))
            addView(ui.button(getString(R.string.main_ai_pick) + " ›", Ui.Kind.CYAN) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_ONLY, OnboardingActivity.PAGE_AI))
            }, ui.block(12))
            addView(ui.button(getString(R.string.settings_ai_panel) + " ›", Ui.Kind.LINE) { pickAi() }, ui.block(8))
        }, ui.block(16))

        addView(ui.button(getString(R.string.settings_intro_again), Ui.Kind.LINE) {
            getSharedPreferences("app", MODE_PRIVATE).edit().putBoolean("welcomed", false).apply()
            startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
            finish()
        }, ui.block(16))

        // the log: what went wrong lately
        val logHead = ui.row()
        logHead.addView(ui.label(getString(R.string.main_log)), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        logHead.addView(ui.button(getString(R.string.main_copy), Ui.Kind.LINE) { copyLog() })
        addView(logHead, ui.block(26))
        logText = ui.text("", 11f, Hud.PAPER_2, ui.mono).apply {
            setLineSpacing(0f, 1.15f)
            setTextIsSelectable(true)
            background = ui.plate()
            setPadding(ui.dpi(12), ui.dpi(10), ui.dpi(12), ui.dpi(12))
        }
        addView(logText, ui.block(8))
    }

    private fun saveName() {
        val n = nameField.text.toString().trim()
        hideKeyboard()
        if (n.isEmpty()) return
        CapriSettings.setName(this, n)
        Toast.makeText(this, getString(R.string.settings_name_saved, n), Toast.LENGTH_SHORT).show()
    }

    // ---------- state ----------

    private fun refresh() {
        if (!::ui.isInitialized) return
        val running = CapriService.running
        statusDot.background = Hud.Plate(if (running) Hud.CYAN else Hud.RED, 0, cut = 0f, stroke = 0f, offset = 0f)
        statusWord.text = getString(if (running) R.string.main_status_on else R.string.main_status_off)
        statusWord.setTextColor(if (running) Hud.CYAN else Hud.RED)
        statusText.text = getString(if (running) R.string.main_running else R.string.main_idle)
        startBtn.text = getString(if (running) R.string.main_stop else R.string.main_start).uppercase()
        startBtn.alpha = if (Settings.canDrawOverlays(this)) 1f else 0.5f
        startBtn.setTextColor(if (running) Hud.YELLOW else Hud.INK)
        startBtn.background = if (running) Hud.Plate(Hud.INK, Hud.YELLOW, Hud.RED, cut = ui.dp(14f), stroke = ui.dp(2f), offset = ui.dp(5f))
        else Hud.Plate(Hud.YELLOW, Hud.CYAN, Hud.CYAN, cut = ui.dp(14f), stroke = 0f, offset = ui.dp(5f))
        permRows.forEach { it() }
        aiText.text = getString(R.string.settings_ai_now, when (val p = CapriSettings.provider(this)) {
            "pollinations" -> getString(R.string.main_ai_free)
            "ollama" -> getString(R.string.main_ai_pc) + " · " + CapriSettings.ollamaHost(this)
            "custom" -> getString(R.string.main_ai_custom)
            else -> CapriSettings.KEYED.firstOrNull { it.id == p }?.name ?: p
        })
        if (!nameField.hasFocus()) nameField.setText(CapriSettings.name(this))
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
            release != null -> getString(R.string.update_new, r.version, mine) + if (r.notes.isNotBlank()) "\n\n" + r.notes else ""
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
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.ob_need_overlay, Toast.LENGTH_LONG).show()
            return
        }
        if (CapriService.running) CapriService.send(this, CapriService.ACTION_SETUP)
        else CapriService.start(this, CapriService.ACTION_SETUP)
        moveTaskToBack(true)
    }

    private fun showLog() {
        if (!::logText.isInitialized) return
        val lines = Diag.all()
        logText.text = if (lines.isEmpty()) getString(R.string.main_log_empty) else lines.takeLast(30).joinToString("\n")
        logText.setTextColor(if (lines.any { it.contains(" E ") }) Hud.PAPER else Hud.PAPER_2)
    }

    private fun copyLog() {
        val all = "Capri ${BuildConfig.VERSION_NAME} / Android ${Build.VERSION.RELEASE} (${Build.MODEL})\n" + Diag.all().joinToString("\n")
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Capri", all))
        Toast.makeText(this, R.string.main_copied, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0); it.clearFocus() }
    }

    /** Her display face for part of a line (TypefaceSpan(Typeface) needs Android 9). */
    private class FaceSpan(private val face: android.graphics.Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.typeface = face }
        override fun updateMeasureState(tp: TextPaint) { tp.typeface = face }
    }
}
