package com.zidny.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Setup screen: guides the user to enable and select the keyboard. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()

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

        setContentView(root)
    }
}
