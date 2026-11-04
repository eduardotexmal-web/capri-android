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

    override fun onCreate() {
        super.onCreate()
        running = true
        bridge = HostBridge(this)
        visible = bridge.store.configFlag("visible", true)
        createChannel()
        startForegroundCompat()
        if (!Settings.canDrawOverlays(this)) {
            // no right to be on top: the app asks for it (MainActivity), she starts from there
            stopSelf()
            return
        }
        overlay = Overlay(this, bridge).also { it.show() }
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
        }
        return START_STICKY
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

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(if (visible) getString(R.string.notify_text, HookServer.PORT) else getString(R.string.notify_text_hidden))
            .setContentIntent(pending(ACTION_MENU, 1)) // a tap on it is a click on her tray icon: her menu
            .setOngoing(true)
            .setShowWhen(false)
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
        private const val CHANNEL = "capri"
        private const val NOTIFICATION_ID = 1
        const val ACTION_MENU = "capri.MENU"
        const val ACTION_TOGGLE = "capri.TOGGLE"
        const val ACTION_QUIT = "capri.QUIT"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CapriService::class.java))
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, CapriService::class.java).setAction(action))
        }
    }
}
