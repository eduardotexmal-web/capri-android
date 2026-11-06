package io.github.emmanuelquintana.capri

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import org.json.JSONObject

/**
 * Gamer mode on its own: only if you turned it on (Ajustes › Modo gamer › Al abrir un juego) and
 * gave her "Acceso de uso". Every few seconds, with the screen on, she asks Android which app is
 * in front; when it's one its store calls a game, her gamer mode goes on, and off again a little
 * after you leave it. Only the name of the app in front is read, nothing of what's in it, and it
 * never leaves the phone.
 */
class GameWatch(private val service: CapriService) {
    private val main = Handler(Looper.getMainLooper())
    private var onByMe = false   // gamer mode is on because of a game (not because you asked)
    private var leftAt = 0L      // when the game stopped being in front
    private var lastApp = ""
    private var dismissed = ""   // the game you turned gamer mode off in
    private val games = HashMap<String, Boolean>()

    private val tick = object : Runnable {
        override fun run() {
            runCatching { check() }.onFailure { Diag.add("W", "games: ${it.message}") }
            main.postDelayed(this, EVERY)
        }
    }

    fun start() {
        main.removeCallbacks(tick)
        main.postDelayed(tick, EVERY)
    }

    fun stop() = main.removeCallbacks(tick)

    private fun check() {
        if (!enabled(service) || !allowed(service)) {
            if (onByMe) gamer(false)
            return
        }
        if (!service.getSystemService(PowerManager::class.java)!!.isInteractive) return
        val app = front() ?: lastApp
        lastApp = app
        val playing = app.isNotEmpty() && isGame(app)
        val now = System.currentTimeMillis()
        // you turned it off yourself during this game: not again until another app comes in front
        if (onByMe && !CapriService.gamer) {
            onByMe = false
            if (playing) dismissed = app
        }
        if (app != dismissed) dismissed = ""
        when {
            playing && app == dismissed -> Unit
            playing && !CapriService.gamer -> {
                Diag.add("I", "games: $app in front, gamer mode on")
                gamer(true)
            }
            playing -> leftAt = 0L
            onByMe && CapriService.gamer -> {
                if (leftAt == 0L) leftAt = now
                // a quick look at a message mid-game doesn't end it
                if (now - leftAt > LEAVE_AFTER) gamer(false)
            }
        }
    }

    private fun gamer(on: Boolean) {
        leftAt = 0L
        val told = CapriSettings.tell(JSONObject().put("gamer", on)) // only while her page is up
        onByMe = on && told
    }

    // the app in front: the last one Android says came to the front in the last minute
    private fun front(): String? {
        val usm = service.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        @Suppress("DEPRECATION")
        val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == resumed) last = e.packageName
        }
        return last
    }

    // a game, as its store filed it (or as it says of itself)
    private fun isGame(pkg: String): Boolean = games.getOrPut(pkg) {
        if (pkg == service.packageName) return@getOrPut false
        val info = runCatching { service.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@getOrPut false
        @Suppress("DEPRECATION")
        val flagged = info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        flagged || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.category == ApplicationInfo.CATEGORY_GAME)
    }

    companion object {
        private const val EVERY = 4000L
        private const val LEAVE_AFTER = 20_000L
        private const val PREF = "autoGamer"

        fun enabled(context: Context) = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean(PREF, false)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply()
        }

        /** "Acceso de uso" given to her (Android's special access, in its Settings). */
        fun allowed(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
