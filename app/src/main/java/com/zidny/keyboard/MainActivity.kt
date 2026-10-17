package com.zidny.keyboard

import android.content.Intent
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
        addDownloadButton(root)

        setContentView(ScrollView(this).apply { addView(root) })
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

        addSlider(root, R.string.key_height, KeyboardSettings.MIN_HEIGHT, KeyboardSettings.MAX_HEIGHT, 5,
            settings.heightPercent, "%") { update(settings.copy(heightPercent = it)) }
        addSlider(root, R.string.text_size, KeyboardSettings.MIN_TEXT, KeyboardSettings.MAX_TEXT, 1,
            settings.textSize, "") { update(settings.copy(textSize = it)) }

        root.addView(SwitchCompat(this).apply {
            text = getString(R.string.vibrate)
            isChecked = settings.vibrate
            setOnCheckedChangeListener { _, on -> update(settings.copy(vibrate = on)) }
        })
        root.addView(SwitchCompat(this).apply {
            text = getString(R.string.number_row)
            isChecked = settings.numberRow
            setOnCheckedChangeListener { _, on -> update(settings.copy(numberRow = on)) }
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

    private fun addDownloadButton(root: LinearLayout) {
        root.addView(heading(getString(R.string.languages_title)))
        val downloadStatus = TextView(this).apply { setPadding(0, pad / 2, 0, 0) }
        root.addView(Button(this).apply {
            text = getString(R.string.download_languages)
            setOnClickListener { button ->
                button.isEnabled = false
                downloadStatus.text = getString(R.string.downloading)
                TextTranslator.downloadAll { ok ->
                    button.isEnabled = true
                    downloadStatus.text = getString(if (ok) R.string.download_done else R.string.download_failed)
                }
            }
        })
        root.addView(downloadStatus)
    }

    private fun update(newSettings: KeyboardSettings) {
        settings = newSettings
        KeyboardSettings.save(this, newSettings)
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
