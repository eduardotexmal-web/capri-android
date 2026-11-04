package io.github.emmanuelquintana.capri

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * Your notifications and your music, for her (assets/android/notes.js): what plays now (title,
 * artist, the app, playing or not) and each new notice (the app, its title, never its text). She
 * gets a card for each to jump on, as she sits on your windows on the desktop.
 *
 * Optional: Android only lets this run once you turn on "notification access" for Capri (the
 * app's screen has the step). Nothing is kept or sent anywhere; it only goes to her page.
 */
class CapriNotifications : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var sessions: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()
    private var lastMedia = ""

    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onSessionDestroyed() = publishMedia()
    }

    override fun onListenerConnected() {
        instance = this
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = manager
        val me = ComponentName(this, CapriNotifications::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(changed, me, main)
            watch(manager.getActiveSessions(me))
        } catch (e: SecurityException) {
            Diag.add("W", "media sessions: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        sessions?.removeOnActiveSessionsChangedListener(changed)
        controllers.forEach { it.unregisterCallback(callback) }
        controllers.clear()
        if (instance === this) instance = null
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers.clear()
        list.forEach { it.registerCallback(callback, main); controllers.add(it) }
        publishMedia()
    }

    /** The controller that plays now, else the first one there is (paused). */
    private fun current(): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()

    private fun publishMedia() {
        val c = current()
        val meta = c?.metadata
        val json = JSONObject()
        if (c != null && meta != null) {
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            json.put("title", title ?: "")
                .put("artist", meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "")
                .put("app", label(c.packageName))
                .put("pkg", c.packageName)
                .put("playing", c.playbackState?.state == PlaybackState.STATE_PLAYING)
        }
        val text = json.toString()
        if (text == lastMedia) return
        lastMedia = text
        CapriService.current?.overlay?.emit("media:update", text)
    }

    /** What the page has now (it asks when it starts). */
    fun media(): JSONObject = JSONObject(lastMedia.ifEmpty { "{}" })

    fun control(action: String): Boolean {
        val t = current()?.transportControls ?: return false
        when (action) {
            "play" -> t.play()
            "pause" -> t.pause()
            "toggle" -> if (current()?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            "next" -> t.skipToNext()
            "previous" -> t.skipToPrevious()
            else -> return false
        }
        return true
    }

    /** The app that plays, in front. */
    fun openPlayer(): Boolean {
        val c = current() ?: return false
        c.sessionActivity?.let { runCatching { it.send() }.onSuccess { return true } }
        val intent = packageManager.getLaunchIntentForPackage(c.packageName) ?: return false
        startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return // the music: its own card
        if (n.category in QUIET) return
        // only what would make a sound or pop up: no silent ones, no updates of the same notice
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) == true && ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT) return
        if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 && sbn.key in seen) return
        seen.add(sbn.key)
        if (seen.size > 200) seen.remove(seen.first())
        posted[sbn.key] = sbn
        if (posted.size > 100) posted.remove(posted.keys.first())
        val title = n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)
        val json = JSONObject()
            .put("key", sbn.key)
            .put("app", label(sbn.packageName))
            .put("pkg", sbn.packageName)
            .put("title", title?.toString()?.take(80) ?: "")
            .put("category", n.category ?: "")
        CapriService.current?.overlay?.emit("note:posted", json.toString())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        posted.remove(sbn.key)
        CapriService.current?.overlay?.emit("note:removed", JSONObject().put("key", sbn.key).toString())
    }

    /** A notice tapped on her card: what tapping it in the shade would do. */
    fun open(key: String): Boolean {
        val sbn = posted[key] ?: return false
        val intent = sbn.notification.contentIntent ?: return false
        runCatching { intent.send() }.onFailure { return false }
        if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) runCatching { cancelNotification(key) }
        return true
    }

    private val seen = LinkedHashSet<String>()
    private val posted = LinkedHashMap<String, StatusBarNotification>()

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    companion object {
        @Volatile
        var instance: CapriNotifications? = null
            private set

        private val QUIET = setOf(
            Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STATUS, Notification.CATEGORY_TRANSPORT,
        )

        /** Has Capri been given notification access (Settings › Notification access)? */
        fun allowed(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, CapriNotifications::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
