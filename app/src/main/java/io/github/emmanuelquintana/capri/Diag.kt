package io.github.emmanuelquintana.capri

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What went wrong lately, for the app's screen (MainActivity): her page's errors, failed calls,
 * the service starting and stopping. Kept in memory, the last few dozen lines.
 */
object Diag {
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)
    var onChange: (() -> Unit)? = null

    @Synchronized
    fun add(level: String, text: String) {
        lines.addLast("${time.format(Date())} $level ${text.take(400)}")
        android.util.Log.println(if (level == "E") android.util.Log.ERROR else if (level == "W") android.util.Log.WARN else android.util.Log.INFO, "capri", text.take(400))
        while (lines.size > 60) lines.removeFirst()
        onChange?.invoke()
    }

    @Synchronized
    fun all(): List<String> = lines.toList()

    @Synchronized
    fun clear() = lines.clear()
}
