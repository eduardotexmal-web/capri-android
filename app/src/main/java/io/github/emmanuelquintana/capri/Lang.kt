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

    /**
     * The languages the app is translated into. Another one: its res/values-<code>/strings.xml,
     * its code here, and it shows up in Ajustes with its own name. (Her own lines, in her page,
     * already come in es, en, fr, pt and ru.)
     */
    val LANGS = listOf("es", "en")

    /** A language in its own words: "Español", "English", "Français"… */
    fun name(code: String): String {
        val locale = Locale(code)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    fun get(context: Context): String =
        runCatching { context.getSharedPreferences("app", Context.MODE_PRIVATE).getString(PREF, "auto") }.getOrNull() ?: "auto"

    fun set(context: Context, lang: String) {
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putString(PREF, lang).apply()
        CapriSettings.set(context, "lang", lang) // her too
    }

    /** A screen's (or her service's) context, in the language picked. */
    fun wrap(base: Context): Context {
        val lang = get(base)
        if (lang !in LANGS) return base // "auto": the phone's
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(Locale(lang))
        return base.createConfigurationContext(conf)
    }
}
