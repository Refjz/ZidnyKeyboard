package com.zidny.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

/** Draws the keys and reports presses to [listener]. Holds no text state itself. */
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val listener: Listener,
    private val settings: KeyboardSettings,
) : FrameLayout(context) {

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onKeyCode(keyCode: Int)
        fun onTranslate()
        fun onCycleLanguage()
    }

    enum class ShiftState { OFF, ON, LOCKED }

    private var showSymbols = false
    private var lastShiftTap = 0L
    private val keyHandler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val theme = settings.theme
    private val audio = context.getSystemService(AudioManager::class.java)

    var shiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            render()
        }

    /** Label for the enter key, e.g. "Kirim" when the app wants a send action. */
    var enterLabel = "⏎"
        set(value) {
            if (field == value) return
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
        orientation = LinearLayout.HORIZONTAL
        addView(languageButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(statusText, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(translateButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
    }

    /** The toolbar and key rows. */
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /** Drawn above the keys: the enlarged letter while pressing, and the row of extra characters while holding. */
    private val overlay = FrameLayout(context)
    private val preview = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        textSize = settings.textSize * 1.4f
        background = rounded(theme.key)
        elevation = dp(6).toFloat()
        visibility = GONE
    }
    private val alternatesRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        background = rounded(theme.key)
        elevation = dp(8).toFloat()
        visibility = GONE
    }

    // State of the key currently held down.
    private var alternates: List<String> = emptyList()
    private var selectedAlternate = 0
    private var spaceLastX = 0f
    private var spaceMoved = false

    init {
        setBackgroundColor(theme.background)
        val pad = dp(4)
        setPadding(pad, pad, pad, pad)
        // Android 15+ draws the keyboard behind the navigation bar, so lift the keys above it.
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(pad, pad, pad, pad + bottom)
            insets
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        overlay.addView(preview, LayoutParams(0, 0))
        overlay.addView(alternatesRow, LayoutParams(LayoutParams.WRAP_CONTENT, 0))
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
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
        background = rounded(theme.specialKey)
        setOnClickListener {
            tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
            onClick()
        }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(theme.cornerRadiusDp).toFloat()
        setColor(color)
    }

    private fun LinearLayout.LayoutParams.withMargins() = apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }

    private fun render() {
        content.removeAllViews()
        content.addView(toolbar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        val layout = if (showSymbols) KeyboardLayouts.symbols else KeyboardLayouts.letters(settings.layout, settings.numberRow)
        val rowHeight = dp(52 * settings.heightPercent / 100)
        for (keys in layout) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (key in keys) row.addView(createKey(key))
            content.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
        }
    }

    private fun isSpecial(key: Key) = key !is Key.Char || key.value in listOf(",", ".")

    @SuppressLint("ClickableViewAccessibility")
    private fun createKey(key: Key): TextView {
        val color = when {
            key == Key.CapsLock && shiftState == ShiftState.LOCKED -> theme.accent
            isSpecial(key) -> theme.specialKey
            else -> theme.key
        }
        val drawable = rounded(color)
        val pressedColor = ColorUtils.blendARGB(color, theme.text, 0.18f)
        val view = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            val label = labelFor(key)
            textSize = if (key is Key.Char || label.length == 1) settings.textSize.toFloat() else settings.textSize * 0.75f
            text = label
            background = drawable
        }
        // Half the gap on each side gives the chosen space between neighbouring keys.
        val margin = (settings.keyGap * density / 2).toInt()
        val lp = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight)
        lp.setMargins(margin, margin, margin, margin)
        view.layoutParams = lp

        val repeatDelete = object : Runnable {
            override fun run() {
                listener.onDelete()
                keyHandler.postDelayed(this, 60)
            }
        }
        val showAlternates = Runnable { openAlternates(view, key) }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    drawable.setColor(pressedColor)
                    tapFeedback(v, soundFor(key))
                    when (key) {
                        Key.Delete -> {
                            listener.onDelete()
                            keyHandler.postDelayed(repeatDelete, settings.longPressMs.toLong())
                        }
                        Key.Space -> {
                            spaceLastX = event.x
                            spaceMoved = false
                        }
                        is Key.Char -> {
                            if (settings.keyPreview) showPreview(v, labelFor(key))
                            if (key.alternates.isNotEmpty()) keyHandler.postDelayed(showAlternates, settings.longPressMs.toLong())
                        }
                        else -> {}
                    }
                }
                MotionEvent.ACTION_MOVE -> when {
                    alternates.isNotEmpty() -> selectAlternate(event.rawX)
                    key == Key.Space -> moveCursorBySwipe(event.x)
                }
                MotionEvent.ACTION_UP -> {
                    finishPress(drawable, color, repeatDelete, showAlternates)
                    when {
                        alternates.isNotEmpty() -> {
                            listener.onText(alternates[selectedAlternate])
                            if (shiftState == ShiftState.ON) shiftState = ShiftState.OFF
                        }
                        key == Key.Delete -> {}
                        key == Key.Space && spaceMoved -> {}
                        else -> onKey(key)
                    }
                    closeAlternates()
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishPress(drawable, color, repeatDelete, showAlternates)
                    closeAlternates()
                }
            }
            true
        }
        return view
    }

    private fun finishPress(drawable: GradientDrawable, color: Int, vararg pending: Runnable) {
        drawable.setColor(color)
        pending.forEach { keyHandler.removeCallbacks(it) }
        preview.visibility = GONE
    }

    /** Dragging along the space bar moves the cursor one character per step. */
    private fun moveCursorBySwipe(x: Float) {
        val step = dp(12)
        val moved = ((x - spaceLastX) / step).toInt()
        if (moved == 0) return
        spaceMoved = true
        spaceLastX += moved * step
        val code = if (moved > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(abs(moved)) { listener.onKeyCode(code) }
    }

    /** Position of [view] inside the overlay, as (left, top). */
    private fun positionOf(view: View): Pair<Int, Int> {
        val mine = IntArray(2).also { overlay.getLocationInWindow(it) }
        val theirs = IntArray(2).also { view.getLocationInWindow(it) }
        return Pair(theirs[0] - mine[0], theirs[1] - mine[1])
    }

    private fun showPreview(key: View, label: String) {
        val (left, top) = positionOf(key)
        val width = (key.width * 1.3f).toInt()
        val height = (key.height * 1.25f).toInt()
        preview.text = label
        preview.layoutParams = LayoutParams(width, height).apply {
            leftMargin = (left - (width - key.width) / 2).coerceIn(0, maxOf(0, overlay.width - width))
            topMargin = maxOf(0, top - height - dp(4))
        }
        preview.visibility = VISIBLE
    }

    private fun openAlternates(key: View, k: Key.Char) {
        val upper = shiftState != ShiftState.OFF && !showSymbols
        val main = if (upper) k.value.uppercase() else k.value
        alternates = (listOf(main) + k.alternates.map { if (upper) it.uppercase() else it.toString() }).distinct()
        selectedAlternate = 1.coerceAtMost(alternates.size - 1)
        preview.visibility = GONE
        if (settings.vibrate) key.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        alternatesRow.removeAllViews()
        val cellWidth = key.width
        alternates.forEach { text ->
            alternatesRow.addView(TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = settings.textSize.toFloat()
                this.text = text
            }, LinearLayout.LayoutParams(cellWidth, LayoutParams.MATCH_PARENT))
        }
        val (left, top) = positionOf(key)
        val rowWidth = cellWidth * alternates.size
        alternatesRow.layoutParams = LayoutParams(rowWidth, key.height).apply {
            leftMargin = (left - cellWidth / 2).coerceIn(0, maxOf(0, overlay.width - rowWidth))
            topMargin = maxOf(0, top - key.height - dp(4))
        }
        alternatesRow.visibility = VISIBLE
        highlightAlternate()
    }

    private fun selectAlternate(rawX: Float) {
        val origin = IntArray(2).also { alternatesRow.getLocationOnScreen(it) }
        val cell = alternatesRow.width / alternates.size.coerceAtLeast(1)
        if (cell == 0) return
        val index = ((rawX - origin[0]) / cell).toInt().coerceIn(0, alternates.size - 1)
        if (index != selectedAlternate) {
            selectedAlternate = index
            highlightAlternate()
        }
    }

    private fun highlightAlternate() {
        for (i in 0 until alternatesRow.childCount) {
            val cell = alternatesRow.getChildAt(i) as TextView
            val selected = i == selectedAlternate
            cell.background = if (selected) rounded(theme.accent) else null
            cell.setTextColor(if (selected) Color.WHITE else theme.text)
        }
    }

    private fun closeAlternates() {
        alternates = emptyList()
        alternatesRow.visibility = GONE
    }

    private fun labelFor(key: Key): String = when (key) {
        is Key.Char -> if (shiftState != ShiftState.OFF && !showSymbols) key.value.uppercase() else key.value
        Key.Shift -> when (shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.ON -> "⬆"
            ShiftState.LOCKED -> "⇪"
        }
        Key.CapsLock -> "Caps"
        Key.Tab -> "Tab"
        Key.Delete -> "⌫"
        Key.Symbols -> "?123"
        Key.Letters -> "ABC"
        Key.Space -> "spasi"
        Key.Enter -> enterLabel
        Key.Left -> "←"
        Key.Right -> "→"
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
            Key.CapsLock -> shiftState = if (shiftState == ShiftState.LOCKED) ShiftState.OFF else ShiftState.LOCKED
            Key.Tab -> listener.onKeyCode(KeyEvent.KEYCODE_TAB)
            Key.Left -> listener.onKeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
            Key.Right -> listener.onKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
            Key.Delete -> listener.onDelete()
            Key.Symbols -> { showSymbols = true; render() }
            Key.Letters -> { showSymbols = false; render() }
            Key.Space -> listener.onText(" ")
            Key.Enter -> listener.onEnter()
        }
    }

    private fun soundFor(key: Key) = when (key) {
        Key.Delete -> AudioManager.FX_KEYPRESS_DELETE
        Key.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
        Key.Enter -> AudioManager.FX_KEYPRESS_RETURN
        else -> AudioManager.FX_KEYPRESS_STANDARD
    }

    private fun tapFeedback(view: View, sound: Int) {
        if (settings.vibrate) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (settings.sound) audio?.playSoundEffect(sound, -1f)
    }

    private fun dp(value: Int) = (value * density).toInt()
}
