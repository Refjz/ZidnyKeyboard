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

/** Draws the keys and reports presses to [listener]. Holds no text state itself. */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
    }

    enum class ShiftState { OFF, ON, LOCKED }

    private var showSymbols = false
    private var lastShiftTap = 0L
    private val repeatHandler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

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

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1E1F22"))
        val pad = dp(4)
        setPadding(pad, pad, pad, pad)
        render()
    }

    private fun render() {
        removeAllViews()
        val layout = if (showSymbols) KeyboardLayouts.symbols else KeyboardLayouts.letters
        for (keys in layout) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (key in keys) row.addView(createKey(key))
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKey(key: Key): TextView {
        val special = key !is Key.Char || key.value in listOf(",", ".")
        val view = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = if (key is Key.Char) 20f else 15f
            text = labelFor(key)
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor(if (special) "#3A3C41" else "#4A4D53"))
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
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        listener.onDelete()
                        repeatHandler.postDelayed(repeat, 400)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> repeatHandler.removeCallbacks(repeat)
                }
                true
            }
        } else {
            view.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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

    private fun dp(value: Int) = (value * density).toInt()
}
