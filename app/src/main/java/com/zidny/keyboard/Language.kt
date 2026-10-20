package com.zidny.keyboard

import android.content.Context

/** The languages the keyboard can translate between. [code] is the ISO 639-1 code the language detector and the translation server use. */
enum class Language(val code: String, val label: String, val displayName: String, val flag: String) {
    INDONESIAN("id", "ID", "Indonesia", "🇮🇩"),
    ENGLISH("en", "EN", "Inggris", "🇬🇧"),
    FRENCH("fr", "FR", "Prancis", "🇫🇷"),
    CHINESE("zh", "ZH", "Mandarin", "🇨🇳"),
    JAPANESE("ja", "JA", "Jepang", "🇯🇵"),
    KOREAN("ko", "KO", "Korea", "🇰🇷"),
    SPANISH("es", "ES", "Spanyol", "🇪🇸"),
    FILIPINO("tl", "TL", "Filipina", "🇵🇭"),
    VIETNAMESE("vi", "VI", "Vietnam", "🇻🇳"),
    THAI("th", "TH", "Thailand", "🇹🇭"),
    RUSSIAN("ru", "RU", "Rusia", "🇷🇺");

    companion object {
        private const val PREFS = "settings"
        private const val KEY_TARGET = "target_language"
        private const val KEY_STYLE = "translation_style"
        private const val APP_PREFS = "app_languages"

        // The language detector may call Filipino "fil" rather than "tl".
        fun fromCode(code: String?): Language? = if (code == "fil") FILIPINO else entries.firstOrNull { it.code == code }

        fun loadTarget(context: Context): Language {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TARGET, null)
            return entries.firstOrNull { it.name == saved } ?: ENGLISH
        }

        fun saveTarget(context: Context, language: Language) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TARGET, language.name).apply()
        }

        fun loadStyle(context: Context): TranslationStyle {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STYLE, null)
            return TranslationStyle.entries.firstOrNull { it.name == saved } ?: TranslationStyle.CASUAL
        }

        fun saveStyle(context: Context, style: TranslationStyle) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STYLE, style.name).apply()
        }

        /** The language and style last used in the app [packageName], or null if none was picked there yet. */
        fun loadForApp(context: Context, packageName: String): Pair<Language, TranslationStyle>? {
            val saved = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).getString(packageName, null)
            return parseAppChoice(saved)
        }

        fun saveForApp(context: Context, packageName: String, language: Language, style: TranslationStyle) {
            context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
                .edit().putString(packageName, "${language.name}|${style.name}").apply()
        }

        fun parseAppChoice(saved: String?): Pair<Language, TranslationStyle>? {
            val parts = saved?.split('|') ?: return null
            val language = entries.firstOrNull { it.name == parts[0] } ?: return null
            val style = TranslationStyle.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: TranslationStyle.CASUAL
            return language to style
        }
    }
}
