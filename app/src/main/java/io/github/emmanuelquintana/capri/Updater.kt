package io.github.emmanuelquintana.capri

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates from the repo's GitHub releases: the latest one, its APK, installed over this one with
 * Android's own installer (it asks you to confirm). Needs every release signed with the same key
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

    /** The APK into the app's cache, with how far along it is (0..1). */
    fun download(context: Context, release: Release, progress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "capri.apk")
        var conn = URL(release.apk).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Capri-Android")
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        // GitHub sends the file from another host: follow it by hand if Java doesn't (https to https is fine)
        var hops = 0
        while (conn.responseCode in 300..399 && hops++ < 5) {
            val next = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = URL(next).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Capri-Android")
        }
        if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
        return file
    }

    /** Hands the APK to Android's installer; it asks you, then InstallResult hears how it went. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("capri.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResult::class.java), flags)
            session.commit(result.intentSender)
        }
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

    /** How the install went: Android's confirmation screen first, then done or why not. */
    class InstallResult : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Diag.add("I", "updated")
                else -> {
                    val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
                    Diag.add("E", "update failed: $why")
                    val msg = if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                        R.string.update_conflict else R.string.update_failed
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
