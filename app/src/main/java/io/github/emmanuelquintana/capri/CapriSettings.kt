package io.github.emmanuelquintana.capri

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * What the app's screens set for her: what she calls you, and who answers her (her AI). While
 * she's on, her page owns her settings, so they go to it (host.js `settings:apply`); while she's
 * off, straight into config.json, the way her page would have saved them.
 */
object CapriSettings {
    data class Provider(val id: String, val name: String, val url: String, val keyPage: String)

    /** The services that take a key, as her setup offers them (Groq and Gemini are free). */
    val KEYED = listOf(
        Provider("groq", "Groq", "https://api.groq.com/openai/v1", "https://console.groq.com/keys"),
        Provider("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "https://aistudio.google.com/apikey"),
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "https://openrouter.ai/keys"),
        Provider("openai", "OpenAI", "https://api.openai.com/v1", "https://platform.openai.com/api-keys"),
        Provider("mistral", "Mistral", "https://api.mistral.ai/v1", "https://console.mistral.ai/api-keys"),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "https://platform.deepseek.com/api_keys"),
    )

    fun name(context: Context): String = Store(context).configString("userName") ?: ""
    fun provider(context: Context): String = Store(context).configString("provider") ?: "pollinations"
    fun ollamaHost(context: Context): String = Store(context).configString("ollamaHost") ?: ""

    fun setName(context: Context, name: String) {
        val clean = name.trim().take(24)
        apply(context, JSONObject().put("userName", clean)) { it.put("userName", clean) }
    }

    /**
     * Her AI: checked first (a key that works, an Ollama that answers), then saved. Returns null
     * when it's set, or why not. Network: not on the main thread.
     */
    fun setAi(context: Context, provider: String, key: String = "", address: String = ""): String? {
        when (provider) {
            "pollinations" -> Unit
            "ollama" -> {
                val host = ollama(address) ?: return "?"
                val ok = runCatching { get("http://$host/api/tags", null) }.getOrElse { return it.message ?: "offline" }
                if (ok !in 200..299) return "HTTP $ok"
                apply(context, JSONObject().put("provider", "ollama").put("url", host)) { it.put("provider", "ollama").put("ollamaHost", host) }
                return null
            }
            else -> {
                val p = KEYED.firstOrNull { it.id == provider } ?: return "?"
                val k = key.trim()
                if (k.isEmpty()) return "?"
                val ok = runCatching { get("${p.url}/models", k) }.getOrElse { return it.message ?: "offline" }
                if (ok == 401 || ok == 403) return "badkey"
                if (ok !in 200..299) return "HTTP $ok"
                val sealed = Store(context).seal(k)
                apply(context, JSONObject().put("provider", provider).put("key", k)) { cfg ->
                    val keys = cfg.optJSONObject("apiKeys") ?: JSONObject()
                    cfg.put("provider", provider).put("apiKeys", keys.put(provider, sealed))
                }
                return null
            }
        }
        apply(context, JSONObject().put("provider", provider)) { it.put("provider", provider) }
        return null
    }

    /** One of her settings (size, ghost, lively): to her page if she's on, else into config.json. */
    fun set(context: Context, key: String, value: Any) {
        apply(context, JSONObject().put(key, value)) { it.put(key, value) }
    }

    /** Something only she can do right now (look at your screen): false if she isn't on. */
    fun tell(live: JSONObject): Boolean {
        val service = CapriService.current ?: return false
        if (!service.pageReady) return false
        service.overlay?.emit("settings:apply", live.toString())
        return true
    }

    /** "192.168.1.20" → "192.168.1.20:11434" (Ollama's port when none is given). */
    fun ollama(address: String): String? {
        val host = address.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        if (host.isEmpty()) return null
        return if (Regex(":\\d+$").containsMatchIn(host)) host else "$host:11434"
    }

    private fun get(url: String, key: String?): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "Capri-Android")
        if (key != null) c.setRequestProperty("Authorization", "Bearer $key")
        return try { c.responseCode } finally { c.disconnect() }
    }

    /** To her page if it's up, else into config.json (merged, keeping everything else). */
    private fun apply(context: Context, live: JSONObject, saved: (JSONObject) -> Unit) {
        val service = CapriService.current
        if (service != null && service.pageReady) {
            service.overlay?.emit("settings:apply", live.toString())
            return
        }
        val store = Store(context)
        val cfg = runCatching { JSONObject(store.read(Store.CONFIG) ?: "{}") }.getOrElse { JSONObject() }
        saved(cfg)
        store.write(Store.CONFIG, cfg.toString())
    }
}
