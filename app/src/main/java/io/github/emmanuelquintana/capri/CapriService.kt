package io.github.emmanuelquintana.capri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import org.json.JSONObject

/**
 * Capri's process, as main.js is on the desktop: her window (Overlay), the hook server Claude
 * Code reaches (HookServer), and her "tray icon", which on a phone is this service's notification:
 * Menu, Put away / Bring back (Ctrl+Alt+C), Quit.
 */
class CapriService : Service() {
    var overlay: Overlay? = null
        private set
    private lateinit var bridge: HostBridge
    private var hooks: HookServer? = null
    private var visible = true

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> overlay?.pause()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> overlay?.resume()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.turned()
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        current = this
        bridge = HostBridge(this)
        visible = bridge.store.configFlag("visible", true)
        createChannel()
        checkUpdates()
        startForegroundCompat()
        Diag.add("I", "service started (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        if (!Settings.canDrawOverlays(this)) {
            Diag.add("E", "no permission to show over other apps")
            // no right to be on top: the app asks for it (MainActivity), she starts from there
            stopSelf()
            return
        }
        overlay = try {
            Overlay(this, bridge).also { it.show() }
        } catch (e: Exception) {
            Diag.add("E", "her window could not open: ${e.message}")
            null
        }
        hooks = HookServer(assets) { evt -> overlay?.emit("claude:event", evt) }.also { it.start() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(screen, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screen, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MENU -> command("menu")
            ACTION_TOGGLE -> command("toggle")
            ACTION_QUIT -> quit()
            ACTION_SETUP -> if (pageReady) command("setupChat") else queued = "setupChat"
        }
        return START_STICKY
    }

    // a command for her page from the app's screen, held until her page is up
    @Volatile var pageReady = false
        private set
    @Volatile private var queued: String? = null

    fun onPageReady() {
        pageReady = true
        // after her intro, so her panel doesn't land on top of her first hello
        queued?.let { queued = null; android.os.Handler(mainLooper).postDelayed({ command(it) }, 6000) }
    }

    private fun command(name: String) = overlay?.emit("capri:command", JSONObject().put("name", name).toString())

    /** Her settings changed (host.js saved them): what the notification shows may have too. */
    fun onConfig(cfg: JSONObject) {
        val v = cfg.optBoolean("visible", true)
        if (v != visible) {
            visible = v
            refreshNotification()
        }
    }

    fun refreshNotification() {
        getSystemService(NotificationManager::class.java)!!.notify(NOTIFICATION_ID, buildNotification())
    }

    fun quit() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        ticker.removeCallbacks(check)
        if (current === this) current = null
        Diag.add("I", "service stopped")
        try { unregisterReceiver(screen) } catch (_: Exception) {}
        hooks?.stop()
        overlay?.destroy()
        overlay = null
        if (::bridge.isInitialized) bridge.shutdown()
        super.onDestroy()
    }

    // ---------- her notification: the tray icon's place ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)!!
        val ch = NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        ch.description = getString(R.string.channel_desc)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun pending(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, CapriService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // a newer release: her notification, and she says so on screen too (host.js)
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())
    private val check = Runnable { checkUpdates() }

    private fun checkUpdates() {
        Updater.checkInBackground(this) { r ->
            val json = JSONObject().put("version", r.version).put("notes", r.notes).toString()
            ticker.postDelayed({ overlay?.emit("capri:update", json) }, if (pageReady) 0 else 20_000)
        }
        ticker.removeCallbacks(check)
        ticker.postDelayed(check, Updater.CHECK_EVERY + 60_000)
    }

    // her last word, for her notification (a wink in your notification shade): what she said, her face
    private var wink: String? = null
    private var winkFace = "smug"
    private var winkAt = 0L
    private val faces = HashMap<String, android.graphics.Bitmap?>()

    fun wink(line: String, face: String) {
        wink = line.take(200)
        winkFace = face
        winkAt = System.currentTimeMillis()
        refreshNotification()
    }

    private fun face(name: String): android.graphics.Bitmap? = faces.getOrPut(name) {
        val file = if (name in FACES) "face-$name.png" else "capri-face.png"
        runCatching { assets.open("capri/assets/capri/$file").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    private fun buildNotification(): Notification {
        val said = wink?.takeIf { visible }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(if (said != null) "Capri" else getString(R.string.notify_title))
            .setContentText(said ?: if (visible) getString(R.string.notify_text, HookServer.PORT) else getString(R.string.notify_text_hidden))
            .also { b ->
                if (said != null) {
                    b.setStyle(Notification.BigTextStyle().bigText(said))
                    b.setWhen(winkAt).setShowWhen(true)
                } else b.setShowWhen(false)
                face(if (said != null) winkFace else "smug")?.let { b.setLargeIcon(it) }
            }
            // her colours: the shade shows her plate in ink, with her yellow
            .setColor(Hud.INK)
            .setColorized(true)
            .setContentIntent(pending(ACTION_MENU, 1)) // a tap on it is a click on her tray icon: her menu
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_menu), pending(ACTION_MENU, 2)).build())
            .addAction(Notification.Action.Builder(null, getString(if (visible) R.string.action_hide else R.string.action_show), pending(ACTION_TOGGLE, 3)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.action_quit), pending(ACTION_QUIT, 4)).build())
            .also { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
            .build()
    }

    companion object {
        @Volatile
        var running = false
            private set
        @Volatile
        var current: CapriService? = null
            private set
        private const val CHANNEL = "capri"
        private const val NOTIFICATION_ID = 1
        const val ACTION_MENU = "capri.MENU"
        const val ACTION_TOGGLE = "capri.TOGGLE"
        const val ACTION_QUIT = "capri.QUIT"
        const val ACTION_SETUP = "capri.SETUP"
        private val FACES = setOf("aside", "calm", "laugh", "look", "many", "smug", "wide")

        fun start(context: Context, action: String? = null) {
            context.startForegroundService(Intent(context, CapriService::class.java).setAction(action))
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, CapriService::class.java).setAction(action))
        }
    }
}
