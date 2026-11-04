package io.github.emmanuelquintana.capri

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The door in: the two permissions she needs (being on top of your apps, her notification), then
 * Start. Once she's running, this screen only says so; everything else is in her own menu.
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var overlayBtn: Button
    private lateinit var notifyBtn: Button
    private lateinit var startBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(INK)
        }
        fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, pad / 2, 0, pad / 2)
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            isAllCaps = false
            setTextColor(INK)
            setBackgroundColor(YELLOW)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = pad / 2
            }
        }
        col.addView(text("CAPRI", 34f, YELLOW, bold = true))
        col.addView(text(getString(R.string.main_intro), 16f, PAPER))
        overlayBtn = button(getString(R.string.main_overlay)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        notifyBtn = button(getString(R.string.main_notify)) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startBtn = button(getString(R.string.main_start)) {
            if (CapriService.running) CapriService.send(this, CapriService.ACTION_QUIT) else CapriService.start(this)
            startBtn.postDelayed({ refresh() }, 600)
        }
        status = text("", 15f, CYAN)
        col.addView(overlayBtn)
        col.addView(notifyBtn)
        col.addView(startBtn)
        col.addView(status)
        col.addView(text(getString(R.string.main_license), 12f, GREY).apply { gravity = Gravity.START })
        setContentView(ScrollView(this).apply { addView(col); setBackgroundColor(INK) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = refresh()

    private fun notifyOk() = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        overlayBtn.visibility = if (overlayOk) View.GONE else View.VISIBLE
        notifyBtn.visibility = if (notifyOk()) View.GONE else View.VISIBLE
        startBtn.isEnabled = overlayOk
        startBtn.alpha = if (overlayOk) 1f else 0.4f
        startBtn.text = getString(if (CapriService.running) R.string.main_stop else R.string.main_start)
        status.text = if (CapriService.running) getString(R.string.main_running) else ""
    }

    companion object {
        // her Night City colours (capri.css)
        private val INK = Color.rgb(20, 19, 15)
        private val PAPER = Color.rgb(242, 241, 232)
        private val GREY = Color.rgb(145, 144, 135)
        private val YELLOW = Color.rgb(240, 235, 0)
        private val CYAN = Color.rgb(0, 222, 235)
    }
}
