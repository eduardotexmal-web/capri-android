package io.github.emmanuelquintana.capri

import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * bridge.js, on Android: Claude Code's hooks POST their event to /event on 127.0.0.1:47823, and it
 * goes to her renderer. Same port, same JSON, same rules (bound to 127.0.0.1 only; anything with
 * an Origin header, i.e. a web page, is refused), so the desktop's hooks work unchanged from Termux.
 *
 * It also serves what Termux needs to plug her in: /install.sh, and her hooks and their installers
 * (copied unchanged from capri-desktop into the APK's assets).
 */
class HookServer(
    private val assets: AssetManager,
    private val onEvent: (String) -> Unit,
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    fun start() {
        if (server != null) return
        pool.execute {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
                server = s
                Log.i(TAG, "listening on 127.0.0.1:$PORT")
                while (!s.isClosed) {
                    val client = try { s.accept() } catch (_: Exception) { break }
                    pool.execute { handle(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "port $PORT unavailable, Claude events will not reach her: ${e.message}")
            }
        }
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 5000
            val input = BufferedInputStream(sock.getInputStream())
            val out = sock.getOutputStream()
            try {
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore('?')
                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                // browsers always send an Origin on cross-site requests; hooks never do
                if (headers.containsKey("origin")) return json(out, 403, """{"ok":false}""")

                when {
                    method == "GET" && path == "/health" -> json(out, 200, """{"ok":true,"name":"capri","pid":${android.os.Process.myPid()}}""")
                    method == "POST" && path == "/event" -> {
                        val length = headers["content-length"]?.toIntOrNull() ?: -1
                        if (length > MAX_BODY) return json(out, 400, """{"ok":false,"error":"payload too large"}""")
                        val body = if (length >= 0) readBytes(input, length) else readAll(input)
                        val evt = try { JSONObject(String(body, Charsets.UTF_8)) } catch (_: Exception) {
                            return json(out, 400, """{"ok":false,"error":"invalid json"}""")
                        }
                        if (evt.opt("hook_event_name") !is String) return json(out, 400, """{"ok":false,"error":"missing hook_event_name"}""")
                        onEvent(evt.toString())
                        json(out, 200, """{"ok":true}""")
                    }
                    method == "GET" && path == "/install.sh" -> send(out, 200, "text/x-shellscript", INSTALL_SH.toByteArray())
                    method == "GET" && SERVED.matches(path) -> {
                        val bytes = try { assets.open("capri$path").use { it.readBytes() } } catch (_: Exception) { null }
                        if (bytes == null) json(out, 404, """{"ok":false}""") else send(out, 200, "text/javascript", bytes)
                    }
                    else -> json(out, 404, """{"ok":false}""")
                }
            } catch (e: Exception) {
                Log.w(TAG, "request failed: ${e.message}")
            }
        }
    }

    private fun json(out: OutputStream, status: Int, body: String) = send(out, status, "application/json", body.toByteArray())

    private fun send(out: OutputStream, status: Int, type: String, body: ByteArray) {
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; else -> "Not Found" }
        out.write("HTTP/1.1 $status $reason\r\ncontent-type: $type\r\ncontent-length: ${body.size}\r\nconnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 16 * 1024) return null
        }
    }

    private fun readBytes(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var at = 0
        while (at < n) {
            val r = input.read(out, at, n - at)
            if (r < 0) break
            at += r
        }
        return out.copyOf(at)
    }

    private fun readAll(input: InputStream): ByteArray {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val r = input.read(chunk)
            if (r < 0) break
            buf.write(chunk, 0, r)
            if (buf.size() > MAX_BODY) break
        }
        return buf.toByteArray()
    }

    companion object {
        const val PORT = 47823
        private const val TAG = "capri"
        private const val MAX_BODY = 512 * 1024
        private val SERVED = Regex("^/(hooks/capri(-codex|-agy)?-hook|scripts/install(-codex)?-hooks(-agy)?)\\.js$")

        /**
         * Run in Termux: `curl -fsS http://127.0.0.1:47823/install.sh | sh -s -- [codex|agy] [--uninstall]`.
         * Her hooks go in ~/.capri, then the desktop's own installer adds them to ~/.claude/settings.json
         * (or Codex's, or Antigravity's), keeping what's there and writing a backup first.
         */
        val INSTALL_SH = """
            |#!/bin/sh
            |# Capri for Android: plugs her hooks into Claude Code (or Codex, or Antigravity) in Termux.
            |set -e
            |if ! command -v node >/dev/null 2>&1; then
            |  echo "Capri: installing Node.js (her hooks need it)..."
            |  pkg install -y nodejs
            |fi
            |D="${'$'}HOME/.capri"
            |mkdir -p "${'$'}D/hooks" "${'$'}D/scripts"
            |for f in hooks/capri-hook.js hooks/capri-codex-hook.js hooks/capri-agy-hook.js \
            |         scripts/install-hooks.js scripts/install-codex-hooks.js scripts/install-hooks-agy.js; do
            |  curl -fsS "http://127.0.0.1:$PORT/${'$'}f" -o "${'$'}D/${'$'}f"
            |done
            |S=install-hooks.js
            |case "${'$'}1" in
            |  codex) S=install-codex-hooks.js; shift ;;
            |  agy) S=install-hooks-agy.js; shift ;;
            |esac
            |node "${'$'}D/scripts/${'$'}S" "${'$'}@"
            |echo "Capri: done. Close Claude Code completely and open it again."
            |""".trimMargin()
    }
}
