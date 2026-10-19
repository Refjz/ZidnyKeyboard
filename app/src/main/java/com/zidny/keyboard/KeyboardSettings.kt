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
    MINT(
        "Hijau Mint",
        Color.parseColor("#DFF3EA"), Color.parseColor("#FFFFFF"), Color.parseColor("#B8E3CF"),
        Color.parseColor("#163A2C"), Color.parseColor("#4E7A68"), Color.parseColor("#1FA971"), 10,
    ),
    LAVENDER(
        "Ungu Lavender",
        Color.parseColor("#ECE6FA"), Color.parseColor("#FFFFFF"), Color.parseColor("#D3C7F2"),
        Color.parseColor("#2B2140"), Color.parseColor("#6E6390"), Color.parseColor("#7C5CE0"), 12,
    ),
    SUNSET(
        "Senja Oranye",
        Color.parseColor("#FFE8D6"), Color.parseColor("#FFFFFF"), Color.parseColor("#FFC9A3"),
        Color.parseColor("#3D2314"), Color.parseColor("#8A5B3F"), Color.parseColor("#F26B1D"), 10,
    ),
    LEMON(
        "Kuning Lemon",
        Color.parseColor("#FFF6C7"), Color.parseColor("#FFFFFF"), Color.parseColor("#F5E38A"),
        Color.parseColor("#3A3410"), Color.parseColor("#7D7340"), Color.parseColor("#D9A400"), 10,
    ),
    SKY(
        "Biru Langit",
        Color.parseColor("#DCEEFF"), Color.parseColor("#FFFFFF"), Color.parseColor("#B5D8FA"),
        Color.parseColor("#13304D"), Color.parseColor("#4F6F8F"), Color.parseColor("#1E88E5"), 10,
    ),
    TOSCA(
        "Tosca",
        Color.parseColor("#0E3B3A"), Color.parseColor("#17605E"), Color.parseColor("#124B4A"),
        Color.WHITE, Color.parseColor("#A6D6D3"), Color.parseColor("#2EC4B6"), 10,
    ),
    MAROON(
        "Merah Marun",
        Color.parseColor("#3B0F17"), Color.parseColor("#6A1F2B"), Color.parseColor("#531824"),
        Color.WHITE, Color.parseColor("#E0AAB3"), Color.parseColor("#FF4D6D"), 10,
    ),
    FOREST(
        "Hijau Hutan",
        Color.parseColor("#13261A"), Color.parseColor("#24452F"), Color.parseColor("#1B3524"),
        Color.WHITE, Color.parseColor("#A9C9B2"), Color.parseColor("#4CAF50"), 10,
    ),
    COFFEE(
        "Kopi",
        Color.parseColor("#2B1E17"), Color.parseColor("#4A372B"), Color.parseColor("#3A2B21"),
        Color.parseColor("#F5E9DD"), Color.parseColor("#C4AE9A"), Color.parseColor("#D4A373"), 10,
    ),
    GALAXY(
        "Galaksi Ungu",
        Color.parseColor("#1A1033"), Color.parseColor("#2E2057"), Color.parseColor("#241847"),
        Color.WHITE, Color.parseColor("#B9A9E6"), Color.parseColor("#B14CFF"), 12,
    ),
    AMOLED(
        "Hitam Pekat",
        Color.BLACK, Color.parseColor("#1A1A1A"), Color.parseColor("#111111"),
        Color.WHITE, Color.parseColor("#8E8E8E"), Color.parseColor("#00E5FF"), 10,
    ),
    NAVY_GOLD(
        "Navy Emas",
        Color.parseColor("#0B1630"), Color.parseColor("#1A2A52"), Color.parseColor("#132144"),
        Color.parseColor("#F7E7B4"), Color.parseColor("#B8A878"), Color.parseColor("#E6B422"), 10,
    ),
}

