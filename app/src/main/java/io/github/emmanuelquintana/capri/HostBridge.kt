package io.github.emmanuelquintana.capri

import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * The page's line to Android (assets/android/native.js), as preload.js + ipcMain are on the
 * desktop. invoke(id, method, args) answers later through window.__capri.reply(id, ok, json);
 * an empty id is a call nobody waits on. Method names follow the desktop's IPC channels.
 */
class HostBridge(private val service: CapriService) {
    val store = Store(service)
    val log = Diag
    private val actions = Actions(service, store)
    private val fetch = NativeFetch { channel, json -> service.overlay?.emit(channel, json) }
    private val worker = Executors.newSingleThreadExecutor()

    @JavascriptInterface
    fun invoke(id: String, method: String, argsJson: String) {
        worker.execute {
            try {
                val args: Any? = if (argsJson.isEmpty() || argsJson == "null") null else JSONArray("[$argsJson]").get(0)
                val result = handle(method, args)
                service.overlay?.reply(id, true, if (result == null || result == Unit) "null" else toJson(result))
            } catch (e: Exception) {
                if (e !is Actions.Unsupported) {
                    Log.w("capri", "$method failed: ${e.message}")
                    log.add("W", "$method: ${e.message}")
                }
                service.overlay?.reply(id, false, JSONObject().put("error", e.message ?: e.javaClass.simpleName).toString())
            }
        }
    }

    @JavascriptInterface
    fun fetchStart(id: String, url: String, method: String, headers: String, body: String) = fetch.start(id, url, method, headers, body)

    @JavascriptInterface
    fun fetchAbort(id: String) = fetch.abort(id)

    private fun obj(args: Any?) = args as? JSONObject ?: JSONObject()

    private fun handle(method: String, args: Any?): Any? = when (method) {
        // her settings and her memory (config.js, main.js)
        "config:load" -> store.read(Store.CONFIG)?.let { JSONObject(it) }
        "config:save" -> store.write(Store.CONFIG, obj(args).toString()).let { service.onConfig(obj(args)) }
        "memory:load" -> store.read(Store.MEMORY)?.let { JSONObject(it) }
        "memory:save" -> store.write(Store.MEMORY, obj(args).toString())
        "keys:seal" -> store.seal(obj(args).optString("value"))
        "keys:open" -> store.open(obj(args).optString("value"))
        "device:info" -> actions.deviceInfo()

        // her window
        "overlay:ready" -> { service.overlay?.ready(); service.onPageReady() }
        "log" -> log.add(obj(args).optString("level", "E"), obj(args).optString("text"))
        "overlay:focus" -> service.overlay?.setFocusable(obj(args).optBoolean("on"))
        "touch:regions" -> service.overlay?.setRegions(obj(args).optJSONArray("rects") ?: JSONArray(), obj(args).optDouble("dpr", 1.0))
        "notify:update" -> service.refreshNotification()
        "boot:set" -> Unit // read from her config by BootReceiver
        "app:quit" -> service.quit()

        // her hooks, her sounds, links
        "hooks:copy" -> actions.copyHooksCommand(obj(args).optString("command"))
        "sounds:list" -> actions.sounds()
        "sounds:open" -> actions.toast(store.soundsDir.absolutePath)
        "open:url" -> actions.openUrl(obj(args).optString("url"))

        // the phone itself
        "pc:cpu" -> actions.cpu()
        "pc:do" -> actions.pcDo(obj(args))
        "apps:launchables" -> actions.launchables()
        "apps:launch" -> actions.launch(obj(args))
        "apps:media" -> actions.media(args as? String ?: "")
        // your notifications and your music (CapriNotifications.kt), once you allow it
        "notes:allowed" -> CapriNotifications.allowed(service)
        "notes:settings" -> actions.notificationAccess()
        "media:now" -> CapriNotifications.instance?.media() ?: JSONObject()
        "media:control" -> CapriNotifications.instance?.control(obj(args).optString("action")) ?: actions.media(obj(args).optString("action").let { if (it == "toggle") "play" else it })
        "media:open" -> CapriNotifications.instance?.openPlayer() ?: false
        "note:open" -> CapriNotifications.instance?.open(obj(args).optString("key")) ?: false

        // Android never lets an app close another one: her "close Discord" answers with this
        "apps:list" -> JSONArray()
        "apps:find" -> null
        "apps:close" -> JSONObject().put("error", "Android does not let apps close other apps")

        else -> throw Actions.Unsupported(method)
    }

    private fun toJson(v: Any): String = when (v) {
        is JSONObject, is JSONArray -> v.toString()
        is String -> JSONObject.quote(v)
        JSONObject.NULL -> "null"
        else -> v.toString()
    }

    fun shutdown() {
        fetch.shutdown()
        worker.shutdownNow()
    }
}
