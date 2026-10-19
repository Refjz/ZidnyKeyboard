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
import android.widget.ScrollView
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
        fun onUndoTranslate()
        fun onVoice()
        /** The user picked the language to translate into from the language panel. */
        fun onPickLanguage(language: Language)
        /** The user picked how the translation should sound from the language panel. */
        fun onPickStyle(style: TranslationStyle)
        /** The user picked [word] from the suggestion bar to replace the word being typed. */
        fun onSuggestion(word: String)
        /** Texts copied earlier, newest first, for the 📋 panel. */
        fun clipboardItems(): List<String>
        fun onClipboardRemove(text: String)
        fun onClipboardClear()
        /** The user tapped the chip for what they just copied, to paste it. */
        fun onRecentClip()
        /** The user long-pressed that chip to hide it. */
        fun onRecentClipDismiss()
    }

    enum class ShiftState { OFF, ON, LOCKED }

    private var showSymbols = false
    private var showEmoji = false
    private var showClipboard = false
    private var showPicker = false
    private var pickedLanguage = Language.ENGLISH
    private var pickedStyle = TranslationStyle.CASUAL

    /** Shown under the choices in the language panel, e.g. that they are remembered for this app. */
    var pickerHint = ""
    private var emojiCategory = 0
    private var lastShiftTap = 0L
    private val keyHandler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val theme = settings.theme
    private val audio = context.getSystemService(AudioManager::class.java)

    /** Changing it relabels the keys in place: rebuilding them mid-typing drops a key that is still held. */
    var shiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            refreshKeys()
        }

    /** Label for the enter key, e.g. "Kirim" when the app wants a send action. */
    var enterLabel = "⏎"
        set(value) {
            if (field == value) return
            field = value
            refreshKeys()
        }

    /** The keys on screen now, so a change of Shift or the enter label can update them without a rebuild. */
    private val keyViews = mutableListOf<Pair<Key, TextView>>()

    private val languageButton = toolbarButton {
        showPicker = !showPicker
        showClipboard = false
        showEmoji = false
        render()
    }
    private val translateButton = toolbarButton { listener.onTranslate() }.apply {
        text = "Terjemahkan"
        setTextColor(Color.WHITE)
        (background as GradientDrawable).setColor(theme.accent)
    }
    private val undoButton = toolbarButton { listener.onUndoTranslate() }.apply {
        text = "↶"
        visibility = GONE
    }
    private val micButton = toolbarButton { listener.onVoice() }.apply { text = "🎤" }
    private val clipboardButton = toolbarButton {
        showClipboard = !showClipboard
        showEmoji = false
        showPicker = false
        render()
    }.apply { text = "📋" }
    private val statusText = TextView(context).apply {
        setTextColor(theme.secondaryText)
        textSize = 13f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        // A long status (like the meaning of a translation) is cut off; tapping it shows the whole message.
        setOnClickListener { if (messageChip.visibility == VISIBLE) messagePanel.visibility = VISIBLE }
    }
    private val toolbar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(languageButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(statusText, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(undoButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(clipboardButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(micButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(translateButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
    }

    /** Word suggestions, plus a button that reopens the last message (e.g. a translated copied message). */
    private val messageChip = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 16f
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(theme.specialKey)
        visibility = GONE
        setOnClickListener { messagePanel.visibility = VISIBLE }
    }
    /** What the user just copied (text or a picture), pasted with one tap. */
    private val recentClipChip = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        textSize = 14f
        maxLines = 1
        maxWidth = dp(160)
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dp(10), 0, dp(10), 0)
        background = rounded(theme.specialKey)
        visibility = GONE
        setOnClickListener {
            tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
            listener.onRecentClip()
        }
        setOnLongClickListener {
            listener.onRecentClipDismiss()
            true
        }
    }
    private val suggestionViews = List(3) {
        TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { v ->
                val word = (v as TextView).text.toString()
                if (word.isNotEmpty()) {
                    tapFeedback(v, AudioManager.FX_KEYPRESS_STANDARD)
                    listener.onSuggestion(word)
                }
            }
        }
    }
    private val strip = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(messageChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(recentClipChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        suggestionViews.forEach { addView(it, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
    }

    /** A message laid over the keys: a translated copied message, or a translation waiting to be used. */
    private val messageTitle = TextView(context).apply {
        setTextColor(theme.secondaryText)
        textSize = 13f
    }
    private val messageBody = TextView(context).apply {
        setTextColor(theme.text)
        textSize = 17f
        setTextIsSelectable(false)
    }
    private val messageAction = toolbarButton { messageActionHandler?.invoke() }.apply {
        setTextColor(Color.WHITE)
        (background as GradientDrawable).setColor(theme.accent)
    }
    private var messageActionHandler: (() -> Unit)? = null
    private val messagePanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(theme.background).apply { setStroke(dp(1), theme.specialKey) }
        elevation = dp(10).toFloat()
        visibility = GONE
        // Swallow touches so the keys underneath are not pressed through the panel.
        isClickable = true
        addView(messageTitle)
        addView(ScrollView(context).apply { addView(messageBody) }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(toolbarButton { this@KeyboardView.hideMessagePanel() }.apply { text = "Tutup" },
                LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).withMargins())
            addView(messageAction, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).withMargins())
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** The toolbar and key rows. */
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /** Key rows (and panels) with the height they ask for; shrunk in [onMeasure] when the screen has no room. */
    private val shrinkable = mutableListOf<Pair<View, Int>>()

    /** Drawn above the keys: the enlarged letter while pressing, and the row of extra characters while holding. */
    private val overlay = FrameLayout(context)
    private val preview = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(theme.text)
        textSize = settings.textSize * 1.4f
        background = rounded(theme.key)
        elevation = dp(6).toFloat()
        // Hidden with INVISIBLE rather than GONE: showing it again then needs no new layout pass.
        visibility = INVISIBLE
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
        // Android 15+ draws the keyboard behind the navigation bar, so lift the keys above it, plus the
        // user's own gap for phones whose hide/switch-keyboard buttons still cover the bottom row.
        val bottomGap = dp(settings.bottomGap)
        setPadding(pad, pad, pad, pad + bottomGap)
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bottom = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom,
            )
            view.setPadding(pad, pad, pad, pad + bottom + bottomGap)
            insets
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        overlay.addView(preview, LayoutParams(0, 0))
        overlay.addView(alternatesRow, LayoutParams(LayoutParams.WRAP_CONTENT, 0))
        overlay.addView(messagePanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(TOOLBAR_DP)
        })
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        render()
    }

    /** Shows the target language and style on the language button, e.g. "→ EN 😎". */
    fun setTargetLanguage(language: Language, style: TranslationStyle) {
        pickedLanguage = language
        pickedStyle = style
        languageButton.text = "→ ${language.label} ${style.emoji}"
        if (showPicker) render()
    }

    fun setStatus(message: String) {
        if (statusText.text.toString() != message) statusText.text = message
    }

    fun setSuggestions(words: List<String>) {
        suggestionViews.forEachIndexed { i, view ->
            val word = words.getOrNull(i) ?: ""
            if (view.text.toString() != word) view.text = word
        }
    }

    /** Shows [label] as a one-tap paste chip next to the suggestions, or hides it when null. */
    fun setRecentClip(label: String?) {
        recentClipChip.text = label ?: ""
        recentClipChip.visibility = if (label != null) VISIBLE else GONE
    }

    fun setUndoVisible(visible: Boolean) {
        undoButton.visibility = if (visible) VISIBLE else GONE
    }

    fun setListening(listening: Boolean) {
        (micButton.background as GradientDrawable).setColor(if (listening) theme.accent else theme.specialKey)
    }

    /**
     * Shows [body] over the keys, or with [open] false only offers it behind the [icon] chip.
     * With [actionLabel], a button runs [action]; the chip reopens the message later.
     */
    fun showMessage(
        icon: String, title: String, body: String, actionLabel: String? = null, open: Boolean = true, action: (() -> Unit)? = null,
    ) {
        messageTitle.text = title
        messageBody.text = body
        messageActionHandler = action
        messageAction.text = actionLabel ?: ""
        messageAction.visibility = if (actionLabel != null) VISIBLE else GONE
        messageChip.text = icon
        messageChip.visibility = VISIBLE
        messagePanel.visibility = if (open) VISIBLE else GONE
    }

    fun hideMessagePanel() {
        messagePanel.visibility = GONE
    }

    fun clearMessage() {
        messagePanel.visibility = GONE
        messageChip.visibility = GONE
        messageActionHandler = null
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
        shrinkable.clear()
        keyViews.clear()
        content.addView(toolbar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(TOOLBAR_DP)))
        content.addView(strip, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(STRIP_DP)))
        (clipboardButton.background as GradientDrawable).setColor(if (showClipboard) theme.accent else theme.specialKey)
        (languageButton.background as GradientDrawable).setColor(if (showPicker) theme.accent else theme.specialKey)
        languageButton.setTextColor(if (showPicker) Color.WHITE else theme.text)
        if (showPicker) {
            renderPicker()
            return
        }
        if (showClipboard) {
            renderClipboard()
            return
        }
        if (showEmoji) {
            renderEmoji()
            return
        }
        val layout = if (showSymbols) KeyboardLayouts.symbols else KeyboardLayouts.letters(settings.layout, settings.numberRow)
        val rowHeight = dp(52 * settings.heightPercent / 100)
        for (keys in layout) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (key in keys) row.addView(createKey(key))
            addShrinkable(row, rowHeight)
        }
    }

    private fun addShrinkable(view: View, height: Int) {
        content.addView(view, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height))
        shrinkable.add(view to height)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        stopKeyRepeat()
        super.onDetachedFromWindow()
    }

    /**
     * A key row can be rebuilt while a key is held (deleting to the start of a sentence turns on
     * Shift), and the removed key then never sees its finger lift. Watching the whole gesture here
     * makes sure a held Delete stops repeating as soon as the finger leaves the screen.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) stopKeyRepeat()
        return handled
    }

    /** Stops any held-key repeat and pending long-press, e.g. when the keyboard closes. */
    fun stopKeyRepeat() {
        keyHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Always asks for exactly the height the toolbar, keys and bottom gap need, so the window grows with
     * the settings. If the system offers less room than that, the key rows shrink to fit instead of the
     * bottom row being cut off.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        shrinkable.forEach { (view, height) -> view.layoutParams.height = height }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val needed = measuredHeight
        val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) needed
        else MeasureSpec.getSize(heightMeasureSpec)
        val excess = needed - limit
        val total = shrinkable.sumOf { it.second }
        if (excess > 0 && total > 0) {
            shrinkable.forEach { (view, height) ->
                view.layoutParams.height = (height - excess.toLong() * height / total).toInt().coerceAtLeast(dp(24))
            }
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(minOf(needed, limit), MeasureSpec.EXACTLY))
    }

    /** Choose the language to translate into and how it should sound. */
    private fun renderPicker() {
        val rowHeight = dp(52 * settings.heightPercent / 100)
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        fun caption(text: String) = TextView(context).apply {
            this.text = text
            setTextColor(theme.secondaryText)
            textSize = 12f
            setPadding(dp(6), dp(8), dp(6), dp(2))
        }
        fun choiceRow(choices: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            choices.forEachIndexed { index, (label, selected) ->
                addView(toolbarButton { onPick(index) }.apply {
                    text = label
                    textSize = 14f
                    setPadding(dp(4), 0, dp(4), 0)
                    if (selected) {
                        setTextColor(Color.WHITE)
                        (background as GradientDrawable).setColor(theme.accent)
                    }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).withMargins())
            }
        }

        panel.addView(caption("Terjemahkan ke"))
        val languages = Language.entries
        panel.addView(choiceRow(languages.map { it.displayName to (it == pickedLanguage) }) {
            listener.onPickLanguage(languages[it])
        })
        panel.addView(caption("Gaya bahasa"))
        val styles = TranslationStyle.entries
        panel.addView(choiceRow(styles.map { "${it.emoji} ${it.displayName}" to (it == pickedStyle) }) {
            listener.onPickStyle(styles[it])
        })
        if (pickerHint.isNotEmpty()) panel.addView(caption(pickerHint))

        // Like the emoji panel, the choices and bottom row take the height of the letter rows they replace.
        val letterRows = KeyboardLayouts.letters(settings.layout, settings.numberRow).size
        addShrinkable(ScrollView(context).apply { addView(panel) }, (rowHeight * (letterRows - 1)).coerceAtLeast(dp(120)))

        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(Key.Letters, Key.Space, Key.Delete).forEach { bottom.addView(createKey(it)) }
        addShrinkable(bottom, rowHeight)
    }

    /** Recently copied texts: tap one to paste it, hold it to remove it. */
    private fun renderClipboard() {
        val rowHeight = dp(52 * settings.heightPercent / 100)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply {
                text = "Riwayat salinan · ketuk untuk tempel, tahan untuk hapus"
                setTextColor(theme.secondaryText)
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(toolbarButton {
                listener.onClipboardClear()
                render()
            }.apply { text = "Hapus semua" }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        }
        content.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))

        val items = listener.clipboardItems()
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "Belum ada teks yang disalin. Salin teks di aplikasi mana saja, nanti muncul di sini."
                setTextColor(theme.secondaryText)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
            })
        }
        items.forEach { item ->
            list.addView(TextView(context).apply {
                text = item
                setTextColor(theme.text)
                textSize = 15f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(theme.key)
                setOnClickListener {
                    tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                    listener.onText(item)
                    showClipboard = false
                    render()
                }
                setOnLongClickListener {
                    listener.onClipboardRemove(item)
                    render()
                    true
                }
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).withMargins())
        }
        // Like the emoji panel, the list and bottom row take the height of the letter rows they replace.
        val letterRows = KeyboardLayouts.letters(settings.layout, settings.numberRow).size
        val listHeight = rowHeight * (letterRows - 1) - dp(40)
        addShrinkable(ScrollView(context).apply { addView(list) }, listHeight.coerceAtLeast(dp(120)))

        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(Key.Letters, Key.Space, Key.Delete).forEach { bottom.addView(createKey(it)) }
        addShrinkable(bottom, rowHeight)
    }

    private fun renderEmoji() {
        val rowHeight = dp(52 * settings.heightPercent / 100)
        val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        EmojiData.categories.forEachIndexed { index, (icon, _) ->
            tabs.addView(TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 20f
                text = icon
                if (index == emojiCategory) background = rounded(theme.specialKey)
                setOnClickListener {
                    emojiCategory = index
                    render()
                }
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        content.addView(tabs, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))

        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        EmojiData.categories[emojiCategory].second.chunked(EMOJI_COLUMNS).forEach { line ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until EMOJI_COLUMNS) {
                val emoji = line.getOrNull(i) ?: ""
                row.addView(TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 26f
                    text = emoji
                    if (emoji.isNotEmpty()) setOnClickListener {
                        tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                        listener.onText(emoji)
                    }
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            grid.addView(row)
        }
        // The tabs, grid and bottom row together take the height of the letter rows they replace.
        val letterRows = KeyboardLayouts.letters(settings.layout, settings.numberRow).size
        val gridHeight = rowHeight * (letterRows - 1) - dp(40)
        addShrinkable(ScrollView(context).apply { addView(grid) }, gridHeight.coerceAtLeast(dp(120)))

        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(Key.Letters, Key.Space, Key.Delete).forEach { bottom.addView(createKey(it)) }
        addShrinkable(bottom, rowHeight)
    }

    private fun isSpecial(key: Key) = key !is Key.Char || key.value in listOf(",", ".")

    private fun colorFor(key: Key) = when {
        key == Key.CapsLock && shiftState == ShiftState.LOCKED -> theme.accent
        isSpecial(key) -> theme.specialKey
        else -> theme.key
    }

    private fun applyLabel(view: TextView, key: Key) {
        val label = labelFor(key)
        if (view.text.toString() == label) return
        view.textSize = if (key is Key.Char || label.length == 1) settings.textSize.toFloat() else settings.textSize * 0.75f
        view.text = label
    }

    /** Updates labels and colours that depend on Shift or the enter label, keeping the same key views. */
    private fun refreshKeys() {
        for ((key, view) in keyViews) {
            applyLabel(view, key)
            if (key == Key.CapsLock) (view.background as GradientDrawable).setColor(colorFor(key))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKey(key: Key): TextView {
        val drawable = rounded(colorFor(key))
        val view = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.text)
            background = drawable
        }
        applyLabel(view, key)
        keyViews.add(key to view)
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
        val showAlternates = Runnable { if (key is Key.Char) openAlternates(view, key) }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    drawable.setColor(ColorUtils.blendARGB(colorFor(key), theme.text, 0.18f))
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
                    finishPress(drawable, colorFor(key), repeatDelete, showAlternates)
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
                    finishPress(drawable, colorFor(key), repeatDelete, showAlternates)
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
        preview.visibility = INVISIBLE
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
        // Resize only when the key size differs and place it with translation, so a key press doesn't
        // make the whole keyboard measure and lay itself out again.
        val lp = preview.layoutParams
        if (lp.width != width || lp.height != height) {
            lp.width = width
            lp.height = height
            preview.layoutParams = lp
        }
        preview.translationX = (left - (width - key.width) / 2).coerceIn(0, maxOf(0, overlay.width - width)).toFloat()
        preview.translationY = maxOf(0, top - height - dp(4)).toFloat()
        preview.visibility = VISIBLE
    }

    private fun openAlternates(key: View, k: Key.Char) {
        val upper = shiftState != ShiftState.OFF && !showSymbols
        val main = if (upper) k.value.uppercase() else k.value
        alternates = (listOf(main) + k.alternates.map { if (upper) it.uppercase() else it.toString() }).distinct()
        selectedAlternate = 1.coerceAtMost(alternates.size - 1)
        preview.visibility = INVISIBLE
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
        Key.Emoji -> "☺"
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
            Key.Letters -> { showSymbols = false; showEmoji = false; showClipboard = false; showPicker = false; render() }
            Key.Emoji -> { showEmoji = true; render() }
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

    private companion object {
        const val TOOLBAR_DP = 44
        const val STRIP_DP = 38
        const val EMOJI_COLUMNS = 8
    }
}