/** Background colours the user can put behind the keys instead of the theme's own. */
val BACKGROUND_COLORS: List<Int> = listOf(
    "#000000", "#1C1C1E", "#3A3A3C", "#8E8E93", "#E5E5EA", "#FFFFFF",
    "#B71C1C", "#E53935", "#FF7043", "#FFB300", "#FDD835", "#C0CA33",
    "#43A047", "#00897B", "#00ACC1", "#1E88E5", "#3949AB", "#5E35B1",
    "#8E24AA", "#D81B60", "#F48FB1", "#A1887F", "#6D4C41", "#263238",
).map { Color.parseColor(it) }

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
    /** Extra space between the bottom row and the bottom of the screen in dp, above the navigation bar. */
    val bottomGap: Int = 8,
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
    /** Remember copied texts so they can be pasted again from the 📋 panel. */
    val clipboardHistory: Boolean = true,
    /** After translating, also show what the result means in Indonesian. */
    val showMeaning: Boolean = true,
    /** Remember the target language and style separately for each app (e.g. Japanese in WhatsApp, formal English in e-mail). */
    val perAppLanguage: Boolean = true,
    /** Raised keys with a darker edge and shadowed letters. */
    val keys3d: Boolean = true,
    /** Colour behind the keys instead of the theme's, or 0 for the theme's own. */
    val backgroundColor: Int = 0,
    /** Show the toolbar for a few seconds when the keyboard opens, then hide it behind the 🌐 button. */
    val autoHideToolbar: Boolean = true,
    /** The 💬 button that suggests replies to a copied message; off until the user turns it on. */
    val replySuggestions: Boolean = false,
    /** An animation where a key is pressed, besides the enlarged letter. */
    val keyEffect: KeyEffect = KeyEffect.NONE,
    // Toolbar buttons, each switchable so everyone keeps only what they use.
    val toolTranslateBox: Boolean = true,
    val toolRead: Boolean = true,
    val toolEmoji: Boolean = true,
    val toolClipboard: Boolean = true,
    val toolMic: Boolean = true,
    val toolCalculator: Boolean = true,
    val toolFonts: Boolean = false,
    val toolKaomoji: Boolean = false,
    val toolLines: Boolean = false,
    val toolTidy: Boolean = false,
    /** A photo from the gallery behind the keys, darkened by [backgroundDim] and blurred by [backgroundBlur] percent. */
    val backgroundImage: Boolean = false,
    val backgroundDim: Int = 30,
    val backgroundBlur: Int = 20,
    /** Changes whenever a new photo is picked, so the keyboard reloads it. */
    val backgroundStamp: Long = 0,
    /** Use [nightTheme] from 18:00 to 06:00 and [theme] during the day. */
    val autoTheme: Boolean = false,
    val nightTheme: KeyboardTheme = KeyboardTheme.SAMSUNG_DARK,
    val soundStyle: SoundStyle = SoundStyle.SYSTEM,
    /** Expand the user's own abbreviations, see [TextShortcuts]. */
    val shortcuts: Boolean = true,
) {
    /** The theme to show at [hour] (0–23). */
    fun themeAt(hour: Int): KeyboardTheme = if (autoTheme && (hour < 6 || hour >= 18)) nightTheme else theme

    companion object {
        const val MIN_HEIGHT = 70
        const val MAX_HEIGHT = 140
        const val MIN_TEXT = 14
        const val MAX_TEXT = 28
        const val MIN_GAP = 0
        const val MAX_GAP = 12
        const val MIN_BOTTOM_GAP = 0
        const val MAX_BOTTOM_GAP = 48
        const val MIN_LONG_PRESS = 200
        const val MAX_LONG_PRESS = 800

        /** The preferences file; the keyboard watches it to apply changes straight away. */
        const val PREFS = "settings"

        /** The photo picked for behind the keys. */
        const val BACKGROUND_FILE = "keyboard_background.jpg"

        fun load(context: Context): KeyboardSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val defaults = KeyboardSettings()
            return KeyboardSettings(
                theme = KeyboardTheme.entries.firstOrNull { it.name == p.getString("theme", null) } ?: defaults.theme,
                layout = KeyboardLayout.entries.firstOrNull { it.name == p.getString("layout", null) } ?: defaults.layout,
                heightPercent = p.getInt("height_percent", defaults.heightPercent).coerceIn(MIN_HEIGHT, MAX_HEIGHT),
                textSize = p.getInt("text_size", defaults.textSize).coerceIn(MIN_TEXT, MAX_TEXT),
                keyGap = p.getInt("key_gap", defaults.keyGap).coerceIn(MIN_GAP, MAX_GAP),
                bottomGap = p.getInt("bottom_gap", defaults.bottomGap).coerceIn(MIN_BOTTOM_GAP, MAX_BOTTOM_GAP),
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
                clipboardHistory = p.getBoolean("clipboard_history", defaults.clipboardHistory),
                showMeaning = p.getBoolean("show_meaning", defaults.showMeaning),
                perAppLanguage = p.getBoolean("per_app_language", defaults.perAppLanguage),
                keys3d = p.getBoolean("keys_3d", defaults.keys3d),
                autoHideToolbar = p.getBoolean("auto_hide_toolbar", defaults.autoHideToolbar),
                replySuggestions = p.getBoolean("reply_suggestions", defaults.replySuggestions),
                keyEffect = KeyEffect.entries.firstOrNull { it.name == p.getString("key_effect", null) } ?: defaults.keyEffect,
                toolTranslateBox = p.getBoolean("tool_translate_box", defaults.toolTranslateBox),
                toolRead = p.getBoolean("tool_read", defaults.toolRead),
                toolEmoji = p.getBoolean("tool_emoji", defaults.toolEmoji),
                toolClipboard = p.getBoolean("tool_clipboard", defaults.toolClipboard),
                toolMic = p.getBoolean("tool_mic", defaults.toolMic),
                toolCalculator = p.getBoolean("tool_calculator", defaults.toolCalculator),
                toolFonts = p.getBoolean("tool_fonts", defaults.toolFonts),
                toolKaomoji = p.getBoolean("tool_kaomoji", defaults.toolKaomoji),
                toolLines = p.getBoolean("tool_lines", defaults.toolLines),
                toolTidy = p.getBoolean("tool_tidy", defaults.toolTidy),
                backgroundImage = p.getBoolean("background_image", defaults.backgroundImage),
                backgroundDim = p.getInt("background_dim", defaults.backgroundDim).coerceIn(0, 90),
                backgroundBlur = p.getInt("background_blur", defaults.backgroundBlur).coerceIn(0, 100),
                backgroundStamp = p.getLong("background_stamp", defaults.backgroundStamp),
                autoTheme = p.getBoolean("auto_theme", defaults.autoTheme),
                nightTheme = KeyboardTheme.entries.firstOrNull { it.name == p.getString("night_theme", null) } ?: defaults.nightTheme,
                soundStyle = SoundStyle.entries.firstOrNull { it.name == p.getString("sound_style", null) } ?: defaults.soundStyle,
                shortcuts = p.getBoolean("shortcuts", defaults.shortcuts),
                backgroundColor = p.getInt("background_color", defaults.backgroundColor),
            )
        }

        fun save(context: Context, settings: KeyboardSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("theme", settings.theme.name)
                .putString("layout", settings.layout.name)
                .putInt("height_percent", settings.heightPercent)
                .putInt("text_size", settings.textSize)
                .putInt("key_gap", settings.keyGap)
                .putInt("bottom_gap", settings.bottomGap)
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
                .putBoolean("clipboard_history", settings.clipboardHistory)
                .putBoolean("show_meaning", settings.showMeaning)
                .putBoolean("per_app_language", settings.perAppLanguage)
                .putBoolean("keys_3d", settings.keys3d)
                .putBoolean("auto_hide_toolbar", settings.autoHideToolbar)
                .putBoolean("reply_suggestions", settings.replySuggestions)
                .putString("key_effect", settings.keyEffect.name)
                .putBoolean("tool_translate_box", settings.toolTranslateBox)
                .putBoolean("tool_read", settings.toolRead)
                .putBoolean("tool_emoji", settings.toolEmoji)
                .putBoolean("tool_clipboard", settings.toolClipboard)
                .putBoolean("tool_mic", settings.toolMic)
                .putBoolean("tool_calculator", settings.toolCalculator)
                .putBoolean("tool_fonts", settings.toolFonts)
                .putBoolean("tool_kaomoji", settings.toolKaomoji)
                .putBoolean("tool_lines", settings.toolLines)
                .putBoolean("tool_tidy", settings.toolTidy)
                .putBoolean("background_image", settings.backgroundImage)
                .putInt("background_dim", settings.backgroundDim)
                .putInt("background_blur", settings.backgroundBlur)
                .putLong("background_stamp", settings.backgroundStamp)
                .putBoolean("auto_theme", settings.autoTheme)
                .putString("night_theme", settings.nightTheme.name)
                .putString("sound_style", settings.soundStyle.name)
                .putBoolean("shortcuts", settings.shortcuts)
                .putInt("background_color", settings.backgroundColor)
                .apply()
        }
    }
}
