package com.kompres.app.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LanguagePrefs {
    private const val PREFS = "kompres_prefs"
    private const val KEY_LANGUAGE = "language_tag"

    val supported: List<Pair<String, String>> = listOf(
        "" to "System default",
        "en" to "English",
        "ru" to "Русский",
        "es" to "Español",
        "pt-BR" to "Português (Brasil)",
        "de" to "Deutsch",
        "fr" to "Français",
        "zh-CN" to "中文（简体）",
        "ja" to "日本語",
        "ko" to "한국어",
        "ar" to "العربية",
        "hi" to "हिन्दी",
        "id" to "Bahasa Indonesia",
        "tr" to "Türkçe",
        "it" to "Italiano",
        "vi" to "Tiếng Việt"
    )

    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""

    fun setTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
    }

    fun wrap(context: Context): Context {
        val tag = currentTag(context)
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }
}
