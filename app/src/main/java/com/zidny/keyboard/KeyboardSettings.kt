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
    /** Key height as a percentage of the default height. */
    val heightPercent: Int = 100,
    /** Letter size in sp. */
    val textSize: Int = 20,
    val vibrate: Boolean = true,
    val numberRow: Boolean = true,
) {
    companion object {
        const val MIN_HEIGHT = 70
        const val MAX_HEIGHT = 140
        const val MIN_TEXT = 14
        const val MAX_TEXT = 28

        private const val PREFS = "settings"

        fun load(context: Context): KeyboardSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val defaults = KeyboardSettings()
            return KeyboardSettings(
                theme = KeyboardTheme.entries.firstOrNull { it.name == p.getString("theme", null) } ?: defaults.theme,
                heightPercent = p.getInt("height_percent", defaults.heightPercent).coerceIn(MIN_HEIGHT, MAX_HEIGHT),
                textSize = p.getInt("text_size", defaults.textSize).coerceIn(MIN_TEXT, MAX_TEXT),
                vibrate = p.getBoolean("vibrate", defaults.vibrate),
                numberRow = p.getBoolean("number_row", defaults.numberRow),
            )
        }

        fun save(context: Context, settings: KeyboardSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("theme", settings.theme.name)
                .putInt("height_percent", settings.heightPercent)
                .putInt("text_size", settings.textSize)
                .putBoolean("vibrate", settings.vibrate)
                .putBoolean("number_row", settings.numberRow)
                .apply()
        }
    }
}
