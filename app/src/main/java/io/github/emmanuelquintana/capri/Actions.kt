package io.github.emmanuelquintana.capri

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.util.Locale

/**
 * What she does to the phone itself, when her renderer asks: the Android side of the desktop's
 * launch.js, system.js and apps.js. Same requests, same shapes of answers; where Android doesn't
 * let an app do it (closing other apps, shutting the phone down), the desktop's own "no".
 */
class Actions(private val context: Context, private val store: Store) {

    class Unsupported(what: String) : Exception("$what is not possible on Android")

    fun deviceInfo(): JSONObject {
        val am = context.getSystemService(ActivityManager::class.java)!!
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return JSONObject()
            .put("lang", Locale.getDefault().toLanguageTag())
            .put("ram", mem.totalMem)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("termux", installed(TERMUX))
            .put("roblox", installed("com.roblox.client"))
    }

    private fun installed(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** A link, in whatever app handles it (throws when nothing does: launch.js falls back to the web). */
    fun openUrl(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme.isNullOrEmpty() || uri.scheme == "javascript" || uri.scheme == "file" || uri.scheme == "content") {
            throw IllegalArgumentException("not a link")
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Her hooks can't be written into Termux from here: the command goes on your clipboard, and
     * Termux opens to paste it in. ("Install Claude Code hooks" in her menu, "Plug me in" in her guide.)
     */
    fun copyHooksCommand(command: String): JSONObject {
        val clip = context.getSystemService(ClipboardManager::class.java)!!
        clip.setPrimaryClip(ClipData.newPlainText("Capri", command))
        toast(context.getString(R.string.hooks_copied))
        context.packageManager.getLaunchIntentForPackage(TERMUX)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return JSONObject().put("ok", true).put("termux", installed(TERMUX))
    }

    fun toast(text: String) {
        android.os.Handler(context.mainLooper).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    fun sounds(): JSONArray {
        val out = JSONArray()
        val files = store.soundsDir.listFiles() ?: return out
        for (f in files.sortedBy { it.name }) {
            val m = SOUND_EXT.find(f.name) ?: continue
            out.put(JSONObject()
                .put("name", f.name.substring(0, m.range.first).lowercase())
                .put("url", "https://${Overlay.HOST}/usersounds/${Uri.encode(f.name)}"))
        }
        return out
    }

    /** How hard the phone works, as the desktop's processor % (pc.js): its thermal state. */
    fun cpu(): Any {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return JSONObject.NULL
        val pm = context.getSystemService(PowerManager::class.java)!!
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> 15
            PowerManager.THERMAL_STATUS_LIGHT -> 55
            PowerManager.THERMAL_STATUS_MODERATE -> 85
            else -> 98
        }
    }

    // ---------- apps (launch.js) ----------

    /** The phone's apps, as launch.js lists the Start menu's: [{ name, kind: 'app', id }]. */
    fun launchables(): JSONArray {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = JSONArray()
        val seen = HashSet<String>()
        for (ri in pm.queryIntentActivities(main, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            out.put(JSONObject().put("name", ri.loadLabel(pm).toString()).put("kind", "app").put("id", pkg))
        }
        return out
    }

    fun launch(item: JSONObject) {
        if (item.optString("kind") != "app") throw Unsupported("opening that")
        val intent = context.packageManager.getLaunchIntentForPackage(item.optString("id"))
            ?: throw IllegalArgumentException("not found")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The media keys (launch.js media()): whatever plays music reacts. */
    fun media(action: String): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)!!
        val key = when (action) {
            "pause", "play" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "volup" -> {
                repeat(2) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
                return true
            }
            "voldown" -> {
                repeat(2) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
                return true
            }
            "mute" -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            else -> return false
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return true
    }

    // ---------- the phone itself (system.js act()) ----------

    fun pcDo(req: JSONObject): Any = when (req.optString("action")) {
        "settings" -> settings(req.optString("page"))
        "info" -> info(req.optString("what"))
        "volume" -> volume(
            if (req.has("level") && !req.isNull("level")) req.optInt("level") else null,
            if (req.has("mute") && !req.isNull("mute")) req.optBoolean("mute") else null,
        )
        // no snipping tool, no windows to sweep aside or arrange, no theme or power switch for an app
        "snip", "tidy", "theme", "power" -> false
        "arrange" -> JSONObject().put("found", false)
        else -> JSONObject.NULL
    }

    private fun settings(page: String): Boolean {
        val action = when (page) {
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "network" -> Settings.ACTION_WIRELESS_SETTINGS
            "update" -> "android.settings.SYSTEM_UPDATE_SETTINGS"
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "notifications" -> if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_SETTINGS
            "power", "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "background" -> Intent.ACTION_SET_WALLPAPER
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "mouse" -> Settings.ACTION_INPUT_METHOD_SETTINGS
            else -> return false
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
    }

    private fun info(what: String): Any = when (what) {
        "battery" -> {
            val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            if (level < 0) JSONObject().put("none", true)
            else JSONObject()
                .put("percent", Math.round(level * 100f / scale))
                .put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
        "disk" -> {
            val s = StatFs(Environment.getDataDirectory().path)
            JSONObject().put("drive", "Android").put("free", s.availableBytes).put("total", s.totalBytes)
        }
        "ram" -> {
            val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)!!.getMemoryInfo(it) }
            JSONObject().put("total", mem.totalMem).put("used", mem.totalMem - mem.availMem)
        }
        "ip" -> JSONObject().put("ip", localIp() ?: JSONObject.NULL)
        else -> JSONObject.NULL
    }

    fun localIp(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)!!
        val props: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    private fun volume(level: Int?, mute: Boolean?): Any {
        val audio = context.getSystemService(AudioManager::class.java)!!
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (mute != null) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
        }
        if (level != null) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level.coerceIn(0, 100) * max / 100f), AudioManager.FLAG_SHOW_UI)
        }
        val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return JSONObject().put("level", Math.round(now * 100f / max))
    }

    companion object {
        const val TERMUX = "com.termux"
        private val SOUND_EXT = Regex("\\.(mp3|wav|ogg|m4a)$", RegexOption.IGNORE_CASE)
    }
}
