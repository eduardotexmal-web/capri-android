package io.github.emmanuelquintana.capri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the repo's GitHub releases: the latest one, its APK, installed over this one with
 * your browser, which hands it to Android's installer. Needs every release signed with the same key
 * (CI's CAPRI_KEYSTORE secrets), or Android refuses to install over.
 */
object Updater {
    const val REPO = "emmanuelquintana/capri-android"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 2
    private const val PREFS = "updater"
    const val CHECK_EVERY = 4 * 3600 * 1000L

    data class Release(val tag: String, val apk: String, val size: Long, val page: String, val notes: String) {
        val version get() = tag.removePrefix("v")
    }

    /** What a release says it brings (its CHANGELOG.md section), without the install footer, in plain text. */
    private fun plain(body: String): String = body.substringBefore("\n---").lines()
        .map { it.trim().replace(Regex("\\*\\*|`|\\*"), "").replace(Regex("^- "), "• ") }
        .joinToString("\n").replace(Regex("\n(?!•)"), " ").trim()

    /** The latest release, or null when offline or there's none with an APK. Network: not on the main thread. */
    fun latest(): Release? = try {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Capri-Android")
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        if (conn.responseCode != 200) null
        else {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var found: Release? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    found = Release(json.optString("tag_name"), a.optString("browser_download_url"), a.optLong("size"), json.optString("html_url"), plain(json.optString("body")))
                    break
                }
            }
            found
        }
    } catch (e: Exception) {
        Diag.add("W", "update check: ${e.message}")
        null
    }

    /** Is that tag newer than this build? "v0.5.1-android.4" against "0.5.1-android.3": number by number. */
    fun isNewer(tag: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        val a = Regex("\\d+").findAll(tag).map { it.value.toLong() }.toList()
        val b = Regex("\\d+").findAll(current).map { it.value.toLong() }.toList()
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Every few hours at most, from her service: a notification (and her own word) when there's a newer release. */
    fun checkInBackground(context: Context, onNew: (Release) -> Unit = {}) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("checked", 0) < CHECK_EVERY) return
        Thread {
            val release = latest() ?: return@Thread
            prefs.edit().putLong("checked", now).apply()
            if (!isNewer(release.tag) || prefs.getString("told", null) == release.tag) return@Thread
            prefs.edit().putString("told", release.tag).apply()
            notifyNew(context, release)
            onNew(release)
        }.start()
    }

    private fun notifyNew(context: Context, release: Release) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.getString(R.string.update_title, release.version))
            .setContentText(release.notes.lineSequence().firstOrNull()?.removePrefix("• ") ?: context.getString(R.string.update_text))
            .setStyle(Notification.BigTextStyle().bigText(release.notes.ifBlank { context.getString(R.string.update_text) } + "\n\n" + context.getString(R.string.update_text)))
            .setColor(Hud.YELLOW)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }
}
