package io.github.emmanuelquintana.capri

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.util.Locale

/**
 * "Capri, …": her ears and her voice. Only when you turned it on (the app's Settings), with the
 * microphone allowed, and only while the screen is on: Android's speech recognizer, over and over,
 * waiting for her name; what comes after it goes to her page (listen.js), which answers, and she
 * says it out loud (TextToSpeech). Nothing is recorded or kept: the recognizer hands back words,
 * and only the ones after her name leave this class.
 */
class Ears(private val service: CapriService) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false   // the recognizer is on right now
    private var wanted = false      // it should be (on, allowed, screen on, not talking)
    private var speaking = false
    private var awakeUntil = 0L     // just her name: what comes next is for her, even without it
    private var failures = 0
    private var torch = false

    /** Switched on or off, the screen on or off, the mic allowed or not: listening or not. */
    fun update() = main.post {
        val pm = service.getSystemService(PowerManager::class.java)!!
        val should = enabled(service) && allowed(service) && pm.isInteractive && SpeechRecognizer.isRecognitionAvailable(service)
        if (should == wanted) return@post
        wanted = should
        if (should) {
            failures = 0
            Diag.add("I", "listening for her name")
            next(0)
        } else stop()
    }

    private fun stop() {
        main.removeCallbacksAndMessages(null)
        listening = false
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }

    fun destroy() {
        wanted = false
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun next(delay: Long) {
        main.removeCallbacks(go)
        main.postDelayed(go, delay)
    }

    private val go = Runnable { start() }

    private fun start() {
        if (!wanted || speaking || listening) return
        // a call, a voice note, another app recording: the mic is theirs
        val audio = service.getSystemService(AudioManager::class.java)!!
        if (audio.mode != AudioManager.MODE_NORMAL) return next(5000)
        val r = recognizer ?: create().also { recognizer = it }
        val lang = language()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, service.packageName)
        listening = true
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            failed(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    // on the phone itself when it can (Android 12+, its speech pack installed): nothing goes out
    private fun create(): SpeechRecognizer {
        val local = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(service) }.getOrDefault(false)
        val r = if (local && failures < 3) SpeechRecognizer.createOnDeviceSpeechRecognizer(service) else SpeechRecognizer.createSpeechRecognizer(service)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                failures = 0
                heard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                next(150)
            }
            override fun onError(error: Int) {
                listening = false
                failed(error)
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        return r
    }

    private fun failed(error: Int) {
        when (error) {
            // silence, or words that weren't words: listen again right away
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> next(200)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                Diag.add("W", "voice: no microphone permission")
                wanted = false
                stop()
            }
            else -> {
                // busy, no network, the on-device one not ready: a fresh recognizer, a little later each time
                failures++
                recognizer?.let { runCatching { it.destroy() } }
                recognizer = null
                if (failures == 1 || failures % 10 == 0) Diag.add("W", "voice: recognizer error $error")
                next(minOf(60_000L, 1000L * failures * failures))
            }
        }
    }

    private fun heard(options: List<String>) {
        val now = System.currentTimeMillis()
        val named = options.firstOrNull { NAME.containsMatchIn(it) }
        val text = when {
            named != null -> named.substring(NAME.find(named)!!.range.first)
            now < awakeUntil && options.isNotEmpty() -> options[0]
            else -> return
        }
        val rest = NAME.replace(text, "").trim(' ', ',', '.', '!', '?')
        // just her name: what you say in the next few seconds is for her too
        awakeUntil = if (rest.isEmpty() && named != null) now + 8000 else 0L
        service.overlay?.emit("capri:heard", JSONObject().put("text", text).toString())
    }

    /** Her answer, out loud (not on silent or vibrate), and not heard by her own ears meanwhile. */
    fun say(text: String, lang: String) = main.post {
        val audio = service.getSystemService(AudioManager::class.java)!!
        if (text.isBlank() || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return@post
        val speak = {
            tts?.language = Locale.forLanguageTag(lang)
            speaking = true
            recognizer?.let { runCatching { it.cancel() } }
            listening = false
            tts?.speak(text.take(600), TextToSpeech.QUEUE_FLUSH, null, "capri")
            Unit
        }
        if (tts != null && ttsReady) {
            speak()
            return@post
        }
        if (tts == null) {
            tts = TextToSpeech(service) { status ->
                main.post {
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) speak()
                }
            }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = main.post { spoke() }.let {}
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = main.post { spoke() }.let {}
                })
            }
        }
    }

    private fun spoke() {
        speaking = false
        if (wanted) next(300)
    }

    /** The flashlight: on, off, or the other way (null). Null back when the phone has none. */
    fun torch(on: Boolean?): Boolean? {
        val cm = service.getSystemService(CameraManager::class.java) ?: return null
        val id = runCatching {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull() ?: return null
        val want = on ?: !torch
        return runCatching { cm.setTorchMode(id, want); torch = want; want }.getOrNull()
    }

    private fun language(): String {
        val lang = Store(service).configString("lang")?.takeIf { it != "auto" } ?: Locale.getDefault().language
        return when (lang.substringBefore('-')) {
            "es" -> "es-MX"
            "en" -> "en-US"
            "fr" -> "fr-FR"
            "pt" -> "pt-BR"
            "ru" -> "ru-RU"
            else -> Locale.getDefault().toLanguageTag()
        }
    }

    companion object {
        private val NAME = Regex("\\b(capri|kapri|capry|kapry|caprí)\\b", RegexOption.IGNORE_CASE)
        private const val PREF = "voice"

        fun enabled(context: Context) = context.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean(PREF, false)

        fun allowed(context: Context) = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply()
            CapriService.current?.voiceChanged()
        }
    }
}
