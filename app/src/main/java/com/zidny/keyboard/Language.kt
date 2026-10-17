package com.zidny.keyboard

import android.content.Context

/** The languages the keyboard can translate between. [code] is the ISO 639-1 code the language detector and the translation server use. */
enum class Language(val code: String, val label: String, val displayName: String) {
    INDONESIAN("id", "ID", "Indonesia"),
    ENGLISH("en", "EN", "Inggris"),
    FRENCH("fr", "FR", "Prancis"),
    CHINESE("zh", "ZH", "Mandarin"),
    JAPANESE("ja", "JA", "Jepang");

    fun next(): Language = entries[(ordinal + 1) % entries.size]

    companion object {
        private const val PREFS = "settings"
        private const val KEY_TARGET = "target_language"

        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }

        fun loadTarget(context: Context): Language {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TARGET, null)
            return entries.firstOrNull { it.name == saved } ?: ENGLISH
        }

        fun saveTarget(context: Context, language: Language) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TARGET, language.name).apply()
        }
    }
}
