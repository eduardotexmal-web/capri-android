package io.github.emmanuelquintana.capri

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's language (Ajustes › Idioma): the phone's, or Spanish or English whatever the phone is
 * in. The same choice goes to her (what she says), so the app and she speak the same.
 */
object Lang {
    private const val PREF = "uiLang"
    val CHOICES = listOf("auto", "es", "en")

    fun get(context: Context): String =
        runCatching { context.getSharedPreferences("app", Context.MODE_PRIVATE).getString(PREF, "auto") }.getOrNull() ?: "auto"

    fun set(context: Context, lang: String) {
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString(PREF, lang).apply()
        CapriSettings.set(context, "lang", lang) // her too
    }

    /** A screen's (or her service's) context, in the language picked. */
    fun wrap(base: Context): Context {
        val lang = get(base)
        if (lang !in CHOICES || lang == "auto") return base
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale(lang))
        return base.createConfigurationContext(conf)
    }
}
