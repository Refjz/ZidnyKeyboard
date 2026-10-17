package com.zidny.keyboard

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage

/** The languages the keyboard can translate between. [code] is the ML Kit language code. */
enum class Language(val code: String, val label: String, val displayName: String) {
    INDONESIAN(TranslateLanguage.INDONESIAN, "ID", "Indonesia"),
    ENGLISH(TranslateLanguage.ENGLISH, "EN", "Inggris"),
    FRENCH(TranslateLanguage.FRENCH, "FR", "Prancis"),
    CHINESE(TranslateLanguage.CHINESE, "ZH", "Mandarin"),
    JAPANESE(TranslateLanguage.JAPANESE, "JA", "Jepang");

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
