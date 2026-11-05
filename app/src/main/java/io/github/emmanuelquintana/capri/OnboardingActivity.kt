package io.github.emmanuelquintana.capri

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Her welcome, the first time the app opens: she boots up, says hello, asks what to call you,
 * which AI answers her, and walks through each permission saying what it's for. Then she starts.
 * Five pages, each arriving with a glitch and her line typed out as on her panel.
 */
class OnboardingActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var page: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var steps: LinearLayout
    private lateinit var count: TextView
    private lateinit var nav: LinearLayout
    private lateinit var back: TextView
    private lateinit var next: TextView
    private var at = 0
    private var name = ""
    private var only = -1 // just one page (Settings: her AI), then back

    // her AI page
    private var aiChoice = "pollinations"
    private var aiKeyed = CapriSettings.KEYED[0]
    private var keyField: EditText? = null
    private var ipField: EditText? = null
    private var aiStatus: TextView? = null
    private var checking = false

    // her permissions page, refreshed when you come back from Android's settings
    private val permRows = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Hud.INK
        window.navigationBarColor = Hud.INK
        ui = Ui(this)
        name = CapriSettings.name(this)

        val root = FrameLayout(this).apply { background = Hud.Backdrop(ui.dp(3f)) }
        val frame = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // where you are: five segments and 01/05
        val top = ui.row().apply { setPadding(ui.dpi(20), ui.dpi(20), ui.dpi(20), ui.dpi(4)) }
        steps = ui.row()
        for (i in 0 until PAGES) steps.addView(View(this), LinearLayout.LayoutParams(0, ui.dpi(4), 1f).apply { rightMargin = ui.dpi(4) })
        top.addView(steps, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        count = ui.text("", 11f, Hud.PAPER_3, ui.mono).apply { setPadding(ui.dpi(10), 0, 0, 0) }
        top.addView(count)
        frame.addView(top)

        page = ui.col().apply { setPadding(ui.dpi(20), ui.dpi(16), ui.dpi(20), ui.dpi(24)) }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        frame.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        nav = ui.row().apply { setPadding(ui.dpi(20), ui.dpi(8), ui.dpi(20), ui.dpi(20)) }
        back = ui.button(getString(R.string.ob_back), Ui.Kind.LINE) { go(at - 1) }
        next = ui.button(getString(R.string.ob_next)) { onNext() }
        nav.addView(back, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = ui.dpi(12) })
        nav.addView(next, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        frame.addView(nav)

        root.addView(frame)
        ui.underSystemBars(root) { t, b -> frame.setPadding(0, t, 0, b) }
        setContentView(root)
        only = intent.getIntExtra(EXTRA_ONLY, -1)
        if (only >= 0) top.visibility = View.GONE
        go(if (only >= 0) only else 0)
    }

    override fun onResume() {
        super.onResume()
        permRows.forEach { it() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permRows.forEach { it() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (at > 0 && only < 0) go(at - 1) else super.onBackPressed()
    }

    private val callMe get() = name.ifBlank { getString(R.string.choom) }

    private fun go(to: Int) {
        if (only >= 0 && to != only) {
            finish()
            return
        }
        if (to < 0 || to >= PAGES) return
        hideKeyboard()
        at = to
        page.removeAllViews()
        permRows.clear()
        for (i in 0 until PAGES) steps.getChildAt(i).background = Hud.Plate(if (i <= at) Hud.YELLOW else Hud.LINE_STRONG, 0, cut = 0f, stroke = 0f, offset = 0f)
        count.text = "%02d/%02d".format(at + 1, PAGES)
        nav.visibility = if (at == 0) View.GONE else View.VISIBLE
        back.visibility = if (at > 0) View.VISIBLE else View.GONE
        next.text = getString(if (at == PAGES - 1) R.string.main_start else if (only >= 0) R.string.ai_use else R.string.ob_next).uppercase()
        scroll.scrollTo(0, 0)
        when (at) {
            0 -> boot()
            1 -> namePage()
            2 -> aiPage()
            3 -> permsPage()
            4 -> donePage()
        }
    }

    private fun onNext() {
        when (at) {
            1 -> {
                val typed = (page.findViewWithTag<EditText>("name"))?.text?.toString()?.trim().orEmpty()
                if (typed.isNotEmpty() && typed != name) {
                    name = typed
                    CapriSettings.setName(this, typed)
                }
                go(2)
            }
            2 -> useAi()
            3 -> if (Settings.canDrawOverlays(this)) go(4) else Toast.makeText(this, R.string.ob_need_overlay, Toast.LENGTH_LONG).show()
            4 -> finishAndStart()
            else -> go(at + 1)
        }
    }

    // ---------- her line on each page: her face, what she says typed out ----------

    private fun says(face: String, line: String, quote: String? = null) {
        val row = ui.row().apply { gravity = Gravity.TOP }
        val frame = FrameLayout(this).apply {
            background = Hud.Plate(Hud.INK_2, Hud.YELLOW, Hud.MANE, cut = ui.dp(10f), stroke = ui.dp(2f), offset = ui.dp(4f))
            setPadding(ui.dpi(3), ui.dpi(3), ui.dpi(7), ui.dpi(7))
            addView(ImageView(this@OnboardingActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                ui.face(face)?.let { setImageBitmap(it) }
            }, FrameLayout.LayoutParams(ui.dpi(64), ui.dpi(64)))
        }
        row.addView(frame)
        val words = ui.col().apply { setPadding(ui.dpi(14), 0, 0, 0) }
        words.addView(ui.text("/// CAPRI", 11f, Hud.MANE, ui.mono).apply { letterSpacing = 0.12f })
        val said = ui.text("", 21f, Hud.PAPER, ui.head).apply { setLineSpacing(0f, 1.1f) }
        words.addView(said, ui.block(4))
        row.addView(words, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        page.addView(row)
        ui.glitchIn(row)
        ui.type(said, line)
        if (quote != null) {
            val q = ui.text(quote, 15f, Hud.PAPER_2, ui.body).apply {
                setLineSpacing(0f, 1.2f)
                setPadding(ui.dpi(12), 0, 0, 0)
                background = LeftRule()
            }
            page.addView(q, ui.block(16))
            ui.glitchIn(q, 250)
        }
    }

    /** The cyan rule down the left of her quotes. */
    private class LeftRule : android.graphics.drawable.Drawable() {
        private val p = android.graphics.Paint().apply { color = Hud.GREEN }
        override fun draw(c: android.graphics.Canvas) {
            c.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.left + 4f, bounds.bottom.toFloat(), p)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    // ---------- 01: she boots ----------

    private fun boot() {
        page.gravity = Gravity.CENTER_VERTICAL
        val head = ui.text("", 12f, Hud.GREEN, ui.mono).apply { letterSpacing = 0.14f }
        page.addView(head)
        val big = ui.text("CAPRI", 64f, Hud.YELLOW, ui.head).apply { letterSpacing = 0.06f; includeFontPadding = false; alpha = 0f }
        page.addView(big, ui.block(6))
        val bar = Ui.Bar(Hud.GREEN, Hud.LINE)
        page.addView(View(this).apply { background = bar }, LinearLayout.LayoutParams(MATCH_PARENT, ui.dpi(6)).apply { topMargin = ui.dpi(18) })
        val link = ui.text(getString(R.string.ob_linking), 12f, Hud.PAPER_3, ui.mono)
        page.addView(link, ui.block(8))
        val hello = ui.col()
        page.addView(hello, ui.block(28))
        ui.type(head, "/// PROJECT CAPRICORN", 40) {
            ui.glitchIn(big)
            ui.fill(bar, 1300) {
                link.text = "OK · v${BuildConfig.VERSION_NAME}"
                link.setTextColor(Hud.GREEN)
                val keep = page
                page = hello
                says("smug", getString(R.string.ob_hello))
                page = keep
                val start = ui.button(getString(R.string.ob_start)) { page.gravity = Gravity.TOP; go(1) }
                hello.addView(start, ui.block(26))
                ui.glitchIn(start, 900)
            }
        }
    }

    // ---------- 02: what she calls you ----------

    private fun namePage() {
        page.gravity = Gravity.TOP
        says("look", getString(R.string.ob_name_line), getString(R.string.ob_name_how))
        val field = ui.field(getString(R.string.ob_name_hint)).apply {
            tag = "name"
            setText(name)
            setSelection(text.length)
            filters = arrayOf(android.text.InputFilter.LengthFilter(24))
            setOnEditorActionListener { _, _, _ -> onNext(); true }
        }
        page.addView(field, ui.block(24))
        ui.glitchIn(field, 400)
        // suggestions: what she calls everyone, until you say
        val chips = ui.row()
        for (s in listOf("choom", "jefe", "netrunner")) {
            chips.addView(ui.button(s, Ui.Kind.LINE) { field.setText(s); field.setSelection(s.length) },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = ui.dpi(8) })
        }
        page.addView(chips, ui.block(14))
        ui.glitchIn(chips, 550)
    }

    // ---------- 03: her AI ----------

    private fun aiPage() {
        page.gravity = Gravity.TOP
        aiChoice = CapriSettings.provider(this).let { if (it == "ollama" || it == "pollinations" || CapriSettings.KEYED.any { k -> k.id == it }) it else "pollinations" }
        CapriSettings.KEYED.firstOrNull { it.id == aiChoice }?.let { aiKeyed = it }
        says("smug", getString(R.string.ob_ai_line, callMe), getString(R.string.ob_ai_how))
        val options = ui.col()
        page.addView(options, ui.block(18))
        aiStatus = ui.text("", 14f, Hud.GREEN, ui.mono)
        fun draw() {
            options.removeAllViews()
            options.addView(aiOption("pollinations", R.string.ai_free_title, R.string.ai_free_how, ::draw))
            options.addView(aiOption("keyed", R.string.ai_key_title, R.string.ai_key_how, ::draw), ui.block(10))
            options.addView(aiOption("ollama", R.string.ai_pc_title, R.string.ai_pc_how, ::draw), ui.block(10))
            options.addView(aiStatus, ui.block(12))
        }
        draw()
        for (i in 0 until options.childCount) ui.glitchIn(options.getChildAt(i), 300L + i * 90)
        val skip = ui.text(getString(R.string.ob_skip), 13f, Hud.PAPER_3, ui.mono).apply {
            setPadding(0, ui.dpi(14), 0, ui.dpi(4))
            isClickable = true
            setOnClickListener { go(3) }
        }
        page.addView(skip)
    }

    private fun isKeyed(id: String) = CapriSettings.KEYED.any { it.id == id }

    private fun aiOption(id: String, title: Int, how: Int, redraw: () -> Unit): View {
        val chosen = if (id == "keyed") isKeyed(aiChoice) else aiChoice == id
        val card = ui.card(if (chosen) Hud.YELLOW else Hud.LINE_STRONG, if (chosen) Hud.MANE else 0)
        val head = ui.row()
        head.addView(ui.text(if (chosen) "◆" else "◇", 16f, if (chosen) Hud.YELLOW else Hud.PAPER_3, ui.mono).apply { setPadding(0, 0, ui.dpi(10), 0) })
        head.addView(ui.text(getString(title), 17f, if (chosen) Hud.PAPER else Hud.PAPER_2, ui.head))
        card.addView(head)
        card.addView(ui.text(getString(how), 13f, Hud.PAPER_2, ui.body).apply { setLineSpacing(0f, 1.15f) }, ui.block(4))
        card.isClickable = true
        card.setOnClickListener {
            if (chosen) return@setOnClickListener
            aiChoice = if (id == "keyed") aiKeyed.id else id
            aiStatus?.text = ""
            redraw()
        }
        if (chosen && id == "keyed") {
            val chips = ui.row()
            for (p in CapriSettings.KEYED) {
                val on = p.id == aiKeyed.id
                chips.addView(ui.button(p.name, if (on) Ui.Kind.GREEN else Ui.Kind.LINE) {
                    aiKeyed = p
                    aiChoice = p.id
                    redraw()
                }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = ui.dpi(8) })
            }
            card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, ui.block(12))
            val f = ui.field(getString(R.string.ai_key_hint), secret = true)
            keyField?.text?.let { if (it.isNotEmpty()) f.setText(it) }
            keyField = f
            card.addView(f, ui.block(8))
            card.addView(ui.button(getString(R.string.ai_get_key) + " ↗", Ui.Kind.LINE) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(aiKeyed.keyPage)))
            }, ui.block(10))
        }
        if (chosen && id == "ollama") {
            val f = ui.field(getString(R.string.ai_ip_hint)).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
                setText(ipField?.text ?: CapriSettings.ollamaHost(this@OnboardingActivity).takeIf { !it.startsWith("127.") })
            }
            ipField = f
            card.addView(f, ui.block(10))
        }
        return card
    }

    private fun useAi() {
        if (checking) return
        hideKeyboard()
        val provider = aiChoice
        val key = keyField?.text?.toString().orEmpty()
        val ip = ipField?.text?.toString().orEmpty()
        checking = true
        aiStatus?.setTextColor(Hud.GREEN)
        aiStatus?.text = getString(R.string.ai_checking)
        Thread {
            val why = CapriSettings.setAi(this, provider, key, ip)
            runOnUiThread {
                checking = false
                if (why == null) {
                    aiStatus?.text = getString(R.string.ai_ok, aiName(provider))
                    aiStatus?.postDelayed({ if (at == 2) go(3) }, 900)
                } else {
                    aiStatus?.setTextColor(Hud.RED)
                    aiStatus?.text = if (why == "badkey" || why == "?") getString(R.string.ai_bad_key) else getString(R.string.ai_fail, why)
                }
            }
        }.start()
    }

    private fun aiName(id: String) = when (id) {
        "pollinations" -> getString(R.string.main_ai_free)
        "ollama" -> getString(R.string.main_ai_pc)
        else -> CapriSettings.KEYED.firstOrNull { it.id == id }?.name ?: id
    }

    // ---------- 04: permissions, and why ----------

    private fun permsPage() {
        page.gravity = Gravity.TOP
        says("wide", getString(R.string.ob_perm_line), getString(R.string.ob_perm_how))
        val list = Permissions.all(this)
        for ((i, p) in list.withIndex()) {
            val (view, refresh) = Permissions.row(this, ui, p)
            page.addView(view, ui.block(if (i == 0) 20 else 12))
            ui.glitchIn(view, 300L + i * 110)
            permRows.add(refresh)
        }
    }

    // ---------- 05: ready ----------

    private fun donePage() {
        page.gravity = Gravity.TOP
        says("laugh", getString(R.string.ob_done_line, callMe), getString(R.string.ob_done_how))
    }

    private fun finishAndStart() {
        done(this)
        if (Settings.canDrawOverlays(this) && !CapriService.running) CapriService.start(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    companion object {
        private const val PAGES = 5
        const val EXTRA_ONLY = "only"
        const val PAGE_AI = 2
        private const val PREFS = "app"

        fun needed(context: Context) = !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("welcomed", false)
        fun done(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("welcomed", true).apply()
    }
}

/** Her permissions, each with what it's for: the welcome's page and the Permissions tab share them. */
object Permissions {
    class Perm(val title: Int, val why: Int, val required: Boolean, val granted: () -> Boolean, val ask: () -> Unit)

    fun all(a: Activity): List<Perm> = listOf(
        Perm(R.string.perm_overlay_title, R.string.perm_overlay_why, true, { Settings.canDrawOverlays(a) }) {
            a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
        },
        Perm(R.string.perm_notify_title, R.string.perm_notify_why, true, {
            Build.VERSION.SDK_INT < 33 || a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }) {
            if (Build.VERSION.SDK_INT >= 33) a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        },
        Perm(R.string.perm_notes_title, R.string.perm_notes_why, false, { CapriNotifications.allowed(a) }) {
            a.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        Perm(R.string.perm_install_title, R.string.perm_install_why, false, { a.packageManager.canRequestPackageInstalls() }) {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
        },
        Perm(R.string.perm_mic_title, R.string.perm_mic_why, false, { Ears.allowed(a) }) {
            a.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC)
        },
    )

    const val MIC = 2

    /** Its card (tag, title, why, and a GRANT plate or a mark) and how to redraw it. */
    fun row(a: Activity, ui: Ui, p: Perm): Pair<View, () -> Unit> {
        val card = ui.card()
        val top = ui.row()
        val tag = ui.text(a.getString(if (p.required) R.string.perm_required else R.string.perm_optional), 10f, Hud.INK, ui.mono).apply {
            letterSpacing = 0.12f
            setPadding(ui.dpi(6), ui.dpi(2), ui.dpi(6), ui.dpi(2))
            background = Hud.Plate(if (p.required) Hud.YELLOW else Hud.PAPER_3, 0, cut = 0f, stroke = 0f, offset = 0f)
        }
        top.addView(tag)
        top.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        val mark = ui.text("", 12f, Hud.GREEN, ui.mono).apply { letterSpacing = 0.1f }
        top.addView(mark)
        card.addView(top)
        val title = ui.text(a.getString(p.title), 17f, Hud.PAPER, ui.head)
        card.addView(title, ui.block(8))
        card.addView(ui.text(a.getString(p.why), 14f, Hud.PAPER_2, ui.body).apply { setLineSpacing(0f, 1.18f) }, ui.block(4))
        val grant = ui.button(a.getString(R.string.main_grant) + " ›", Ui.Kind.GREEN) { p.ask() }
        card.addView(grant, ui.block(12))
        val refresh = {
            val ok = p.granted()
            mark.text = if (ok) "✓ " + a.getString(R.string.main_granted) else ""
            grant.visibility = if (ok) View.GONE else View.VISIBLE
            card.background = ui.plate(if (ok) Hud.LINE_STRONG else if (p.required) Hud.YELLOW else Hud.LINE_STRONG)
        }
        refresh()
        return card to refresh
    }
}
