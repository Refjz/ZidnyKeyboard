package com.zidny.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import androidx.appcompat.widget.SwitchCompat

/** Setup and settings screen: enable the keyboard, pick a theme and size, download language packs. */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: KeyboardSettings
    private var pad = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KeyboardSettings.load(this)
        pad = (24 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.setup_title)
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.setup_steps)
            textSize = 16f
            setPadding(0, pad / 2, 0, pad / 2)
        })
        root.addView(Button(this).apply {
            text = getString(R.string.enable_keyboard)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        root.addView(Button(this).apply {
            text = getString(R.string.choose_keyboard)
            setOnClickListener {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        })
        root.addView(EditText(this).apply { hint = getString(R.string.try_here) })

        addSettings(root)
        addMicrophoneButton(root)
        root.addView(TextView(this).apply {
            text = getString(R.string.credits)
            textSize = 12f
            setPadding(0, pad, 0, 0)
        })

        // The settings scroll; a banner ad sits fixed at the bottom of the screen.
        val adContainer = LinearLayout(this)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(this@MainActivity).apply { addView(root) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(adContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
        loadBannerAfterConsent(adContainer)
        if (intent.getBooleanExtra(EXTRA_ASK_MICROPHONE, false)) askMicrophone()
    }

    /** Asks for ad consent where the law requires it (e.g. EU), then shows a banner if allowed. */
    private fun loadBannerAfterConsent(container: LinearLayout) {
        val consent = UserMessagingPlatform.getConsentInformation(this)
        val showBanner = {
            if (consent.canRequestAds()) {
                MobileAds.initialize(this) {}
                val width = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
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

    private fun addMicrophoneButton(root: LinearLayout) {
        root.addView(heading(getString(R.string.voice_title)))
        root.addView(label(getString(R.string.voice_hint)))
        root.addView(Button(this).apply {
            text = getString(R.string.allow_microphone)
            setOnClickListener { askMicrophone() }
        })
    }

    private fun addSettings(root: LinearLayout) {
        root.addView(heading(getString(R.string.settings_title)))

        root.addView(label(getString(R.string.theme)))
        val themes = RadioGroup(this)
        KeyboardTheme.entries.forEachIndexed { index, theme ->
            themes.addView(RadioButton(this).apply {
                id = index + 1
                text = theme.displayName
                isChecked = theme == settings.theme
            })
        }
        themes.setOnCheckedChangeListener { _, checkedId ->
            update(settings.copy(theme = KeyboardTheme.entries[checkedId - 1]))
        }
        root.addView(themes)

        root.addView(label(getString(R.string.layout)))
        val layouts = RadioGroup(this)
        KeyboardLayout.entries.forEachIndexed { index, layout ->
            layouts.addView(RadioButton(this).apply {
                id = index + 1
                text = layout.displayName
                isChecked = layout == settings.layout
            })
        }
        layouts.setOnCheckedChangeListener { _, checkedId ->
            update(settings.copy(layout = KeyboardLayout.entries[checkedId - 1]))
        }
        root.addView(layouts)

        addSlider(root, R.string.key_height, KeyboardSettings.MIN_HEIGHT, KeyboardSettings.MAX_HEIGHT, 5,
            settings.heightPercent, "%") { update(settings.copy(heightPercent = it)) }
        addSlider(root, R.string.text_size, KeyboardSettings.MIN_TEXT, KeyboardSettings.MAX_TEXT, 1,
            settings.textSize, "") { update(settings.copy(textSize = it)) }
        addSlider(root, R.string.key_gap, KeyboardSettings.MIN_GAP, KeyboardSettings.MAX_GAP, 1,
            settings.keyGap, " dp") { update(settings.copy(keyGap = it)) }
        addSlider(root, R.string.bottom_gap, KeyboardSettings.MIN_BOTTOM_GAP, KeyboardSettings.MAX_BOTTOM_GAP, 2,
            settings.bottomGap, " dp") { update(settings.copy(bottomGap = it)) }
        addSlider(root, R.string.long_press, KeyboardSettings.MIN_LONG_PRESS, KeyboardSettings.MAX_LONG_PRESS, 50,
            settings.longPressMs, " ms") { update(settings.copy(longPressMs = it)) }

        addSwitch(root, R.string.vibrate, settings.vibrate) { update(settings.copy(vibrate = it)) }
        addSwitch(root, R.string.sound, settings.sound) { update(settings.copy(sound = it)) }
        addSwitch(root, R.string.key_preview, settings.keyPreview) { update(settings.copy(keyPreview = it)) }
        addSwitch(root, R.string.number_row, settings.numberRow) { update(settings.copy(numberRow = it)) }
        addSwitch(root, R.string.auto_caps, settings.autoCaps) { update(settings.copy(autoCaps = it)) }
        addSwitch(root, R.string.double_space_period, settings.doubleSpacePeriod) {
            update(settings.copy(doubleSpacePeriod = it))
        }
        addSwitch(root, R.string.suggestions, settings.suggestions) { update(settings.copy(suggestions = it)) }
        addSwitch(root, R.string.autocorrect, settings.autocorrect) { update(settings.copy(autocorrect = it)) }
        addSwitch(root, R.string.clipboard_history, settings.clipboardHistory) {
            update(settings.copy(clipboardHistory = it))
        }
        addSwitch(root, R.string.clipboard_translate, settings.clipboardTranslate) {
            update(settings.copy(clipboardTranslate = it))
        }
        addSwitch(root, R.string.translate_preview, settings.translatePreview) {
            update(settings.copy(translatePreview = it))
        }
    }

    private fun addSwitch(root: LinearLayout, labelRes: Int, value: Boolean, onChange: (Boolean) -> Unit) {
        root.addView(SwitchCompat(this).apply {
            text = getString(labelRes)
            isChecked = value
            setPadding(0, pad / 4, 0, pad / 4)
            setOnCheckedChangeListener { _, on -> onChange(on) }
        })
    }

    /** A slider from [min] to [max] in steps of [step], showing its value next to the label. */
    private fun addSlider(
        root: LinearLayout, labelRes: Int, min: Int, max: Int, step: Int, value: Int, unit: String,
        onChange: (Int) -> Unit,
    ) {
        val title = label("")
        fun show(v: Int) { title.text = "${getString(labelRes)}: $v$unit" }
        show(value)
        root.addView(title)
        root.addView(SeekBar(this).apply {
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


    private fun update(newSettings: KeyboardSettings) {
        settings = newSettings
        KeyboardSettings.save(this, newSettings)
    }

    companion object {
        const val EXTRA_ASK_MICROPHONE = "ask_microphone"
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setPadding(0, pad, 0, pad / 3)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, pad / 2, 0, 0)
    }
}
