package com.zidny.keyboard

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
        /** The user held Terjemahkan to turn translating while typing on or off. */
        fun onToggleLiveTranslate()
        /** With live translation on, what the user has typed so far (the keys type here, not into the app). */
        fun onLiveInput(text: String)
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
        /** The user asked for replies to the message they copied. */
        fun onSuggestReplies()
        /** The user tapped ⚙️ to open the keyboard's settings. */
        fun onOpenSettings()
        /** The user tapped 🔍 to read the copied message in Indonesian before replying. */
        fun onReadCopied()
        /** Text typed in the 文A translate box, to be translated into [target]; answer with [showBoxTranslation]. */
        fun onTranslateBox(text: String, target: Language)
        /** The user tapped 🪄 to tidy up what they typed, e.g. "km dmn" → "Kamu di mana?". */
        fun onTidy()
        /** The user picked a mood in the 🎭 panel; answer with [showLines]. */
        fun onWriteLines(mood: LineMood, language: Language)
        /** The user tapped one of the 🎭 lines to send it. */
        fun onLinePicked(text: String)
        /** Everything in the text field, for the 🔤 app's counts; empty when it can't be read. */
        fun fieldText(): String
        /** The user picked how the 🔤 app should change everything in the text field. */
        fun onTextTool(action: TextTools.Action)
    }

    enum class ShiftState { OFF, ON, LOCKED }

    /** What replaces the letter keys: nothing, or one of the panels opened from the toolbar. */
    private enum class Panel {
        KEYS, PICKER, CLIPBOARD, EMOJI, REPLIES, TRANSLATOR, FONTS, KAOMOJI, LINES,
        // The 🧰 menu and the small apps in it.
        TOOLS, CALCULATOR, CONVERTER, CLOCK, CALENDAR, TEXT,
    }

    private var panel = Panel.KEYS
    private var showSymbols = false

    // The reply suggestions panel: null replies means they are still loading.
    private var repliesTitle = ""
    private var replies: List<TextTranslator.Reply>? = null
    private var repliesLanguage: Language? = null

    private var calcExpression = ""
    // The 💱 converter: the amount as typed, which unit is being picked (null for none) and the saved rates.
    private var convCategory = UnitConverter.Category.MONEY
    private val convFrom = mutableMapOf(UnitConverter.Category.MONEY to 1)
    private val convTo = mutableMapOf<UnitConverter.Category, Int>()
    private var convAmount = "1"
    private var convPicking: Boolean? = null
    private var rates: ExchangeRates.Rates? = null
    // The 🕐 world clock shows the time now, or a time the user stepped to with the arrows.
    private var clockTime: Long? = null
    // The 📅 calendar: the month shown and the picked day.
    private val today get() = java.util.Calendar.getInstance()
    private var calYear = today.get(java.util.Calendar.YEAR)
    private var calMonth = today.get(java.util.Calendar.MONTH)
    private var calDay = today.get(java.util.Calendar.DAY_OF_MONTH)
    private val calcExpressionView = TextView(context)
    private val calcResultView = TextView(context)
    // The 文A translate box: text typed into the box itself rather than the app, like Google Translate.
    private var boxText = ""
    private var boxTarget: Language? = null
    private val boxInput = TextView(context)
    private val boxResult = TextView(context)
    private val boxNote = TextView(context)
    private val boxTimer = Handler(Looper.getMainLooper())
    private val translateBox = Runnable {
        if (boxText.isNotBlank()) listener.onTranslateBox(boxText, boxTarget ?: pickedLanguage)
    }
    private var pickedLanguage = Language.ENGLISH

    /** The 𝓐 letter style everything is typed in, until set back to normal. */
    private var fancyFont = FancyFont.NORMAL
    val fancyActive get() = fancyFont != FancyFont.NORMAL
    private var kaomojiCategory = 0

    // The 🎭 panel: lines are null while they load, and empty after a failure explained in linesTitle.
    private var linesMood: LineMood? = null
    private var linesLanguage = Language.INDONESIAN
    private var linesTitle = ""
    private var lines: List<TextTranslator.Reply>? = null
    private var pickedStyle = TranslationStyle.CASUAL

    /** Shown under the choices in the language panel, e.g. that they are remembered for this app. */
    var pickerHint = ""
    private var emojiCategory = 0
    private var lastShiftTap = 0L
    private val keyHandler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    /** The theme drawn now: with the automatic theme on, it depends on the time the keyboard opened. */
    val themeInUse = settings.themeAt(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    private val theme = themeInUse
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

    private val languageButton = toolbarButton { togglePanel(Panel.PICKER) }
    private val translateButton = toolbarButton { listener.onTranslate() }.apply {
        text = "Terjemahkan"
        setTextColor(Color.WHITE)
        (background as GradientDrawable).setColor(theme.accent)
        setOnLongClickListener {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener.onToggleLiveTranslate()
            true
        }
    }
    /**
     * With live translation on, the keys type here and the app's field gets the translation, like Gboard's
     * translate mode. This line shows what the user actually typed; a tap asks the AI for a better translation.
     */
    private val livePreview = TextView(context).apply {
        setTextColor(theme.text)
        hint = "⚡ Ketik di sini, terjemahannya langsung masuk ke kolom chat"
        setHintTextColor(theme.secondaryText)
        textSize = 14f
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.START
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
        background = rounded(theme.key)
        visibility = GONE
        setOnClickListener {
            tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
            listener.onTranslate()
        }
    }
    private var liveTranslate = false
    /** What the user typed while live translation is on. */
    var liveText = ""
        private set
    private val undoButton = toolbarButton { listener.onUndoTranslate() }.apply {
        text = "↶"
        visibility = GONE
    }
    // Each tool can be switched off in the settings, so the toolbar holds only what the user wants.
    private val micButton = iconButton("🎤") { listener.onVoice() }.shownIf(settings.toolMic)
    private val emojiButton = iconButton("😊") { togglePanel(Panel.EMOJI) }.shownIf(settings.toolEmoji)
    private val readButton = iconButton("🔍") { listener.onReadCopied() }.shownIf(settings.toolRead)
    private val replyButton = iconButton("💬") { listener.onSuggestReplies() }.shownIf(settings.replySuggestions)
    private val boxButton = iconButton("文A") { togglePanel(Panel.TRANSLATOR) }.shownIf(settings.toolTranslateBox)
    private val clipboardButton = iconButton("📋") { togglePanel(Panel.CLIPBOARD) }.shownIf(settings.toolClipboard)
    private val toolsButton = iconButton("🧰") {
        togglePanel(if (panel in TOOL_APPS) panel else Panel.TOOLS)
    }.shownIf(settings.toolApps && toolApps().isNotEmpty())
    private val kaomojiButton = iconButton("ツ") { togglePanel(Panel.KAOMOJI) }.shownIf(settings.toolKaomoji)
    private val linesButton = iconButton("🎭") { togglePanel(Panel.LINES) }.shownIf(settings.toolLines)
    private val tidyButton = iconButton("🪄") { listener.onTidy() }.shownIf(settings.toolTidy)
    private val settingsButton = iconButton("⚙️") { listener.onOpenSettings() }

    /** Toolbar buttons that open a panel, so the open one can be highlighted. */
    private val panelButtons = mapOf(
        Panel.PICKER to languageButton,
        Panel.EMOJI to emojiButton,
        Panel.CLIPBOARD to clipboardButton,
        Panel.TOOLS to toolsButton,
        Panel.REPLIES to replyButton,
        Panel.TRANSLATOR to boxButton,
        Panel.KAOMOJI to kaomojiButton,
        Panel.LINES to linesButton,
    )

    /** Shown in the suggestion bar while there are no word suggestions. */
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
    /** The tools scroll sideways on narrow screens; undo and Terjemahkan always stay in view. */
    private val toolbar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val tools = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(
                languageButton, boxButton, readButton, replyButton, tidyButton, linesButton, emojiButton, kaomojiButton,
                clipboardButton, micButton, toolsButton, settingsButton,
            ).forEach {
                addView(it, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
            }
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(tools, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(livePreview, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).withMargins())
        addView(undoButton, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
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
    // The toolbar shows for a few seconds when the keyboard opens and then gets out of the way;
    // this button in the suggestion bar brings it back.
    private var toolbarShown = true
    private var busy = false
    private val toolbarTimer = Handler(Looper.getMainLooper())
    private val hideToolbar = object : Runnable {
        override fun run() {
            // Keep it while something on it is in use, and look again a little later.
            if (panel != Panel.KEYS || busy || liveTranslate || messagePanel.visibility == VISIBLE) {
                toolbarTimer.postDelayed(this, TOOLBAR_SHOW_MS)
            } else {
                setToolbarShown(false)
            }
        }
    }
    private val toolbarToggle = iconButton("🌐") {
        toolbarTimer.removeCallbacks(hideToolbar)
        setToolbarShown(!toolbarShown)
    }.apply { visibility = if (settings.autoHideToolbar) VISIBLE else GONE }

    /**
     * Autofill suggestions (a saved address, phone number or password from Samsung Pass or Google) shown in the
     * suggestion bar, like Gboard does. Without them the autofill service opens its own pop-up over the app, and
     * that pop-up takes the focus and closes the keyboard.
     */
    private val inlineChips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val inlineRow = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        visibility = GONE
        addView(inlineChips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val strip = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(toolbarToggle, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(messageChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(recentClipChip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        addView(inlineRow, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(statusText, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        suggestionViews.forEach {
            it.visibility = GONE
            addView(it, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
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
    private val effects = KeyEffectsView(context, theme.accent, theme.text)
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
        val photo = if (settings.backgroundImage) KeyboardBackground.load(context, settings.backgroundBlur, settings.backgroundDim) else null
        if (photo != null) background = photo
        else setBackgroundColor(if (settings.backgroundColor != 0) settings.backgroundColor else theme.background)
        if (settings.sound && settings.soundStyle != SoundStyle.SYSTEM) TypingSounds.prepare(context)
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
        overlay.addView(effects, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.addView(preview, LayoutParams(0, 0))
        overlay.addView(alternatesRow, LayoutParams(LayoutParams.WRAP_CONTENT, 0))
        overlay.addView(messagePanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            topMargin = toolbarHeight()
        })
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        render()
    }

    /** Shows the target language and style on the language button, e.g. "→ EN 😎". */
    fun setTargetLanguage(language: Language, style: TranslationStyle) {
        pickedLanguage = language
        pickedStyle = style
        languageButton.text = "→ ${language.label} ${style.emoji}"
        if (panel == Panel.PICKER) render()
    }

    /** Turns live translation on or off: the typing line replaces the tools and the toolbar stays in view. */
    fun setLiveTranslate(on: Boolean) {
        liveTranslate = on
        translateButton.text = if (on) "⚡ AI" else "Terjemahkan"
        clearLiveText()
        livePreview.visibility = if (on) VISIBLE else GONE
        toolbar.getChildAt(0).visibility = if (on) GONE else VISIBLE
        if (on) setToolbarShown(true)
    }

    /** Starts a new message, e.g. after the last one was sent and the app emptied its field. */
    fun clearLiveText() {
        liveText = ""
        livePreview.text = ""
    }

    private fun setLiveText(text: String) {
        liveText = text
        livePreview.text = if (text.length > BOX_VISIBLE_CHARS) "…" + text.takeLast(BOX_VISIBLE_CHARS) else text
        listener.onLiveInput(text)
    }

    fun setStatus(message: String) {
        if (statusText.text.toString() != message) statusText.text = message
    }

    /** Shows [words] in the suggestion bar, or the status line when there are none. */
    fun setSuggestions(words: List<String>) {
        lastWords = words
        val inline = inlineRow.visibility == VISIBLE
        val any = !inline && words.any { it.isNotEmpty() }
        suggestionViews.forEachIndexed { i, view ->
            val word = words.getOrNull(i) ?: ""
            if (view.text.toString() != word) view.text = word
            setVisible(view, any)
        }
        setVisible(statusText, !any && !inline)
    }
    private var lastWords: List<String> = emptyList()

    /** Shows autofill suggestions in place of the word suggestions, or brings those back when [views] is empty. */
    fun setInlineSuggestions(views: List<View>) {
        inlineChips.removeAllViews()
        views.forEach {
            inlineChips.addView(it, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        }
        inlineRow.visibility = if (views.isEmpty()) GONE else VISIBLE
        setSuggestions(lastWords)
    }

    private fun setVisible(view: View, visible: Boolean) {
        val wanted = if (visible) VISIBLE else GONE
        if (view.visibility != wanted) view.visibility = wanted
    }

    /** Opens the reply suggestions panel; [replies] null shows that they are still loading. */
    fun showReplies(title: String, replies: List<TextTranslator.Reply>?, language: Language?) {
        repliesTitle = title
        this.replies = replies
        repliesLanguage = language
        panel = Panel.REPLIES
        render()
    }

    val isShowingReplies get() = panel == Panel.REPLIES

    /** Back to the letter keys, e.g. when the keyboard closes, so it doesn't reopen on an old panel. */
    fun closePanels() {
        // A letter style is for one text, so it ends with it rather than following the user to the next app.
        if (panel == Panel.KEYS && !showSymbols && fancyFont == FancyFont.NORMAL) return
        panel = Panel.KEYS
        showSymbols = false
        fancyFont = FancyFont.NORMAL
        render()
    }

    private fun togglePanel(target: Panel) {
        panel = if (panel == target) Panel.KEYS else target
        if (panel == Panel.CONVERTER) refreshRates()
        render()
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

    /** Shows the toolbar, then hides it after a few seconds when the user chose that in the settings. */
    fun showToolbarBriefly() {
        toolbarTimer.removeCallbacks(hideToolbar)
        setToolbarShown(true)
        if (settings.autoHideToolbar) toolbarTimer.postDelayed(hideToolbar, TOOLBAR_SHOW_MS)
    }

    private fun setToolbarShown(shown: Boolean) {
        toolbarShown = shown
        toolbar.visibility = if (shown) VISIBLE else GONE
        (messagePanel.layoutParams as? LayoutParams)?.let {
            it.topMargin = if (shown) toolbarHeight() else 0
            messagePanel.layoutParams = it
        }
    }

    fun setTranslating(busy: Boolean) {
        this.busy = busy
        // A translation started from a hidden toolbar (e.g. by voice) still shows its progress there.
        if (busy) setToolbarShown(true)
        translateButton.isEnabled = !busy
        translateButton.alpha = if (busy) 0.5f else 1f
    }

    private fun TextView.shownIf(shown: Boolean) = apply { visibility = if (shown) VISIBLE else GONE }

    private fun iconButton(icon: String, onClick: () -> Unit) = toolbarButton(onClick).apply {
        text = icon
        setPadding(dp(10), 0, dp(10), 0)
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
        content.addView(toolbar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, toolbarHeight()))
        content.addView(strip, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, stripHeight()))
        panelButtons.forEach { (buttonPanel, button) ->
            val open = buttonPanel == panel
            (button.background as GradientDrawable).setColor(if (open) theme.accent else theme.specialKey)
            button.setTextColor(if (open) Color.WHITE else theme.text)
        }
        // A letter style stays on after its panel closes, so 🧰 stays lit to show it.
        if (panel in TOOL_APPS || fancyFont != FancyFont.NORMAL) {
            (toolsButton.background as GradientDrawable).setColor(theme.accent)
            toolsButton.setTextColor(Color.WHITE)
        }
        when (panel) {
            Panel.PICKER -> return renderPicker()
            Panel.CLIPBOARD -> return renderClipboard()
            Panel.EMOJI -> return renderEmoji()
            Panel.TOOLS -> return renderTools()
            Panel.CALCULATOR -> return renderCalculator()
            Panel.CONVERTER -> return renderConverter()
            Panel.CLOCK -> return renderClock()
            Panel.CALENDAR -> return renderCalendar()
            Panel.REPLIES -> return renderReplies()
            Panel.TRANSLATOR -> renderTranslateBox()
            Panel.FONTS -> return renderFonts()
            Panel.TEXT -> return renderTextTools()
            Panel.KAOMOJI -> return renderKaomoji()
            Panel.LINES -> return renderLines()
            Panel.KEYS -> {}
        }
        val layout = if (showSymbols) KeyboardLayouts.symbols else KeyboardLayouts.letters(settings.layout, settings.numberRow)
        val rowHeight = keyRowHeight()
        for (keys in layout) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (key in keys) row.addView(createKey(key))
            // The F1–F12 row of the PC layout is lower than the letter rows.
            addShrinkable(row, if (keys.first() == Key.Escape) rowHeight * 3 / 4 else rowHeight)
        }
    }

    /**
     * A box above the keys that the keys type into instead of the app. What is typed is translated as the
     * user pauses, like Google Translate, and can be copied or typed into the app.
     */
    private fun renderTranslateBox() {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val target = boxTarget ?: pickedLanguage
            addView(toolbarButton {
                // Tapping steps through the languages.
                val all = Language.entries
                boxTarget = all[(all.indexOf(target) + 1) % all.size]
                render()
                scheduleBoxTranslation(0)
            }.apply { text = "Otomatis → ${target.flag} ${target.displayName}" },
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).withMargins())
            addView(toolbarButton {
                boxText = ""
                showBoxInput()
                showBoxTranslation("", "", "")
            }.apply { text = "🗑" }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        }
        content.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(theme.key)
            (boxInput.parent as? LinearLayout)?.removeView(boxInput)
            (boxResult.parent as? LinearLayout)?.removeView(boxResult)
            (boxNote.parent as? LinearLayout)?.removeView(boxNote)
            addView(boxInput.apply {
                setTextColor(theme.text)
                setHintTextColor(theme.secondaryText)
                hint = "Ketik teks yang mau diterjemahkan…"
                textSize = 16f
                maxLines = 2
            })
            addView(boxNote.apply {
                setTextColor(theme.secondaryText)
                textSize = 11f
                maxLines = 1
            })
            addView(ScrollView(context).apply {
                addView(boxResult.apply {
                    setTextColor(theme.accent)
                    textSize = 17f
                })
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        content.addView(box, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(110)).withMargins())
        showBoxInput()

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(toolbarButton {
                val text = boxResult.text.toString()
                if (text.isNotEmpty()) {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Terjemahan", text))
                    boxNote.text = "Hasil disalin"
                }
            }.apply { text = "📋 Salin" }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).withMargins())
            addView(toolbarButton {
                val text = boxResult.text.toString()
                if (text.isNotEmpty()) {
                    listener.onText(text)
                    boxText = ""
                    showBoxTranslation("", "", "")
                    panel = Panel.KEYS
                    render()
                }
            }.apply {
                text = "Ketik ke chat"
                setTextColor(Color.WHITE)
                (background as GradientDrawable).setColor(theme.accent)
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).withMargins())
        }
        content.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))
    }

    /** Shows the translation of [forText] in the 文A box, unless the user has typed on since. */
    fun showBoxTranslation(forText: String, result: String, note: String) {
        if (forText != boxText) return
        boxResult.text = result
        boxNote.text = note
    }

    private fun scheduleBoxTranslation(delay: Long = BOX_DELAY_MS) {
        boxTimer.removeCallbacks(translateBox)
        if (boxText.isBlank()) showBoxTranslation(boxText, "", "")
        else boxTimer.postDelayed(translateBox, delay)
    }

    /** Types [text] into the 文A box when it is open, otherwise into the app. */
    private fun type(text: String) {
        if (liveTranslate && panel == Panel.KEYS) return setLiveText(liveText + text)
        if (panel != Panel.TRANSLATOR) return listener.onText(fancyFont.apply(text))
        boxText += text
        showBoxInput()
        scheduleBoxTranslation()
    }

    private fun deleteOne() {
        if (liveTranslate && panel == Panel.KEYS && liveText.isNotEmpty()) {
            val start = java.text.BreakIterator.getCharacterInstance().run {
                setText(liveText)
                preceding(liveText.length).coerceAtLeast(0)
            }
            return setLiveText(liveText.substring(0, start))
        }
        if (panel != Panel.TRANSLATOR) return listener.onDelete()
        if (boxText.isEmpty()) return
        // Remove a whole emoji or accented letter, not half of it.
        val end = boxText.length
        val start = java.text.BreakIterator.getCharacterInstance().run {
            setText(boxText)
            preceding(end).coerceAtLeast(0)
        }
        boxText = boxText.substring(0, start)
        showBoxInput()
        scheduleBoxTranslation()
    }

    /** The end of a long text is what the user is typing, so that is the part kept in view. */
    private fun showBoxInput() {
        boxInput.text = if (boxText.length > BOX_VISIBLE_CHARS) "…" + boxText.takeLast(BOX_VISIBLE_CHARS) else boxText
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
        toolbarTimer.removeCallbacksAndMessages(null)
        boxTimer.removeCallbacksAndMessages(null)
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
        val rowHeight = keyRowHeight()
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
                    textSize = 13f
                    maxLines = 1
                    setPadding(dp(4), 0, dp(4), 0)
                    // Blank choices only keep the last row's buttons the same width as the others.
                    if (label.isEmpty()) visibility = INVISIBLE
                    if (selected) {
                        setTextColor(Color.WHITE)
                        (background as GradientDrawable).setColor(theme.accent)
                    }
                }, LinearLayout.LayoutParams(0, dp(44), 1f).withMargins())
            }
        }

        panel.addView(caption("Terjemahkan ke"))
        val languages = Language.entries
        // Four languages to a row, so each name still fits.
        languages.chunked(LANGUAGES_PER_ROW).forEachIndexed { row, chunk ->
            val labels = chunk.map { "${it.flag} ${it.displayName}" to (it == pickedLanguage) }
            panel.addView(choiceRow(labels + List(LANGUAGES_PER_ROW - chunk.size) { "" to false }) { index ->
                languages.getOrNull(row * LANGUAGES_PER_ROW + index)?.let { listener.onPickLanguage(it) }
            })
        }
        panel.addView(caption("Gaya bahasa"))
        val styles = TranslationStyle.entries
        panel.addView(choiceRow(styles.map { "${it.emoji} ${it.displayName}" to (it == pickedStyle) }) {
            listener.onPickStyle(styles[it])
        })
        // Also turned on and off by holding Terjemahkan; here it can be found without knowing that.
        panel.addView(caption("⚡ Terjemahan langsung saat mengetik (masuk ke kolom chat)"))
        panel.addView(choiceRow(listOf("Mati" to !liveTranslate, "⚡ Nyala" to liveTranslate)) { index ->
            if ((index == 1) != liveTranslate) listener.onToggleLiveTranslate()
            this@KeyboardView.panel = Panel.KEYS
            render()
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
        val rowHeight = keyRowHeight()
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
                    panel = Panel.KEYS
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
        val rowHeight = keyRowHeight()
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

    private fun isSpecial(key: Key) = key !is Key.Char || key.value in listOf(",", ".", "@")

    /** The height panels get in place of the letter rows, minus [header] above them. */
    private fun panelHeight(header: Int): Int {
        val letterRows = KeyboardLayouts.letters(settings.layout, settings.numberRow).size
        return (keyRowHeight() * (letterRows - 1) - header).coerceAtLeast(dp(120))
    }

    private fun bottomKeys() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        listOf(Key.Letters, Key.Space, Key.Delete).forEach { addView(createKey(it)) }
    }

    /** Letter styles: picking one types everything in it until Normal is picked again. */
    private fun renderFonts() {
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(backToTools(), wrapParams())
            addView(caption("Gaya huruf · berlaku sampai kamu pilih Normal lagi"), LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(TOOL_HEADER_DP)))
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        FancyFont.entries.chunked(2).forEach { pair ->
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                pair.forEach { font ->
                    addView(toolbarButton {
                        fancyFont = font
                        panel = Panel.KEYS
                        render()
                    }.apply {
                        text = font.sample
                        textSize = 16f
                        if (font == fancyFont) {
                            setTextColor(Color.WHITE)
                            (background as GradientDrawable).setColor(theme.accent)
                        }
                    }, LinearLayout.LayoutParams(0, dp(44), 1f).withMargins())
                }
                if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, dp(44), 1f))
            })
        }
        addShrinkable(ScrollView(context).apply { addView(grid) }, panelHeight(dp(TOOL_HEADER_DP)))
        addShrinkable(bottomKeys(), keyRowHeight())
    }

    /** The field's text or cursor changed: keeps the 🔤 counts up to date. */
    fun onFieldChanged() {
        if (panel == Panel.TEXT) render()
    }

    /** Changes the case or spacing of everything in the field, and counts its characters and words. */
    private fun renderTextTools() {
        val stats = TextTools.stats(listener.fieldText())
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(backToTools(), wrapParams())
            addView(caption("${stats.characters} karakter · ${stats.words} kata · ${stats.lines} baris"),
                LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(TOOL_HEADER_DP)))
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        TextTools.Action.entries.chunked(2).forEach { pair ->
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                pair.forEach { action ->
                    addView(toolbarButton { listener.onTextTool(action) }.apply {
                        text = action.label
                        textSize = 14f
                    }, LinearLayout.LayoutParams(0, dp(44), 1f).withMargins())
                }
                if (pair.size == 1) addView(View(context), LinearLayout.LayoutParams(0, dp(44), 1f))
            })
        }
        grid.addView(note("Berlaku ke semua tulisan di kolom. Salah pilih? Tekan ↶ buat balikin."))
        addShrinkable(ScrollView(context).apply { addView(grid) }, panelHeight(dp(TOOL_HEADER_DP)))
        addShrinkable(bottomKeys(), keyRowHeight())
    }

    /** Text faces like (◕‿◕), grouped by feeling. */
    private fun renderKaomoji() {
        val tabs = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        Kaomoji.categories.forEachIndexed { index, (name, _) ->
            tabs.addView(TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                text = name
                setTextColor(if (index == kaomojiCategory) Color.WHITE else theme.text)
                background = rounded(if (index == kaomojiCategory) theme.accent else theme.specialKey)
                setOnClickListener {
                    kaomojiCategory = index
                    render()
                }
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).withMargins())
        }
        content.addView(tabs, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))

        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        Kaomoji.categories[kaomojiCategory].second.chunked(3).forEach { line ->
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                for (i in 0 until 3) {
                    val face = line.getOrNull(i)
                    addView(TextView(context).apply {
                        gravity = Gravity.CENTER
                        textSize = 15f
                        setTextColor(theme.text)
                        text = face ?: ""
                        if (face != null) {
                            background = rounded(seeThrough(theme.key))
                            setOnClickListener {
                                tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                                listener.onText(face)
                            }
                        }
                    }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).withMargins().apply { height = dp(44) })
                }
            })
        }
        addShrinkable(ScrollView(context).apply { addView(grid) }, panelHeight(dp(40)))
        addShrinkable(bottomKeys(), keyRowHeight())
    }

    /** AI-written lines to send: pick a mood (and optionally type a topic first), then tap a line. */
    private fun renderLines() {
        val moods = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        LineMood.entries.chunked(3).forEach { row ->
            moods.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                row.forEach { mood ->
                    addView(toolbarButton {
                        linesMood = mood
                        lines = null
                        render()
                        listener.onWriteLines(mood, linesLanguage)
                    }.apply {
                        text = mood.label
                        textSize = 13f
                        setPadding(dp(4), 0, dp(4), 0)
                        if (mood == linesMood) {
                            setTextColor(Color.WHITE)
                            (background as GradientDrawable).setColor(theme.accent)
                        }
                    }, LinearLayout.LayoutParams(0, dp(38), 1f).withMargins())
                }
            })
        }
        content.addView(moods)

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val mood = linesMood
        when {
            mood == null -> list.addView(note("Pilih gaya di atas. Mau lebih pas? Ketik dulu untuk siapa atau topiknya (misal \"pacar suka kopi\"), lalu pilih gaya."))
            lines == null -> list.addView(note("Lagi mikir kata-kata ${mood.label}…"))
            lines.isNullOrEmpty() -> list.addView(note(linesTitle))
            else -> addReplyItems(list, lines.orEmpty()) { listener.onLinePicked(it) }
        }
        addShrinkable(ScrollView(context).apply { addView(list) }, panelHeight(dp(88)))

        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(createKey(Key.Letters))
        bottom.addView(toolbarButton {
            // Steps through the languages the lines are written in.
            val all = Language.entries
            linesLanguage = all[(all.indexOf(linesLanguage) + 1) % all.size]
            render()
        }.apply { text = "${linesLanguage.flag} ${linesLanguage.displayName}" },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 2f).withMargins())
        bottom.addView(toolbarButton {
            val current = linesMood ?: return@toolbarButton
            lines = null
            render()
            listener.onWriteLines(current, linesLanguage)
        }.apply { text = "🔄 Lagi" }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f).withMargins())
        addShrinkable(bottom, keyRowHeight())
    }

    /** Shows the lines written for [mood], or [error] when there are none. Ignored once the user moved on. */
    fun showLines(mood: LineMood, result: List<TextTranslator.Reply>, error: String = "") {
        if (panel != Panel.LINES || mood != linesMood) return
        lines = result
        linesTitle = error
        render()
    }

    /** Back to the keys after a line was sent. */
    fun closeLines() {
        if (panel == Panel.LINES) {
            panel = Panel.KEYS
            render()
        }
    }

    private fun caption(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(theme.secondaryText)
        textSize = 12f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun note(text: String) = TextView(context).apply {
        this.text = text
        setTextColor(theme.secondaryText)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(20), dp(16), dp(20))
    }

    /** Cards for replies or lines: the text, its meaning underneath, and [onPick] on a tap. */
    private fun addReplyItems(list: LinearLayout, items: List<TextTranslator.Reply>, onPick: (String) -> Unit) {
        items.forEach { reply ->
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = rounded(seeThrough(theme.key))
                addView(TextView(context).apply {
                    text = reply.text
                    setTextColor(theme.text)
                    textSize = 16f
                })
                reply.meaning?.let { meaning ->
                    addView(TextView(context).apply {
                        text = meaning
                        setTextColor(theme.secondaryText)
                        textSize = 13f
                    })
                }
                setOnClickListener {
                    tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                    onPick(reply.text)
                }
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).withMargins())
        }
    }

    /** Suggested replies to the copied message: tap one to type it. */
    private fun renderReplies() {
        val rowHeight = keyRowHeight()
        content.addView(TextView(context).apply {
            text = repliesTitle
            setTextColor(theme.secondaryText)
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(40)))

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val current = replies
        if (current.isNullOrEmpty()) {
            list.addView(TextView(context).apply {
                text = if (current == null) "Mencari saran balasan…" else "Tekan 🔄 Lagi untuk mencoba lagi."
                setTextColor(theme.secondaryText)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
            })
        }
        addReplyItems(list, current.orEmpty()) { text ->
            listener.onText(text)
            panel = Panel.KEYS
            render()
        }
        val letterRows = KeyboardLayouts.letters(settings.layout, settings.numberRow).size
        addShrinkable(ScrollView(context).apply { addView(list) }, (rowHeight * (letterRows - 1) - dp(40)).coerceAtLeast(dp(120)))

        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(createKey(Key.Letters))
        repliesLanguage?.let { language ->
            bottom.addView(toolbarButton {
                listener.onPickLanguage(language)
                panel = Panel.KEYS
                render()
            }.apply { text = "✍️ Ketik sendiri" }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 2f).withMargins())
        }
        bottom.addView(toolbarButton { listener.onSuggestReplies() }.apply { text = "🔄 Lagi" },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f).withMargins())
        addShrinkable(bottom, rowHeight)
    }

    /** A small calculator; its result can be typed into the text field. */
    private fun renderCalculator() {
        val rowHeight = keyRowHeight()
        val display = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(theme.key)
            (calcExpressionView.parent as? LinearLayout)?.removeAllViews()
            addView(calcExpressionView.apply {
                setTextColor(theme.text)
                textSize = 20f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.START
            })
            addView(calcResultView.apply {
                setTextColor(theme.secondaryText)
                textSize = 14f
                maxLines = 1
            })
        }
        addShrinkable(display, dp(56))
        updateCalculatorDisplay()

        val rows = listOf(
            listOf("C", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "="),
        )
        // The calculator rows and the bottom row share the height of the letter rows.
        val letterRows = KeyboardLayouts.letters(settings.layout, settings.numberRow).size
        val calcRowHeight = ((rowHeight * letterRows - dp(56)) / (rows.size + 1)).coerceAtLeast(dp(30))
        val margin = (settings.keyGap * density / 2).toInt()
        for (labels in rows) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (label in labels) {
                val special = label.single() !in "0123456789."
                val color = if (label == "=") theme.accent else if (special) theme.specialKey else theme.key
                row.addView(TextView(context).apply {
                    styleKeyText(this)
                    if (label == "=") setTextColor(Color.WHITE)
                    textSize = settings.textSize.toFloat()
                    text = label
                    background = keyBackground(color).first
                    setOnClickListener {
                        tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                        onCalculatorKey(label)
                    }
                }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(margin, margin, margin, margin) })
            }
            addShrinkable(row, calcRowHeight)
        }

        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(backToTools(), LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins())
        bottom.addView(createKey(Key.Letters))
        bottom.addView(toolbarButton {
            val value = Calculator.evaluate(calcExpression)?.let(Calculator::format) ?: calcExpression
            if (value.isNotEmpty()) listener.onText(value)
            panel = Panel.KEYS
            render()
        }.apply {
            text = "Ketik hasilnya"
            setTextColor(Color.WHITE)
            (background as GradientDrawable).setColor(theme.accent)
        }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 3f).withMargins())
        addShrinkable(bottom, calcRowHeight)
    }

    private fun onCalculatorKey(label: String) {
        calcExpression = when (label) {
            "C" -> ""
            "⌫" -> calcExpression.dropLast(1)
            "=" -> Calculator.evaluate(calcExpression)?.let(Calculator::format) ?: calcExpression
            else -> calcExpression + label
        }
        updateCalculatorDisplay()
    }

    private fun updateCalculatorDisplay() {
        calcExpressionView.text = calcExpression.ifEmpty { "0" }
        val result = Calculator.evaluate(calcExpression)?.let(Calculator::format)
        calcResultView.text = if (result != null && result != calcExpression) "= $result" else ""
    }

    // ---- 🧰 Tools: one toolbar button holding small apps ----

    /** The apps switched on in the settings, with their icon and name. */
    private fun toolApps() = listOfNotNull(
        if (settings.appCalculator) Triple(Panel.CALCULATOR, "🧮", "Kalkulator") else null,
        if (settings.appConverter) Triple(Panel.CONVERTER, "💱", "Kurs & satuan") else null,
        if (settings.appClock) Triple(Panel.CLOCK, "🕐", "Zona waktu") else null,
        if (settings.appCalendar) Triple(Panel.CALENDAR, "📅", "Kalender") else null,
        if (settings.appFonts) Triple(Panel.FONTS, "𝓐", "Gaya huruf") else null,
        if (settings.appText) Triple(Panel.TEXT, "🔤", "Rapikan teks") else null,
    )

    private fun openTool(target: Panel) {
        panel = target
        if (target == Panel.CONVERTER) refreshRates()
        render()
    }

    /** The menu of apps, as big tiles. */
    private fun renderTools() {
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        toolApps().chunked(4).forEach { row ->
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                row.forEach { (target, icon, name) ->
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        background = rounded(seeThrough(theme.key))
                        addView(TextView(context).apply {
                            text = icon
                            textSize = 26f
                            gravity = Gravity.CENTER
                        })
                        addView(TextView(context).apply {
                            text = name
                            textSize = 12f
                            gravity = Gravity.CENTER
                            setTextColor(theme.text)
                            maxLines = 1
                        })
                        setOnClickListener {
                            tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                            openTool(target)
                        }
                    }, LinearLayout.LayoutParams(0, dp(76), 1f).withMargins())
                }
                repeat(4 - row.size) { addView(View(context), LinearLayout.LayoutParams(0, dp(76), 1f)) }
            })
        }
        addShrinkable(ScrollView(context).apply { addView(grid) }, panelHeight(0))
        addShrinkable(bottomKeys(), keyRowHeight())
    }

    private fun backToTools() = toolbarButton {
        panel = Panel.TOOLS
        render()
    }.apply {
        text = "‹"
        textSize = 20f
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun lettersButton() = toolbarButton {
        panel = Panel.KEYS
        render()
    }.apply {
        text = "ABC"
        setPadding(dp(10), 0, dp(10), 0)
    }

    /** The space the letter rows leave for an app below its [TOOL_HEADER_DP] header. */
    private fun toolBodyHeight(): Int {
        val letterRows = KeyboardLayouts.letters(settings.layout, settings.numberRow).size
        return (keyRowHeight() * letterRows - dp(TOOL_HEADER_DP)).coerceAtLeast(dp(150))
    }

    private fun wrapParams() = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).withMargins()

    private fun TextView.highlighted() = apply {
        setTextColor(Color.WHITE)
        (background as GradientDrawable).setColor(theme.accent)
    }

    // ---- 💱 Currency and unit converter ----

    private fun refreshRates() {
        if (rates == null) rates = ExchangeRates.saved(context)
        ExchangeRates.refreshIfStale(context) { fresh ->
            if (fresh != null) rates = fresh
            if (panel == Panel.CONVERTER) render()
        }
    }

    private fun convUnits() = convCategory.units
    private fun convFromUnit() = convUnits()[convFrom[convCategory] ?: 0]
    private fun convToUnit() = convUnits()[convTo[convCategory] ?: if (convFromUnit() == convUnits()[0]) 1 else 0]

    private fun convResult(): Double? {
        val amount = convAmount.toDoubleOrNull() ?: return null
        return UnitConverter.convert(convCategory, amount, convFromUnit(), convToUnit(), rates?.perDollar.orEmpty())
    }

    /** The amount as typed, with the Indonesian decimal comma. */
    private fun convAmountShown() = convAmount.replace('.', ',').ifEmpty { "0" }

    /** Pick a kind of measurement, type an amount and see it converted; one tap types the result. */
    private fun renderConverter() {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(backToTools(), wrapParams())
            val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            UnitConverter.Category.entries.forEach { category ->
                chips.addView(toolbarButton {
                    convCategory = category
                    convPicking = null
                    if (category == UnitConverter.Category.MONEY) refreshRates()
                    render()
                }.apply {
                    text = category.label
                    textSize = 13f
                    setPadding(dp(10), 0, dp(10), 0)
                    if (category == convCategory) highlighted()
                }, wrapParams())
            }
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(chips, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        content.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(TOOL_HEADER_DP)))

        val result = convResult()
        val money = convCategory == UnitConverter.Category.MONEY
        val display = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(seeThrough(theme.key))
            addView(TextView(context).apply {
                text = if (result != null) "${UnitConverter.format(result, convToUnit(), convCategory)} ${convToUnit().code}"
                else if (money) "Butuh internet sekali untuk ambil kurs" else "–"
                setTextColor(if (result != null) theme.accent else theme.secondaryText)
                textSize = if (result != null) 20f else 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.START
            })
            addView(TextView(context).apply {
                val updated = rates?.updatedMillis?.takeIf { money && it > 0 }?.let {
                    val day = java.util.Calendar.getInstance().apply { timeInMillis = it }
                    " · kurs ${day.get(java.util.Calendar.DAY_OF_MONTH)} ${CalendarTools.MONTHS[day.get(java.util.Calendar.MONTH)].take(3)}"
                } ?: ""
                text = "${convAmountShown()} ${convFromUnit().code} (${convFromUnit().name})$updated"
                setTextColor(theme.secondaryText)
                textSize = 12f
                maxLines = 1
            })
        }
        addShrinkable(display, dp(52))

        val bodyHeight = toolBodyHeight() - dp(52)
        val picking = convPicking
        if (picking != null) {
            // Choosing a unit: every unit of this kind as a chip, the current one lit.
            val current = if (picking) convFromUnit() else convToUnit()
            val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            convUnits().withIndex().chunked(3).forEach { row ->
                grid.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    row.forEach { (index, unit) ->
                        addView(toolbarButton {
                            if (picking) convFrom[convCategory] = index else convTo[convCategory] = index
                            convPicking = null
                            render()
                        }.apply {
                            text = "${unit.code} · ${unit.name}"
                            textSize = 13f
                            maxLines = 1
                            setPadding(dp(6), 0, dp(6), 0)
                            if (unit == current) highlighted()
                        }, LinearLayout.LayoutParams(0, dp(40), 1f).withMargins())
                    }
                    repeat(3 - row.size) { addView(View(context), LinearLayout.LayoutParams(0, dp(40), 1f)) }
                })
            }
            addShrinkable(ScrollView(context).apply { addView(grid) }, bodyHeight)
            return
        }

        val rows = listOf(
            listOf("7", "8", "9", "⌫", "from"),
            listOf("4", "5", "6", "C", "swap"),
            listOf("1", "2", "3", "000", "to"),
            listOf("ABC", "0", ",", "type"),
        )
        val rowHeight = bodyHeight / rows.size
        val margin = (settings.keyGap * density / 2).toInt()
        for (labels in rows) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (label in labels) {
                val view = TextView(context).apply {
                    styleKeyText(this)
                    textSize = if (label.length == 1) settings.textSize.toFloat() else 14f
                    text = when (label) {
                        "from" -> "${convFromUnit().code} ▾"
                        "to" -> "→ ${convToUnit().code} ▾"
                        "swap" -> "⇅ Tukar"
                        "type" -> "Ketik hasilnya"
                        else -> label
                    }
                    val color = when {
                        label == "type" -> theme.accent
                        label.all { it.isDigit() } -> theme.key
                        else -> theme.specialKey
                    }
                    if (label == "type") setTextColor(Color.WHITE)
                    background = keyBackground(seeThrough(color)).first
                    setOnClickListener {
                        tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                        onConverterKey(label)
                    }
                }
                val weight = when (label) {
                    "type" -> 2.4f
                    "from", "to", "swap" -> 1.4f
                    else -> 1f
                }
                row.addView(view, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                    setMargins(margin, margin, margin, margin)
                })
            }
            addShrinkable(row, rowHeight)
        }
    }

    private fun onConverterKey(label: String) {
        when (label) {
            "C" -> convAmount = ""
            "⌫" -> convAmount = convAmount.dropLast(1)
            "," -> if ('.' !in convAmount) convAmount = convAmount.ifEmpty { "0" } + "."
            "from" -> convPicking = true
            "to" -> convPicking = false
            "swap" -> {
                val from = convUnits().indexOf(convFromUnit())
                val to = convUnits().indexOf(convToUnit())
                convFrom[convCategory] = to
                convTo[convCategory] = from
            }
            "ABC" -> panel = Panel.KEYS
            "type" -> {
                val result = convResult() ?: return
                listener.onText(UnitConverter.sentence(convCategory, convAmountShown(), convFromUnit(), result, convToUnit()))
                panel = Panel.KEYS
            }
            else -> if (convAmount.length < 15) convAmount = (if (convAmount == "0") "" else convAmount) + label
        }
        render()
    }

    // ---- 🕐 World clock ----

    /** The time in other cities, now or at a time stepped to with the arrows; tapping a city types it. */
    private fun renderClock() {
        val home = java.util.TimeZone.getDefault().id
        val now = System.currentTimeMillis()
        val shown = clockTime ?: now
        fun step(minutes: Int) {
            // Stepping starts from the current quarter hour, so the times stay round.
            val base = clockTime ?: (now - now % (15 * 60_000L))
            clockTime = base + minutes * 60_000L
            render()
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(backToTools(), wrapParams())
            addView(toolbarButton { step(-60) }.apply { text = "−1j"; textSize = 13f; setPadding(dp(7), 0, dp(7), 0) }, wrapParams())
            addView(toolbarButton { step(-15) }.apply { text = "−15"; textSize = 13f; setPadding(dp(7), 0, dp(7), 0) }, wrapParams())
            addView(toolbarButton {
                clockTime = null
                render()
            }.apply {
                // Tapping it goes back to the time now.
                text = "🕐 ${WorldClock.time(shown, home)} ${WorldClock.zoneLabel(home, shown)}"
                setPadding(dp(4), 0, dp(4), 0)
                maxLines = 1
                if (clockTime != null) highlighted()
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).withMargins())
            addView(toolbarButton { step(15) }.apply { text = "+15"; textSize = 13f; setPadding(dp(7), 0, dp(7), 0) }, wrapParams())
            addView(toolbarButton { step(60) }.apply { text = "+1j"; textSize = 13f; setPadding(dp(7), 0, dp(7), 0) }, wrapParams())
            addView(lettersButton(), wrapParams())
        }
        content.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(TOOL_HEADER_DP)))

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        WorldClock.cities.forEach { city ->
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                background = rounded(seeThrough(theme.key))
                addView(TextView(context).apply {
                    text = "${city.flag}  ${city.name}"
                    setTextColor(theme.text)
                    textSize = 15f
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    val day = when (WorldClock.dayShift(shown, home, city.zone)) {
                        1 -> "besok "
                        -1 -> "kemarin "
                        else -> ""
                    }
                    text = (day + WorldClock.difference(shown, home, city.zone)).trim()
                    setTextColor(theme.secondaryText)
                    textSize = 12f
                    setPadding(dp(8), 0, dp(8), 0)
                })
                addView(TextView(context).apply {
                    text = WorldClock.time(shown, city.zone)
                    setTextColor(theme.accent)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                })
                setOnClickListener {
                    tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                    listener.onText(WorldClock.sentence(shown, home, city))
                    panel = Panel.KEYS
                    render()
                }
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)).withMargins())
        }
        addShrinkable(ScrollView(context).apply { addView(list) }, toolBodyHeight())
    }

    // ---- 📅 Calendar ----

    /** A month to pick a day from; the day can be typed, and with permission its events and free time shown. */
    private fun renderCalendar() {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(backToTools(), wrapParams())
            addView(toolbarButton { moveMonth(-1) }.apply { text = "◀" }, wrapParams())
            addView(TextView(context).apply {
                text = "${CalendarTools.MONTHS[calMonth]} $calYear"
                setTextColor(theme.text)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(toolbarButton { moveMonth(1) }.apply { text = "▶" }, wrapParams())
            addView(lettersButton(), wrapParams())
        }
        content.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(TOOL_HEADER_DP)))

        val isThisMonth = calYear == today.get(java.util.Calendar.YEAR) && calMonth == today.get(java.util.Calendar.MONTH)
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val names = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        CalendarTools.SHORT_DAYS.forEachIndexed { index, name ->
            names.addView(TextView(context).apply {
                text = name
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) Color.parseColor("#E5484D") else theme.secondaryText)
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        grid.addView(names, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.8f))
        CalendarTools.monthGrid(calYear, calMonth).chunked(7).forEach { week ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            week.forEachIndexed { index, day ->
                row.addView(TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 13f
                    text = day?.toString() ?: ""
                    val isToday = isThisMonth && day == today.get(java.util.Calendar.DAY_OF_MONTH)
                    setTextColor(if (index == 0) Color.parseColor("#E5484D") else theme.text)
                    if (isToday) typeface = Typeface.DEFAULT_BOLD
                    when {
                        day == calDay -> {
                            setTextColor(Color.WHITE)
                            background = rounded(theme.accent)
                        }
                        isToday -> background = GradientDrawable().apply {
                            cornerRadius = dp(theme.cornerRadiusDp).toFloat()
                            setStroke(dp(1), theme.accent)
                        }
                    }
                    if (day != null) setOnClickListener {
                        tapFeedback(it, AudioManager.FX_KEYPRESS_STANDARD)
                        calDay = day
                        render()
                    }
                }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
            }
            grid.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val side = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(2), dp(4), dp(2))
            val dayName = CalendarTools.dayName(calYear, calMonth, calDay)
            addView(TextView(context).apply {
                text = "$dayName, $calDay ${CalendarTools.MONTHS[calMonth].take(3)}"
                setTextColor(theme.text)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            })
            val showEvents = settings.calendarEvents && DeviceCalendar.allowed(context)
            val events = if (showEvents) DeviceCalendar.eventsOn(context, calYear, calMonth, calDay) else emptyList()
            val agenda = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            when {
                !showEvents -> agenda.addView(TextView(context).apply {
                    text = "Mau lihat jadwal & jam kosong? Nyalakan di ⚙️ › Tools › Kalender."
                    setTextColor(theme.secondaryText)
                    textSize = 12f
                    setOnClickListener { listener.onOpenSettings() }
                })
                events.isEmpty() -> agenda.addView(TextView(context).apply {
                    text = "Tidak ada acara."
                    setTextColor(theme.secondaryText)
                    textSize = 12f
                })
                else -> events.forEach { event ->
                    agenda.addView(TextView(context).apply {
                        val time = if (event.allDay) "Seharian" else "${CalendarTools.clock(event.start)}–${CalendarTools.clock(event.end)}"
                        text = "$time  ${event.title}"
                        setTextColor(theme.text)
                        textSize = 12f
                        maxLines = 2
                        setPadding(0, dp(2), 0, dp(2))
                    })
                }
            }
            addView(ScrollView(context).apply { addView(agenda) }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(toolbarButton {
                listener.onText(CalendarTools.formatDate(calYear, calMonth, calDay))
                panel = Panel.KEYS
                render()
            }.apply {
                text = "Ketik tanggal"
                textSize = 13f
                setPadding(dp(4), 0, dp(4), 0)
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34)).withMargins())
            if (showEvents) addView(toolbarButton {
                val isToday = isThisMonth && calDay == today.get(java.util.Calendar.DAY_OF_MONTH)
                val clock = java.util.Calendar.getInstance()
                val nowMinutes = clock.get(java.util.Calendar.HOUR_OF_DAY) * 60 + clock.get(java.util.Calendar.MINUTE)
                val slots = CalendarTools.freeSlots(events, CalendarTools.dayStart(isToday, nowMinutes), CalendarTools.DAY_END)
                val label = if (isToday) "Hari ini" else "$dayName, $calDay ${CalendarTools.MONTHS[calMonth]}"
                listener.onText(CalendarTools.freeSentence(label, slots))
                panel = Panel.KEYS
                render()
            }.apply {
                text = "Ketik jam kosong"
                textSize = 13f
                setPadding(dp(4), 0, dp(4), 0)
                highlighted()
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34)).withMargins())
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(grid, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.35f))
            addView(side, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addShrinkable(body, toolBodyHeight())
    }

    private fun moveMonth(delta: Int) {
        val months = calYear * 12 + calMonth + delta
        calYear = months / 12
        calMonth = months % 12
        // Keep the same day where the new month has it, e.g. 31 January becomes 28 February.
        val last = java.util.Calendar.getInstance().apply {
            clear()
            set(calYear, calMonth, 1)
        }.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        calDay = calDay.coerceAtMost(last)
        render()
    }

    private fun colorFor(key: Key) = seeThrough(when {
        key == Key.CapsLock && shiftState == ShiftState.LOCKED -> theme.accent
        isSpecial(key) -> theme.specialKey
        else -> theme.key
    })

    /** Over a photo the keys let a little of it show through. */
    private fun seeThrough(color: Int) = if (settings.backgroundImage) ColorUtils.setAlphaComponent(color, 215) else color

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
            if (key == Key.CapsLock) (view.tag as GradientDrawable).setColor(colorFor(key))
        }
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun keyRowHeight() = dp((if (isLandscape()) LANDSCAPE_ROW_DP else ROW_DP) * settings.heightPercent / 100)

    private fun toolbarHeight() = dp(if (isLandscape()) LANDSCAPE_TOOLBAR_DP else TOOLBAR_DP)

    private fun stripHeight() = dp(if (isLandscape()) LANDSCAPE_STRIP_DP else STRIP_DP)

    /**
     * A key's background and its face, the part whose colour changes when pressed. With 3D keys the face
     * sits on a darker edge, so the key looks raised.
     */
    private fun keyBackground(color: Int): Pair<Drawable, GradientDrawable> {
        val face = rounded(color)
        if (!settings.keys3d) return face to face
        val edge = rounded(ColorUtils.blendARGB(color, Color.BLACK, 0.35f))
        return LayerDrawable(arrayOf(edge, face)).apply { setLayerInset(1, 0, 0, 0, dp(KEY_EDGE_DP)) } to face
    }

    /** A key-like label: with 3D keys the text gets a soft shadow and sits on the raised face. */
    private fun styleKeyText(view: TextView) {
        view.gravity = Gravity.CENTER
        view.setTextColor(theme.text)
        if (!settings.keys3d) return
        view.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        view.setShadowLayer(2f, 0f, density, ColorUtils.setAlphaComponent(Color.BLACK, 80))
        view.setPadding(0, 0, 0, dp(KEY_EDGE_DP))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKey(key: Key): TextView {
        val (background, drawable) = keyBackground(colorFor(key))
        // Half the gap on each side gives the chosen space between neighbouring keys. The gap is drawn as an
        // inset rather than a margin, so a tap that lands between two keys still presses the nearer one
        // instead of being lost.
        val margin = (settings.keyGap * density / 2).toInt()
        val view = TextView(context).apply {
            styleKeyText(this)
            this.background = if (margin > 0) InsetDrawable(background, margin) else background
            tag = drawable
            setPadding(paddingLeft + margin, paddingTop + margin, paddingRight + margin, paddingBottom + margin)
        }
        applyLabel(view, key)
        keyViews.add(key to view)
        view.layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight)

        val repeatDelete = object : Runnable {
            override fun run() {
                deleteOne()
                keyHandler.postDelayed(this, 60)
            }
        }
        val showAlternates = Runnable { if (key is Key.Char) openAlternates(view, key) }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    drawable.setColor(ColorUtils.blendARGB(colorFor(key), theme.text, 0.18f))
                    tapFeedback(v, soundFor(key))
                    if (settings.keyEffect != KeyEffect.NONE) {
                        val (left, top) = positionOf(v)
                        effects.play(settings.keyEffect, left + v.width / 2f, top + v.height / 2f, v.height)
                    }
                    when (key) {
                        Key.Delete -> {
                            deleteOne()
                            keyHandler.postDelayed(repeatDelete, settings.longPressMs.toLong())
                        }
                        Key.Space -> {
                            spaceLastX = event.x
                            spaceMoved = false
                        }
                        is Key.Char -> {
                            if (settings.keyPreview) showPreview(v, labelFor(key))
                            if (key.alternates.isNotEmpty() || isLetter(key)) keyHandler.postDelayed(showAlternates, settings.longPressMs.toLong())
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
                            type(alternates[selectedAlternate])
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

    /** What a character key types now: its Shift character (e.g. ! on a PC 1 key) or capital when Shift is on. */
    private fun typedValue(key: Key.Char): String =
        if (shiftState != ShiftState.OFF && !showSymbols) key.shifted ?: key.value.uppercase() else key.value

    private fun isLetter(key: Key.Char) = key.value.length == 1 && key.value[0].isLetter()

    private fun openAlternates(key: View, k: Key.Char) {
        val upper = shiftState != ShiftState.OFF && !showSymbols
        alternates = if (isLetter(k) && !showSymbols) {
            // A letter offers the other case first (a while A is showing, A while a is), then its symbol or
            // digit, then its accented forms in the case that is showing.
            val other = if (upper) k.value.lowercase() else k.value.uppercase()
            val accents = k.alternates.filter { it.isLetter() }.map { if (upper) it.uppercase() else it.toString() }
            val symbols = k.alternates.filterNot { it.isLetter() }.map { it.toString() }
            (listOf(other) + symbols + accents).distinct()
        } else {
            (listOf(typedValue(k)) + k.alternates.map { if (upper) it.uppercase() else it.toString() }).distinct()
        }
        selectedAlternate = if (isLetter(k) && !showSymbols) 0 else 1.coerceAtMost(alternates.size - 1)
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
        is Key.Char -> typedValue(key)
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
        Key.Escape -> "Esc"
        is Key.Function -> "F${key.number}"
    }

    private fun onKey(key: Key) {
        when (key) {
            is Key.Char -> {
                type(typedValue(key))
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
            Key.Delete -> deleteOne()
            Key.Escape -> listener.onKeyCode(KeyEvent.KEYCODE_ESCAPE)
            is Key.Function -> listener.onKeyCode(KeyEvent.KEYCODE_F1 + key.number - 1)
            Key.Symbols -> { showSymbols = true; render() }
            Key.Letters -> {
                showSymbols = false
                // In the translate box ABC only leaves the symbols; elsewhere it goes back to the keys.
                if (panel != Panel.TRANSLATOR) panel = Panel.KEYS
                render()
            }
            Key.Emoji -> { panel = Panel.EMOJI; render() }
            Key.Space -> type(" ")
            Key.Enter -> when {
                panel == Panel.TRANSLATOR -> scheduleBoxTranslation(0)
                else -> {
                    // A sent message or a new line starts a new live line; what's in the field stays.
                    if (liveTranslate) clearLiveText()
                    listener.onEnter()
                }
            }
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
        if (!settings.sound) return
        val big = sound != AudioManager.FX_KEYPRESS_STANDARD
        if (settings.soundStyle == SoundStyle.SYSTEM || !TypingSounds.play(settings.soundStyle, big)) audio?.playSoundEffect(sound, -1f)
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val TOOLBAR_DP = 44
        const val STRIP_DP = 38
        const val ROW_DP = 52
        // Landscape screens are short, so everything is lower there.
        const val LANDSCAPE_TOOLBAR_DP = 38
        const val LANDSCAPE_STRIP_DP = 32
        const val LANDSCAPE_ROW_DP = 40
        const val KEY_EDGE_DP = 3
        const val TOOLBAR_SHOW_MS = 3000L
        const val BOX_DELAY_MS = 700L
        const val BOX_VISIBLE_CHARS = 90
        const val EMOJI_COLUMNS = 8
        const val LANGUAGES_PER_ROW = 4
        const val TOOL_HEADER_DP = 38
        val TOOL_APPS = setOf(Panel.TOOLS, Panel.CALCULATOR, Panel.CONVERTER, Panel.CLOCK, Panel.CALENDAR, Panel.FONTS, Panel.TEXT)
    }
}
