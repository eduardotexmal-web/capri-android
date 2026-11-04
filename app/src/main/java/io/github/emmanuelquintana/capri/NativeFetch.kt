package io.github.emmanuelquintana.capri

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * fetch() for her chat (assets/android/fetch.js): the request is made here, so there's no CORS
 * (Ollama on 127.0.0.1, any OpenAI-compatible server), and the body comes back in pieces as it
 * arrives, for her answers to stream in like on the desktop.
 */
class NativeFetch(private val emit: (channel: String, json: String) -> Unit) {
    private val pool = Executors.newCachedThreadPool()
    private val open = ConcurrentHashMap<String, HttpURLConnection>()

    fun start(id: String, url: String, method: String, headersJson: String, body: String) {
        pool.execute {
            var conn: HttpURLConnection? = null
            try {
                val u = URL(url)
                if (u.protocol != "http" && u.protocol != "https") throw IllegalArgumentException("only http(s)")
                conn = (u.openConnection() as HttpURLConnection).apply {
                    requestMethod = method.uppercase()
                    connectTimeout = 15000
                    readTimeout = 180000 // a local model can think a long time before its first word
                    instanceFollowRedirects = true
                    val headers = JSONObject(headersJson.ifEmpty { "{}" })
                    for (k in headers.keys()) setRequestProperty(k, headers.getString(k))
                }
                open[id] = conn
                if (body.isNotEmpty()) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = conn.responseCode
                val head = JSONObject()
                for ((k, v) in conn.headerFields) if (k != null && v.isNotEmpty()) head.put(k.lowercase(), v.joinToString(", "))
                emit("fetch:head", JSONObject().put("id", id).put("status", status).put("headers", head).toString())
                val stream = if (status >= 400) conn.errorStream else conn.inputStream
                stream?.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n == 0) continue
                        val data = Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)
                        emit("fetch:chunk", JSONObject().put("id", id).put("data", data).toString())
                    }
                }
                emit("fetch:end", JSONObject().put("id", id).toString())
            } catch (e: Exception) {
                if (open.containsKey(id)) {
                    emit("fetch:error", JSONObject().put("id", id).put("error", e.message ?: e.javaClass.simpleName).toString())
                }
            } finally {
                open.remove(id)
                conn?.disconnect()
            }
        }
    }

    fun abort(id: String) {
        open.remove(id)?.let { c -> pool.execute { c.disconnect() } }
    }

    fun shutdown() {
        for (c in open.values) c.disconnect()
        open.clear()
        pool.shutdownNow()
    }
}
