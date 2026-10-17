package com.zidny.keyboard

import android.content.Context
import android.graphics.Color

/** A colour scheme for the keyboard. Colours are ARGB ints. */
enum class KeyboardTheme(
    val displayName: String,
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val text: Int,
    val secondaryText: Int,
    val accent: Int,
    val cornerRadiusDp: Int,
) {
    SAMSUNG_LIGHT(
        "Samsung Terang",
        Color.parseColor("#E9EAEE"), Color.parseColor("#FFFFFF"), Color.parseColor("#D3D6DC"),
        Color.parseColor("#1C1C1E"), Color.parseColor("#6B6E75"), Color.parseColor("#3E91FF"), 10,
    ),
    SAMSUNG_DARK(
        "Samsung Gelap",
        Color.parseColor("#141416"), Color.parseColor("#2C2C2F"), Color.parseColor("#1F1F22"),
        Color.parseColor("#F2F2F2"), Color.parseColor("#9A9DA3"), Color.parseColor("#3E91FF"), 10,
    ),
    CLASSIC_DARK(
        "Klasik Gelap",
        Color.parseColor("#1E1F22"), Color.parseColor("#4A4D53"), Color.parseColor("#3A3C41"),
        Color.WHITE, Color.parseColor("#B0B3B8"), Color.parseColor("#2E7D32"), 6,
    ),
    OCEAN(
        "Biru Laut",
        Color.parseColor("#0F2A43"), Color.parseColor("#1D4468"), Color.parseColor("#163756"),
        Color.WHITE, Color.parseColor("#A9C4DD"), Color.parseColor("#00B4D8"), 10,
    ),
    SAKURA(
        "Sakura",
        Color.parseColor("#FBE9EF"), Color.parseColor("#FFFFFF"), Color.parseColor("#F4C9D6"),
        Color.parseColor("#3A2430"), Color.parseColor("#8A6573"), Color.parseColor("#E0628C"), 12,
    ),
}

/** Everything the user can change about how the keyboard looks and feels. */
data class KeyboardSettings(
    val theme: KeyboardTheme = KeyboardTheme.SAMSUNG_LIGHT,
    val layout: KeyboardLayout = KeyboardLayout.QWERTY,
    /** Key height as a percentage of the default height. */
    val heightPercent: Int = 100,
    /** Letter size in sp. */
    val textSize: Int = 20,
    /** Space between neighbouring keys in dp. */
    val keyGap: Int = 6,
    /** How long a key must be held before its extra characters appear, in ms. */
    val longPressMs: Int = 350,
    val vibrate: Boolean = true,
    val sound: Boolean = false,
    /** Show an enlarged letter above the key while it is pressed. */
    val keyPreview: Boolean = true,
    val numberRow: Boolean = true,
    val autoCaps: Boolean = true,
    /** Two spaces in a row type ". " instead. */
    val doubleSpacePeriod: Boolean = true,
    val suggestions: Boolean = true,
    val autocorrect: Boolean = true,
    /** Translate messages the user copies into Indonesian and show them above the keys. */
    val clipboardTranslate: Boolean = false,
    /** Show the translation first and only replace the text when the user taps "Pakai". */
    val translatePreview: Boolean = false,
) {
    companion object {
        const val MIN_HEIGHT = 70
        const val MAX_HEIGHT = 140
        const val MIN_TEXT = 14
        const val MAX_TEXT = 28
        const val MIN_GAP = 0
        const val MAX_GAP = 12
        const val MIN_LONG_PRESS = 200
        const val MAX_LONG_PRESS = 800

        private const val PREFS = "settings"

        fun load(context: Context): KeyboardSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val defaults = KeyboardSettings()
            return KeyboardSettings(
                theme = KeyboardTheme.entries.firstOrNull { it.name == p.getString("theme", null) } ?: defaults.theme,
                layout = KeyboardLayout.entries.firstOrNull { it.name == p.getString("layout", null) } ?: defaults.layout,
                heightPercent = p.getInt("height_percent", defaults.heightPercent).coerceIn(MIN_HEIGHT, MAX_HEIGHT),
                textSize = p.getInt("text_size", defaults.textSize).coerceIn(MIN_TEXT, MAX_TEXT),
                keyGap = p.getInt("key_gap", defaults.keyGap).coerceIn(MIN_GAP, MAX_GAP),
                longPressMs = p.getInt("long_press_ms", defaults.longPressMs).coerceIn(MIN_LONG_PRESS, MAX_LONG_PRESS),
                vibrate = p.getBoolean("vibrate", defaults.vibrate),
                sound = p.getBoolean("sound", defaults.sound),
                keyPreview = p.getBoolean("key_preview", defaults.keyPreview),
                numberRow = p.getBoolean("number_row", defaults.numberRow),
                autoCaps = p.getBoolean("auto_caps", defaults.autoCaps),
                doubleSpacePeriod = p.getBoolean("double_space_period", defaults.doubleSpacePeriod),
                suggestions = p.getBoolean("suggestions", defaults.suggestions),
                autocorrect = p.getBoolean("autocorrect", defaults.autocorrect),
                clipboardTranslate = p.getBoolean("clipboard_translate", defaults.clipboardTranslate),
                translatePreview = p.getBoolean("translate_preview", defaults.translatePreview),
            )
        }

        fun save(context: Context, settings: KeyboardSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("theme", settings.theme.name)
                .putString("layout", settings.layout.name)
                .putInt("height_percent", settings.heightPercent)
                .putInt("text_size", settings.textSize)
                .putInt("key_gap", settings.keyGap)
                .putInt("long_press_ms", settings.longPressMs)
                .putBoolean("vibrate", settings.vibrate)
                .putBoolean("sound", settings.sound)
                .putBoolean("key_preview", settings.keyPreview)
                .putBoolean("number_row", settings.numberRow)
                .putBoolean("auto_caps", settings.autoCaps)
                .putBoolean("double_space_period", settings.doubleSpacePeriod)
                .putBoolean("suggestions", settings.suggestions)
                .putBoolean("autocorrect", settings.autocorrect)
                .putBoolean("clipboard_translate", settings.clipboardTranslate)
                .putBoolean("translate_preview", settings.translatePreview)
                .apply()
        }
    }
}
