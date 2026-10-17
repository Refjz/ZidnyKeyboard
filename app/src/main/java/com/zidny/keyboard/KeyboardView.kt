package com.zidny.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Draws the keys and reports presses to [listener]. Holds no text state itself. */
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val listener: Listener,
    private val settings: KeyboardSettings,
) : LinearLayout(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onTranslate()
        fun onCycleLanguage()
    }

    enum class ShiftState { OFF, ON, LOCKED }

    private var showSymbols = false
    private var lastShiftTap = 0L
    private val repeatHandler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val theme = settings.theme

    var shiftState = ShiftState.OFF
        set(value) {
            field = value
            render()
        }

    /** Label for the enter key, e.g. "Kirim" when the app wants a send action. */
    var enterLabel = "⏎"
        set(value) {
            field = value
            render()
        }

    private val languageButton = toolbarButton { listener.onCycleLanguage() }
    private val translateButton = toolbarButton { listener.onTranslate() }.apply {
        text = "Terjemahkan"
        setTextColor(Color.WHITE)
        (background as GradientDrawable).setColor(theme.accent)
    }
    private val statusText = TextView(context).apply {
        setTextColor(theme.secondaryText)
        textSize = 13f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val toolbar = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(languageButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(statusText, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(translateButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = dp(4)
        setPadding(pad, pad, pad, pad)
        // Android 15+ draws the keyboard behind the navigation bar, so lift the keys above it.
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(pad, pad, pad, pad + bottom)
            insets
        }
        render()
    }

    /** Shows the target language on the language button, e.g. "→ EN". */
    fun setTargetLanguage(language: Language) {
        languageButton.text = "→ ${language.label}"
    }

    fun setStatus(message: String) {
        statusText.text = message
    }

    fun setTranslating(busy: Boolean) {
        translateButton.isEnabled = !busy
        translateButton.alpha = if (busy) 0.5f else 1f
    }

    private fun toolbarButton(onClick: () -> Unit) = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        textSize = 15f
        setPadding(dp(14), 0, dp(14), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(theme.cornerRadiusDp).toFloat()
            setColor(theme.specialKey)
        }
        setOnClickListener {
            tapFeedback(it)
            onClick()
        }
    }

    private fun LayoutParams.withMargins() = apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    private fun render() {
        removeAllViews()
        addView(toolbar, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        val layout = if (showSymbols) KeyboardLayouts.symbols else KeyboardLayouts.letters(settings.numberRow)
        val rowHeight = dp(52 * settings.heightPercent / 100)
        for (keys in layout) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (key in keys) row.addView(createKey(key))
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKey(key: Key): TextView {
        val special = key !is Key.Char || key.value in listOf(",", ".")
        val view = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            textSize = if (key is Key.Char) settings.textSize.toFloat() else settings.textSize * 0.75f
            text = labelFor(key)
            background = GradientDrawable().apply {
                cornerRadius = dp(theme.cornerRadiusDp).toFloat()
                setColor(if (special) theme.specialKey else theme.key)
            }
        }
        val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        view.layoutParams = lp

        if (key is Key.Delete) {
            // Hold to keep deleting.
            val repeat = object : Runnable {
                override fun run() {
                    listener.onDelete()
                    repeatHandler.postDelayed(this, 60)
                }
            }
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        tapFeedback(v)
                        listener.onDelete()
                        repeatHandler.postDelayed(repeat, 400)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> repeatHandler.removeCallbacks(repeat)
                }
                true
            }
        } else {
            view.setOnClickListener {
                tapFeedback(it)
                onKey(key)
            }
        }
        return view
    }

    private fun labelFor(key: Key): String = when (key) {
        is Key.Char -> if (shiftState != ShiftState.OFF && !showSymbols) key.value.uppercase() else key.value
        Key.Shift -> when (shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.ON -> "⬆"
            ShiftState.LOCKED -> "⇪"
        }
        Key.Delete -> "⌫"
        Key.Symbols -> "?123"
        Key.Letters -> "ABC"
        Key.Space -> "spasi"
        Key.Enter -> enterLabel
    }

    private fun onKey(key: Key) {
        when (key) {
            is Key.Char -> {
                val upper = shiftState != ShiftState.OFF && !showSymbols
                listener.onText(if (upper) key.value.uppercase() else key.value)
                if (shiftState == ShiftState.ON) shiftState = ShiftState.OFF
            }
            Key.Shift -> {
                val now = System.currentTimeMillis()
                shiftState = when {
                    shiftState == ShiftState.OFF -> ShiftState.ON
                    shiftState == ShiftState.ON && now - lastShiftTap < 400 -> ShiftState.LOCKED
                    else -> ShiftState.OFF
                }
                lastShiftTap = now
            }
            Key.Delete -> listener.onDelete()
            Key.Symbols -> { showSymbols = true; render() }
            Key.Letters -> { showSymbols = false; render() }
            Key.Space -> listener.onText(" ")
            Key.Enter -> listener.onEnter()
        }
    }

    private fun tapFeedback(view: android.view.View) {
        if (settings.vibrate) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
