package com.zidny.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlin.concurrent.thread

/**
 * Setup and settings screen. Everything is grouped in cards; options that belong to a switch only show
 * while that switch is on, so the screen stays short for people who keep things simple.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: KeyboardSettings
    private val density get() = resources.displayMetrics.density

    // Colours of this screen, following the phone's light or dark mode.
    private val night get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val pageColor get() = if (night) 0xFF111216.toInt() else 0xFFF2F3F7.toInt()
    private val cardColor get() = if (night) 0xFF1D1F24.toInt() else Color.WHITE
    private val textColor get() = if (night) 0xFFF1F2F4.toInt() else 0xFF1B1C20.toInt()
    private val mutedColor get() = if (night) 0xFF9EA3AD.toInt() else 0xFF6A6F78.toInt()
    private val chipColor get() = if (night) 0xFF2A2D33.toInt() else 0xFFE9EBF0.toInt()
    private val accent get() = settings.theme.accent

    /** Setup step labels, refreshed when the user comes back from the system settings. */
    private lateinit var enableStep: TextView
    private lateinit var chooseStep: TextView

    /** The calendar switch, turned back off if the user refuses the permission. */
    private var calendarSwitch: SwitchCompat? = null
    private val askCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        update(settings.copy(calendarEvents = granted))
        if (!granted) calendarSwitch?.isChecked = false
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        thread(name = "save-background") {
            val saved = KeyboardBackground.save(this, uri)
            runOnUiThread {
                if (saved) {
                    update(settings.copy(backgroundImage = true, backgroundStamp = System.currentTimeMillis()))
                    toast(tr("Foto latar dipasang", "Background photo set"))
                } else {
                    toast(tr("Foto tidak bisa dibuka", "Couldn't open that photo"))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings.load(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(hero())
        root.addView(EditText(this).apply {
            hint = getString(R.string.try_here)
            setTextColor(textColor)
            setHintTextColor(mutedColor)
            background = rounded(cardColor, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }, matchWrap().apply { topMargin = dp(12) })
        root.addView(howTo())
        root.addView(languageCard())
        root.addView(lookCard())
        root.addView(typingCard())
        root.addView(toolbarCard())
        root.addView(translationCard())
        root.addView(shortcutsCard())
        root.addView(voiceCard())
        root.addView(TextView(this).apply {
            text = getString(R.string.credits)
            textSize = 11f
            setTextColor(mutedColor)
            setPadding(dp(4), dp(16), dp(4), 0)
        })

        // The settings scroll; a banner ad sits fixed at the bottom of the screen.
        val adContainer = LinearLayout(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageColor)
            // Android 15+ draws the screen behind the status and navigation bars; keep the content clear of them.
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            addView(ScrollView(this@MainActivity).apply { addView(root) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(adContainer, matchWrap())
        })
        loadBannerAfterConsent(adContainer)
        if (intent.getBooleanExtra(EXTRA_ASK_MICROPHONE, false)) askMicrophone()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupSteps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Picking the keyboard happens in a system dialog over this screen.
        if (hasFocus) refreshSetupSteps()
    }

    // ---- Top of the screen ----

    private fun hero(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(18))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(accent, ColorUtils.blendARGB(accent, Color.BLACK, 0.45f)),
        ).apply { cornerRadius = dp(22).toFloat() }
        addView(TextView(context).apply {
            text = "⌨️  ZidnyKeyboard"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        addView(TextView(context).apply {
            text = tr("Keyboard pintar: terjemahan AI, tema, efek, dan banyak lagi.", "A smart keyboard: AI translation, themes, effects and more.")
            textSize = 14f
            setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 220))
            setPadding(0, dp(4), 0, dp(14))
        })
        enableStep = heroButton { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        chooseStep = heroButton { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        addView(enableStep, matchWrap())
        addView(chooseStep, matchWrap().apply { topMargin = dp(8) })
        refreshSetupSteps()
    }

    private fun heroButton(onClick: () -> Unit) = TextView(this).apply {
        textSize = 15f
        setTextColor(Color.WHITE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(ColorUtils.setAlphaComponent(Color.WHITE, 45), 14)
        setOnClickListener { onClick() }
    }

    /** Ticks off the two setup steps once they are done. */
    private fun refreshSetupSteps() {
        if (!::enableStep.isInitialized) return
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val chosen = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
        enableStep.text = (if (enabled) "✅  " else "1️⃣  ") + getString(R.string.enable_keyboard)
        chooseStep.text = (if (chosen) "✅  " else "2️⃣  ") + getString(R.string.choose_keyboard)
    }

    private fun howTo(): View {
        val steps = TextView(this).apply {
            text = getString(R.string.setup_steps)
            textSize = 14f
            setTextColor(mutedColor)
            setLineSpacing(0f, 1.15f)
            visibility = View.GONE
        }
        return card("📖", tr("Cara pakai", "How to use"), collapsible = steps).apply { addView(steps) }
    }

    // ---- Sections ----

    /** True when this screen is showing in English, picked here or because the phone is set to English. */
    private val english get() = resources.configuration.locales[0].language == "en"

    private fun languageCard() = card("🌐", getString(R.string.app_language)).apply {
        addView(chips(listOf("id", "en"), if (english) "en" else "id", { if (it == "en") "🇬🇧 English" else "🇮🇩 Indonesia" }) {
            // AppCompat restarts this screen in the new language and remembers the choice.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
        })
    }

    private fun lookCard() = card("🎨", tr("Tampilan", "Look")).apply {
        addView(label(getString(R.string.theme)))
        addView(themePicker(settings.theme) { update(settings.copy(theme = it)) })

        val nightOptions = group(
            label(tr("Tema malam (18.00–06.00)", "Night theme (6 pm–6 am)")),
            themePicker(settings.nightTheme) { update(settings.copy(nightTheme = it)) },
        )
        addView(switch(tr("Tema otomatis siang dan malam", "Switch theme for day and night"), settings.autoTheme, nightOptions) {
            update(settings.copy(autoTheme = it))
        })
        addView(nightOptions)

        val photoOptions = group(
            button(tr("🖼️  Pilih foto dari galeri", "🖼️  Pick a photo from the gallery")) {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            slider(tr("Gelapkan foto", "Darken photo"), 0, 90, 5, settings.backgroundDim, "%") {
                update(settings.copy(backgroundDim = it))
            },
            slider(tr("Blur foto", "Blur photo"), 0, 100, 5, settings.backgroundBlur, "%") {
                update(settings.copy(backgroundBlur = it))
            },
        )
        addView(switch(tr("Foto sendiri sebagai latar keyboard", "Your own photo behind the keys"), settings.backgroundImage, photoOptions) {
            update(settings.copy(backgroundImage = it))
            if (it && !KeyboardBackground.file(this@MainActivity).exists()) {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        })
        addView(photoOptions)

        addView(label(getString(R.string.background_color)))
        addView(backgroundColors())
        addView(switch(getString(R.string.keys_3d), settings.keys3d) { update(settings.copy(keys3d = it)) })

        addView(label(getString(R.string.key_effect)))
        addView(chips(KeyEffect.entries, settings.keyEffect, { if (english) it.englishName else it.displayName }) {
            update(settings.copy(keyEffect = it))
        })

        addView(slider(getString(R.string.key_height), KeyboardSettings.MIN_HEIGHT, KeyboardSettings.MAX_HEIGHT, 5,
            settings.heightPercent, "%") { update(settings.copy(heightPercent = it)) })
        addView(slider(getString(R.string.text_size), KeyboardSettings.MIN_TEXT, KeyboardSettings.MAX_TEXT, 1,
            settings.textSize, "") { update(settings.copy(textSize = it)) })
        addView(slider(getString(R.string.key_gap), KeyboardSettings.MIN_GAP, KeyboardSettings.MAX_GAP, 1,
            settings.keyGap, " dp") { update(settings.copy(keyGap = it)) })
        addView(slider(getString(R.string.bottom_gap), KeyboardSettings.MIN_BOTTOM_GAP, KeyboardSettings.MAX_BOTTOM_GAP, 2,
            settings.bottomGap, " dp") { update(settings.copy(bottomGap = it)) })
    }

    private fun typingCard() = card("⌨️", tr("Mengetik", "Typing")).apply {
        addView(label(getString(R.string.layout)))
        addView(chips(KeyboardLayout.entries, settings.layout, { layoutName(it) }) { update(settings.copy(layout = it)) })
        addView(switch(getString(R.string.number_row), settings.numberRow) { update(settings.copy(numberRow = it)) })
        addView(switch(getString(R.string.auto_caps), settings.autoCaps) { update(settings.copy(autoCaps = it)) })
        addView(switch(getString(R.string.double_space_period), settings.doubleSpacePeriod) {
            update(settings.copy(doubleSpacePeriod = it))
        })
        addView(switch(getString(R.string.suggestions), settings.suggestions) { update(settings.copy(suggestions = it)) })
        addView(switch(getString(R.string.autocorrect), settings.autocorrect) { update(settings.copy(autocorrect = it)) })
        addView(switch(getString(R.string.key_preview), settings.keyPreview) { update(settings.copy(keyPreview = it)) })
        addView(switch(getString(R.string.vibrate), settings.vibrate) { update(settings.copy(vibrate = it)) })

        val soundOptions = group(
            label(tr("Jenis suara", "Sound")),
            chips(SoundStyle.entries, settings.soundStyle, { if (english) it.englishName else it.displayName }) {
                update(settings.copy(soundStyle = it))
            },
        )
        addView(switch(getString(R.string.sound), settings.sound, soundOptions) { update(settings.copy(sound = it)) })
        addView(soundOptions)
        addView(slider(getString(R.string.long_press), KeyboardSettings.MIN_LONG_PRESS, KeyboardSettings.MAX_LONG_PRESS, 50,
            settings.longPressMs, " ms") { update(settings.copy(longPressMs = it)) })
    }

    private fun toolbarCard() = card("🧰", tr("Tombol di bar atas", "Toolbar buttons")).apply {
        addView(hint(tr("Nyalakan yang kamu pakai saja, sisanya disembunyikan.", "Turn on only what you use; the rest stays hidden.")))
        addView(switch(getString(R.string.auto_hide_toolbar), settings.autoHideToolbar) {
            update(settings.copy(autoHideToolbar = it))
        })
        addView(switch(tr("文A  Kotak terjemahan (seperti Google Translate)", "文A  Translate box (like Google Translate)"), settings.toolTranslateBox) {
            update(settings.copy(toolTranslateBox = it))
        })
        addView(switch(tr("🔍  Cek arti pesan yang disalin", "🔍  Meaning of a copied message"), settings.toolRead) {
            update(settings.copy(toolRead = it))
        })
        addView(switch(getString(R.string.reply_suggestions), settings.replySuggestions) {
            update(settings.copy(replySuggestions = it))
        })
        addView(switch(tr("🪄  Rapikan tulisan (km dmn → Kamu di mana?)", "🪄  Tidy up text (u there → Are you there?)"), settings.toolTidy) {
            update(settings.copy(toolTidy = it))
        })
        addView(switch(tr("🎭  Kata-kata AI: gombal, lucu, roasting, motivasi", "🎭  AI lines: flirty, funny, roast, motivation"), settings.toolLines) {
            update(settings.copy(toolLines = it))
        })
        addView(switch(tr("😊  Emoji", "😊  Emoji"), settings.toolEmoji) { update(settings.copy(toolEmoji = it)) })
        addView(switch(tr("ツ  Kaomoji (◕‿◕)", "ツ  Kaomoji (◕‿◕)"), settings.toolKaomoji) { update(settings.copy(toolKaomoji = it)) })
        addView(switch(tr("𝓐  Font keren (𝐁𝐨𝐥𝐝, 𝓢𝓪𝓶𝓫𝓾𝓷𝓰, ⓑⓤⓑⓑⓛⓔ)", "𝓐  Fancy fonts (𝐁𝐨𝐥𝐝, 𝓢𝓬𝓻𝓲𝓹𝓽, ⓑⓤⓑⓑⓛⓔ)"), settings.toolFonts) {
            update(settings.copy(toolFonts = it))
        })
        addView(switch(tr("📋  Riwayat salinan", "📋  Clipboard"), settings.toolClipboard) { update(settings.copy(toolClipboard = it)) })
        addView(switch(tr("🎤  Ketik pakai suara", "🎤  Voice typing"), settings.toolMic) { update(settings.copy(toolMic = it)) })
        val calendarEventsSwitch = switch(tr("Tampilkan jadwal & jam kosong dari kalender HP", "Show events & free time from the phone's calendar"),
            settings.calendarEvents && DeviceCalendar.allowed(this@MainActivity)) { on ->
            if (on && !DeviceCalendar.allowed(this@MainActivity)) askCalendar.launch(Manifest.permission.READ_CALENDAR)
            else update(settings.copy(calendarEvents = on))
        }
        calendarSwitch = calendarEventsSwitch
        val calendarOptions = group(calendarEventsSwitch)
        val apps = group(
            hint(tr("Satu tombol berisi aplikasi kecil, biar bar atas tetap rapi.", "One button holding small apps, so the toolbar stays tidy.")),
            switch(tr("🧮  Kalkulator", "🧮  Calculator"), settings.appCalculator) { update(settings.copy(appCalculator = it)) },
            switch(tr("💱  Kurs & konversi satuan", "💱  Currency & unit converter"), settings.appConverter) {
                update(settings.copy(appConverter = it))
            },
            switch(tr("🕐  Zona waktu dunia", "🕐  World clock"), settings.appClock) { update(settings.copy(appClock = it)) },
            switch(tr("📅  Kalender", "📅  Calendar"), settings.appCalendar, calendarOptions) { update(settings.copy(appCalendar = it)) },
            calendarOptions,
        )
        addView(switch(tr("🧰  Tools", "🧰  Tools"), settings.toolApps, apps) { update(settings.copy(toolApps = it)) })
        addView(apps)
    }

    private fun translationCard() = card("🤖", tr("Terjemahan", "Translation")).apply {
        addView(switch(getString(R.string.clipboard_translate), settings.clipboardTranslate) {
            update(settings.copy(clipboardTranslate = it))
        })
        addView(switch(getString(R.string.translate_preview), settings.translatePreview) {
            update(settings.copy(translatePreview = it))
        })
        addView(switch(getString(R.string.show_meaning), settings.showMeaning) { update(settings.copy(showMeaning = it)) })
        addView(switch(getString(R.string.per_app_language), settings.perAppLanguage) {
            update(settings.copy(perAppLanguage = it))
        })
        addView(switch(getString(R.string.clipboard_history), settings.clipboardHistory) {
            update(settings.copy(clipboardHistory = it))
        })
    }

    /** The user's abbreviations: a list with delete buttons and a form to add one. */
    private fun shortcutsCard() = card("⚡", tr("Singkatan", "Text shortcuts")).apply {
        val prefs = getSharedPreferences(TextShortcuts.PREFS, MODE_PRIVATE)
        var shortcuts = TextShortcuts.decode(prefs.getString(TextShortcuts.KEY, null))
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun save(new: TextShortcuts) {
            shortcuts = new
            prefs.edit().putString(TextShortcuts.KEY, new.encode()).apply()
        }
        fun showList() {
            list.removeAllViews()
            if (shortcuts.entries.isEmpty()) list.addView(hint(tr("Belum ada singkatan.", "No shortcuts yet.")))
            shortcuts.entries.forEach { (short, long) ->
                list.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(chipColor, 12)
                    setPadding(dp(12), dp(8), dp(4), dp(8))
                    addView(TextView(context).apply {
                        text = "$short  →  $long"
                        setTextColor(textColor)
                        textSize = 14f
                        maxLines = 3
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        text = "✕"
                        textSize = 18f
                        setTextColor(mutedColor)
                        setPadding(dp(12), dp(4), dp(12), dp(4))
                        setOnClickListener {
                            save(shortcuts.without(short))
                            showList()
                        }
                    })
                }, matchWrap().apply { topMargin = dp(6) })
            }
        }
        val shortField = field(tr("Singkatan, misal: ttd", "Shortcut, e.g. addr"), multiLine = false)
        val longField = field(tr("Jadi teks lengkap, misal: Jl. Melati No. 5, Bandung", "Becomes, e.g. 5 Melati Street, Bandung"), multiLine = true)
        val options = group(
            hint(tr("Ketik singkatannya lalu spasi, otomatis jadi teks lengkap.", "Type the shortcut and a space to get the full text.")),
            list, shortField, longField,
            button(tr("➕  Tambah singkatan", "➕  Add shortcut")) {
                val short = shortField.text.toString().trim()
                val long = longField.text.toString()
                when {
                    short.isEmpty() || short.any { it.isWhitespace() } ->
                        toast(tr("Singkatan harus satu kata", "A shortcut must be one word"))
                    long.isBlank() -> toast(tr("Isi teks lengkapnya dulu", "Fill in the full text first"))
                    else -> {
                        save(shortcuts.with(short, long))
                        shortField.text.clear()
                        longField.text.clear()
                        showList()
                    }
                }
            },
        )
        showList()
        addView(switch(tr("Pakai singkatan", "Use shortcuts"), settings.shortcuts, options) { update(settings.copy(shortcuts = it)) })
        addView(options)
    }

    private fun voiceCard() = card("🎤", getString(R.string.voice_title)).apply {
        addView(hint(getString(R.string.voice_hint)))
        addView(button(getString(R.string.allow_microphone)) { askMicrophone() })
    }

    // ---- Building blocks ----

    /**
     * A rounded card with an icon and title. With [collapsible], tapping the title shows or hides that view.
     */
    private fun card(icon: String, title: String, collapsible: View? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(cardColor, 18)
        elevation = dp(1).toFloat()
        layoutParams = matchWrap().apply { topMargin = dp(14) }
        addView(TextView(context).apply {
            text = if (collapsible != null) "$icon  $title  ▾" else "$icon  $title"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            setPadding(0, 0, 0, dp(4))
            if (collapsible != null) setOnClickListener {
                collapsible.visibility = if (collapsible.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                text = "$icon  $title  " + if (collapsible.visibility == View.VISIBLE) "▴" else "▾"
            }
        })
    }

    /** Views that belong to one switch, shown only while it is on. */
    private fun group(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), 0, 0, dp(4))
        views.forEach { addView(it) }
    }

    private fun switch(text: String, value: Boolean, dependent: View? = null, onChange: (Boolean) -> Unit) = SwitchCompat(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(textColor)
        isChecked = value
        setPadding(0, dp(8), 0, dp(8))
        dependent?.visibility = if (value) View.VISIBLE else View.GONE
        setOnCheckedChangeListener { _, on ->
            dependent?.visibility = if (on) View.VISIBLE else View.GONE
            onChange(on)
        }
    }

    /** A row of chips that scrolls sideways; the picked one is filled with the accent colour. */
    private fun <T> chips(entries: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val views = mutableListOf<Pair<T, TextView>>()
        fun mark(picked: T) = views.forEach { (entry, view) ->
            val on = entry == picked
            view.background = rounded(if (on) accent else chipColor, 20)
            view.setTextColor(if (on) Color.WHITE else textColor)
        }
        entries.forEach { entry ->
            val chip = TextView(this).apply {
                text = name(entry)
                textSize = 14f
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener {
                    mark(entry)
                    onPick(entry)
                }
            }
            views.add(entry to chip)
            row.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(6), dp(8), dp(6))
            })
        }
        mark(selected)
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    /** Small previews of every theme: its background with three keys and the accent colour. */
    private fun themePicker(selected: KeyboardTheme, onPick: (KeyboardTheme) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cards = mutableListOf<Pair<KeyboardTheme, LinearLayout>>()
        fun mark(picked: KeyboardTheme) = cards.forEach { (theme, view) ->
            (view.background as GradientDrawable).setStroke(if (theme == picked) dp(3) else dp(1),
                if (theme == picked) accent else ColorUtils.setAlphaComponent(mutedColor, 80))
        }
        KeyboardTheme.entries.forEach { theme ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(10), dp(8), dp(8))
                background = rounded(theme.background, 14)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    listOf("Q", "W", "E").forEach { letter ->
                        addView(TextView(context).apply {
                            text = letter
                            gravity = Gravity.CENTER
                            textSize = 11f
                            setTextColor(theme.text)
                            background = GradientDrawable().apply {
                                cornerRadius = dp(theme.cornerRadiusDp / 2).toFloat()
                                setColor(theme.key)
                            }
                        }, LinearLayout.LayoutParams(dp(22), dp(26)).apply { setMargins(dp(2), 0, dp(2), 0) })
                    }
                })
                addView(View(context).apply {
                    background = rounded(theme.accent, 4)
                }, LinearLayout.LayoutParams(dp(54), dp(8)).apply { topMargin = dp(6) })
                addView(TextView(context).apply {
                    text = themeName(theme)
                    textSize = 11f
                    setTextColor(theme.text)
                    maxLines = 1
                    setPadding(0, dp(6), 0, 0)
                })
                setOnClickListener {
                    mark(theme)
                    onPick(theme)
                }
            }
            cards.add(theme to card)
            row.addView(card, LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(6), dp(10), dp(6))
            })
        }
        mark(selected)
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    /** A row of colour circles for the keyboard background, starting with the theme's own colour. */
    private fun backgroundColors(): View {
        val size = dp(38)
        val gap = dp(6)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val swatches = mutableListOf<Pair<Int, TextView>>()
        fun mark() = swatches.forEach { (color, view) ->
            (view.background as GradientDrawable).setStroke(if (color == settings.backgroundColor) gap / 2 else 1, accent)
            view.text = if (color == settings.backgroundColor) "✓" else ""
        }
        (listOf(0) + BACKGROUND_COLORS).forEach { color ->
            val fill = if (color == 0) settings.theme.background else color
            val swatch = TextView(this).apply {
                gravity = Gravity.CENTER
                setTextColor(if (ColorUtils.calculateLuminance(fill) > 0.5) Color.BLACK else Color.WHITE)
                contentDescription = if (color == 0) getString(R.string.background_default) else "#%06X".format(color and 0xFFFFFF)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(fill)
                }
                setOnClickListener {
                    update(settings.copy(backgroundColor = color))
                    mark()
                }
            }
            swatches.add(color to swatch)
            row.addView(swatch, LinearLayout.LayoutParams(size, size).apply { setMargins(0, gap, gap, gap) })
        }
        mark()
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    /** A slider from [min] to [max] in steps of [step], showing its value next to the label. */
    private fun slider(name: String, min: Int, max: Int, step: Int, value: Int, unit: String, onChange: (Int) -> Unit): View {
        val title = label("")
        fun show(v: Int) { title.text = "$name: $v$unit" }
        show(value)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(SeekBar(context).apply {
                this.max = (max - min) / step
                progress = (value - min) / step
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        val v = min + progress * step
                        show(v)
                        if (fromUser) onChange(v)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) {}
                })
            })
        }
    }

    private fun button(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 15f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = rounded(accent, 14)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = matchWrap().apply { setMargins(0, dp(8), 0, dp(4)) }
        setOnClickListener { onClick() }
    }

    private fun field(hint: String, multiLine: Boolean) = EditText(this).apply {
        this.hint = hint
        textSize = 14f
        setTextColor(textColor)
        setHintTextColor(mutedColor)
        background = rounded(chipColor, 12)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        inputType = if (multiLine) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        layoutParams = matchWrap().apply { topMargin = dp(8) }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        setPadding(0, dp(12), 0, 0)
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(mutedColor)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int) = (value * density).toInt()

    private fun tr(indonesian: String, englishText: String) = if (english) englishText else indonesian

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun themeName(theme: KeyboardTheme) = if (english) THEME_NAMES_EN[theme] ?: theme.displayName else theme.displayName

    private fun layoutName(layout: KeyboardLayout) = if (!english) layout.displayName else when (layout) {
        KeyboardLayout.AZERTY -> "AZERTY (French)"
        KeyboardLayout.QWERTZ -> "QWERTZ (German)"
        KeyboardLayout.DESKTOP -> "Desktop (like a PC keyboard)"
        else -> layout.displayName
    }

    private fun update(newSettings: KeyboardSettings) {
        settings = newSettings
        KeyboardSettings.save(this, newSettings)
    }

    // ---- Ads and permissions ----

    /** Asks for ad consent where the law requires it (e.g. EU), then shows a banner if allowed. */
    private fun loadBannerAfterConsent(container: LinearLayout) {
        val consent = UserMessagingPlatform.getConsentInformation(this)
        val showBanner = {
            if (consent.canRequestAds()) {
                MobileAds.initialize(this) {}
                val width = (resources.displayMetrics.widthPixels / density).toInt()
                val banner = AdView(this).apply {
                    adUnitId = BuildConfig.ADMOB_BANNER_ID
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, width))
                }
                container.removeAllViews()
                container.addView(banner)
                banner.loadAd(AdRequest.Builder().build())
            }
        }
        consent.requestConsentInfoUpdate(
            this,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { showBanner() }
            },
            { showBanner() },
        )
    }

    private fun askMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    companion object {
        const val EXTRA_ASK_MICROPHONE = "ask_microphone"

        private val THEME_NAMES_EN = mapOf(
            KeyboardTheme.SAMSUNG_LIGHT to "Samsung Light",
            KeyboardTheme.SAMSUNG_DARK to "Samsung Dark",
            KeyboardTheme.CLASSIC_DARK to "Classic Dark",
            KeyboardTheme.OCEAN to "Ocean Blue",
            KeyboardTheme.MINT to "Mint Green",
            KeyboardTheme.LAVENDER to "Lavender",
            KeyboardTheme.SUNSET to "Orange Sunset",
            KeyboardTheme.LEMON to "Lemon Yellow",
            KeyboardTheme.SKY to "Sky Blue",
            KeyboardTheme.MAROON to "Maroon",
            KeyboardTheme.FOREST to "Forest Green",
            KeyboardTheme.COFFEE to "Coffee",
            KeyboardTheme.GALAXY to "Purple Galaxy",
            KeyboardTheme.AMOLED to "Pitch Black",
            KeyboardTheme.NAVY_GOLD to "Navy Gold",
        )
    }
}
