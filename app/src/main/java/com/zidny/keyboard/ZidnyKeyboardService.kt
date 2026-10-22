package com.zidny.keyboard

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File
import kotlin.concurrent.thread

/** The keyboard service: turns key presses from [KeyboardView] into edits in the focused text field, and runs translations. */
class ZidnyKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var keyboardView: KeyboardView? = null
    private val translator by lazy { TextTranslator(this) }
    private lateinit var target: Language
    private lateinit var style: TranslationStyle
    private var settings = KeyboardSettings()

    /** The user's abbreviations, reloaded each time the keyboard opens so edits in the settings apply. */
    private var shortcuts = TextShortcuts(emptyList())

    /**
     * Translating while typing, turned on and off by holding Terjemahkan and remembered between uses. The keys
     * then type into the keyboard's own line, and the app's field shows the translation: [livePrefix] is what
     * was in the field before, followed by the translation, and [liveWritten] is what this put there last.
     */
    private var liveTranslate = false
    private val liveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var livePrefix = ""
    private var liveWritten: String? = null
    /** The newest translation that came back, [liveTranslation] of [liveSource], which each key press builds on. */
    private var liveSource = ""
    private var liveTranslation = ""
    /** One translation runs at a time; when typing moved on meanwhile, the newest text goes next. */
    private var liveBusy = false
    private var liveAgain = false
    /** Bumped when a new message starts, so a translation of the previous one that comes back late is dropped. */
    private var liveGeneration = 0
    private val runLiveTranslate = Runnable { translateLive() }

    private fun translateLive() {
        val text = keyboardView?.liveText ?: return
        if (text.isBlank()) return
        if (liveBusy) {
            liveAgain = true
            return
        }
        liveBusy = true
        val generation = liveGeneration
        translator.translateQuick(text, target) { result ->
            liveBusy = false
            if (!liveTranslate || generation != liveGeneration) return@translateQuick
            liveSource = text
            liveTranslation = when (result) {
                is TextTranslator.Quick.Done -> result.text
                else -> text
            }
            // Until the models are on the phone the field shows what was typed, and the status says why.
            when (result) {
                TextTranslator.Quick.Downloading ->
                    keyboardView?.setStatus("⏬ Mengunduh paket bahasa ${target.displayName} (±30 MB, sekali aja)… tunggu bentar")
                TextTranslator.Quick.DownloadFailed ->
                    keyboardView?.setStatus("⚠️ Paket bahasa gagal diunduh, cek internet. Ketik lagi untuk coba ulang")
                TextTranslator.Quick.Off ->
                    keyboardView?.setStatus("⚡ Nyalakan Terjemahan kilat offline di ⚙️ biar terjemahan langsung jalan")
                else -> {}
            }
            showLive()
            if (liveAgain || keyboardView?.liveText != text) {
                liveAgain = false
                translateLive()
            }
        }
    }

    /** Shows the live line in the field: its translation when ready, otherwise the last one plus what was typed since. */
    private fun showLive() {
        val typed = keyboardView?.liveText ?: return
        writeLive(LiveText.provisional(liveSource, liveTranslation, typed))
    }

    private fun resetLive() {
        liveHandler.removeCallbacks(runLiveTranslate)
        liveWritten = null
        livePrefix = ""
        liveSource = ""
        liveTranslation = ""
        liveAgain = false
        liveGeneration++
    }

    /** What was typed as the topic for the 🎭 lines, replaced by the line the user picks. */
    private var lineTopic = ""

    /** Loaded in the background when the service starts; null until then. */
    @Volatile private var predictor: WordPredictor? = null

    /** The last automatic correction, so an immediate backspace can undo it. */
    private var lastCorrection: Correction? = null

    /** The text before and after the last translation, for the undo button. */
    private var lastTranslation: Pair<String, String>? = null

    private var lastClipText: String? = null
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipHistory by lazy { ClipboardHistory(File(filesDir, CLIPBOARD_FILE)) }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        // Android only lets the keyboard read the clipboard while it is open.
        if (isInputViewShown) {
            rememberClip()
            offerRecentClip()
            translateClipboard()
        }
    }

    /** Something the user just copied, offered as a one-tap paste above the keys. */
    private sealed class RecentClip(val key: String) {
        class Text(val text: String) : RecentClip("text:$text")
        class Image(val uri: Uri, val mimeType: String) : RecentClip("image:$uri")
    }

    private var recentClip: RecentClip? = null

    /** Clips already pasted or dismissed from the chip, so they aren't offered again. */
    private var spentClipKey: String? = null

    private var recognizer: SpeechRecognizer? = null

    /** Applies changes made on the settings screen at once, even while the keyboard is open. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (keyboardView == null || KeyboardSettings.load(this) == settings) return@OnSharedPreferenceChangeListener
        setInputView(onCreateInputView())
        keyboardView?.enterLabel = enterLabelFor(currentInputEditorInfo)
    }

    private data class Correction(val original: String, val corrected: String, val separator: String)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        target = Language.loadTarget(this)
        style = Language.loadStyle(this)
        settings = KeyboardSettings.load(this)
        liveTranslate = getSharedPreferences(KeyboardSettings.PREFS, MODE_PRIVATE).getBoolean(KEY_LIVE, false)
        // Once a language's offline models arrive, the live line is translated without typing again.
        translator.onOfflineReady = {
            if (liveTranslate && !keyboardView?.liveText.isNullOrBlank()) {
                keyboardView?.setStatus("✅ Paket bahasa siap")
                liveHandler.removeCallbacks(runLiveTranslate)
                liveHandler.post(runLiveTranslate)
            }
        }
        clipboard?.addPrimaryClipChangedListener(clipListener)
        getSharedPreferences(KeyboardSettings.PREFS, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(settingsListener)
        thread(name = "load-words") {
            val loaded = WordPredictor(listOf(readWords("words_id.txt"), readWords("words_en.txt")))
            loaded.addUserWords(readUserWords())
            predictor = loaded
        }
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        getSharedPreferences(KeyboardSettings.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(settingsListener)
        recognizer?.destroy()
        translator.close()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        settings = KeyboardSettings.load(this)
        return KeyboardView(this, this, settings).also {
            keyboardView = it
            it.setTargetLanguage(target, style)
            it.setLiveTranslate(liveTranslate)
            it.pickerHint = pickerHint()
            it.setStatus("Ketik, lalu tekan Terjemahkan")
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Rebuild the keys if the user changed the settings, or the automatic theme turned day or night.
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (KeyboardSettings.load(this) != settings || keyboardView?.themeInUse != settings.themeAt(hour)) {
            setInputView(onCreateInputView())
        }
        shortcuts = TextShortcuts.decode(getSharedPreferences(TextShortcuts.PREFS, MODE_PRIVATE).getString(TextShortcuts.KEY, null))
        keyboardView?.enterLabel = enterLabelFor(info)
        lastCorrection = null
        restoreAppLanguage(info.packageName)
        refreshTypingState()
        rememberClip()
        offerRecentClip()
        translateClipboard()
        keyboardView?.showToolbarBriefly()
        translator.offlineEnabled = settings.offlineTranslate
        translator.warmUp(target)
    }

    /** Label for the enter key, e.g. "Kirim" when the app wants a send action. */
    private fun enterLabelFor(info: EditorInfo?) = when ((info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_SEND -> "Kirim"
        EditorInfo.IME_ACTION_SEARCH -> "Cari"
        EditorInfo.IME_ACTION_GO -> "Buka"
        EditorInfo.IME_ACTION_NEXT -> "Lanjut"
        EditorInfo.IME_ACTION_DONE -> "Selesai"
        else -> "⏎"
    }

    /**
     * Asks autofill services (Samsung Pass, Google) to show their suggestions in the keyboard's suggestion bar.
     * Otherwise they open their own pop-up over the app, which takes the focus and closes the keyboard.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest {
        val density = resources.displayMetrics.density
        val height = (INLINE_HEIGHT_DP * density).toInt()
        val style = UiVersions.newStylesBuilder().addStyle(InlineSuggestionUi.newStyleBuilder().build()).build()
        val spec = InlinePresentationSpec.Builder(
            Size((INLINE_MIN_WIDTH_DP * density).toInt(), height),
            Size(resources.displayMetrics.widthPixels, height),
        ).setStyle(style).build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            keyboardView?.setInlineSuggestions(emptyList())
            return true
        }
        val views = arrayOfNulls<View>(suggestions.size)
        var left = suggestions.size
        val size = Size(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, (INLINE_HEIGHT_DP * resources.displayMetrics.density).toInt())
        suggestions.forEachIndexed { i, suggestion ->
            suggestion.inflate(this, size, mainExecutor) { view ->
                views[i] = view
                left--
                if (left == 0) keyboardView?.setInlineSuggestions(views.filterNotNull())
            }
        }
        return true
    }

    /**
     * Android shows a full-screen text box instead of the app in landscape by default. Keep the app visible
     * with the keyboard under it, like Gboard and Samsung do.
     */
    override fun onEvaluateFullscreenMode() = false

    override fun onOpenSettings() {
        requestHideSelf(0)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Suggests replies, in the sender's language, to the message the user copied from the chat. */
    override fun onSuggestReplies() {
        val view = keyboardView ?: return
        val message = copiedText()
        if (message == null) {
            view.showMessage(
                "💬", "Saran balasan",
                "Salin (copy) pesan yang kamu terima di chat, lalu tekan 💬 lagi. Kamu dapat 3 pilihan balasan " +
                    "dalam bahasa pengirimnya, lengkap dengan artinya.",
            )
            return
        }
        val snippet = message.replace(Regex("\\s+"), " ").trim().let { if (it.length > 60) it.take(60) + "…" else it }
        view.showReplies("Saran balasan untuk: “$snippet”", null, null)
        translator.suggestReplies(message, style) { result ->
            // The user may have closed the panel while waiting.
            if (!view.isShowingReplies) return@suggestReplies
            when (result) {
                is TextTranslator.ReplyResult.Success -> view.showReplies(
                    "Balasan dalam ${result.language.displayName} · ${style.displayName} · untuk: “$snippet”",
                    result.replies, result.language,
                )
                is TextTranslator.ReplyResult.Error -> view.showReplies(result.message, emptyList(), null)
            }
        }
    }

    override fun onReadCopied() {
        val view = keyboardView ?: return
        val message = copiedText()
        if (message == null) {
            view.showMessage(
                "🔍", "Cek arti pesan",
                "Tekan lama pesan yang kamu terima di chat, pilih Salin (copy), lalu tekan 🔍. Artinya muncul " +
                    "di sini dalam bahasa Indonesia tanpa dikirim dan tanpa buka aplikasi lain.",
            )
            return
        }
        view.setStatus("Mencari arti pesan…")
        translator.translate(message, Language.INDONESIAN) { result ->
            when (result) {
                is TextTranslator.Result.Success -> showMeaning(
                    view, "🔍", "Artinya (dari ${result.source.flag} ${result.source.displayName}):", result.text,
                )
                is TextTranslator.Result.AlreadyInTarget ->
                    view.showMessage("🔍", "Cek arti pesan", "Pesan yang kamu salin sudah dalam bahasa Indonesia.")
                is TextTranslator.Result.Error -> view.setStatus(result.message)
            }
        }
    }

    /** Shows what a copied message means; with reply suggestions turned on, one tap offers replies to it. */
    private fun showMeaning(view: KeyboardView, icon: String, title: String, meaning: String) {
        if (!settings.replySuggestions) return view.showMessage(icon, title, meaning)
        view.showMessage(icon, title, meaning, "💬 Saran balasan") {
            view.hideMessagePanel()
            onSuggestReplies()
        }
    }

    override fun onTidy() {
        val ic = currentInputConnection ?: return
        val view = keyboardView ?: return
        val original = currentText() ?: return
        if (original.isBlank()) {
            view.setStatus("Ketik dulu kalimatnya, lalu tekan 🪄")
            return
        }
        view.setTranslating(true)
        view.setStatus("Merapikan tulisan…")
        translator.tidy(original, style) { result ->
            view.setTranslating(false)
            when (result) {
                is TextTranslator.Result.Success -> replaceAll(ic, original, result.text, "Dirapikan · ${style.displayName}")
                is TextTranslator.Result.AlreadyInTarget -> view.setStatus("Tulisan sudah rapi")
                is TextTranslator.Result.Error -> view.setStatus(result.message)
            }
        }
    }

    override fun fieldText(): String = currentText() ?: ""

    override fun onTextTool(action: TextTools.Action) {
        val ic = currentInputConnection ?: return
        val view = keyboardView ?: return
        val original = currentText() ?: return
        if (original.isBlank()) return view.setStatus("Ketik dulu tulisannya, lalu pilih 🔤")
        val changed = TextTools.apply(action, original)
        if (changed == original) return view.setStatus("Tulisan sudah begitu")
        replaceAll(ic, original, changed, "🔤 ${action.label.substringAfter("  ")}")
    }

    override fun onWriteLines(mood: LineMood, language: Language) {
        val view = keyboardView ?: return
        // What the user typed before opening the panel tells who or what the lines are about.
        lineTopic = currentText()?.takeIf { it.isNotBlank() && it.length <= MAX_TOPIC_CHARS } ?: ""
        translator.suggestLines(mood, lineTopic, language, style) { result ->
            when (result) {
                is TextTranslator.ReplyResult.Success -> view.showLines(mood, result.replies)
                is TextTranslator.ReplyResult.Error -> view.showLines(mood, emptyList(), result.message)
            }
        }
    }

    override fun onLinePicked(text: String) {
        val ic = currentInputConnection ?: return
        val topic = lineTopic
        if (topic.isNotEmpty() && currentText() == topic) replaceAll(ic, topic, text, "Kata-kata 🎭")
        else onText(text)
        lineTopic = ""
        keyboardView?.closeLines()
    }

    override fun onTranslateBox(text: String, target: Language) {
        val view = keyboardView ?: return
        view.showBoxTranslation(text, "", "Menerjemahkan…")
        translator.translate(text, target, TextTranslator.Options(style = style)) { result ->
            when (result) {
                is TextTranslator.Result.Success -> view.showBoxTranslation(
                    text, result.text, "${result.source.flag} ${result.source.displayName} → ${target.flag} ${target.displayName}",
                )
                // Typed in the target language already: show what it means in Indonesian instead, like
                // Google Translate swapping the languages.
                is TextTranslator.Result.AlreadyInTarget ->
                    if (target == Language.INDONESIAN) {
                        view.showBoxTranslation(text, "", "Teks ini sudah bahasa Indonesia, pilih bahasa tujuan lain")
                    } else {
                        translator.translate(text, Language.INDONESIAN) { back ->
                            when (back) {
                                is TextTranslator.Result.Success -> view.showBoxTranslation(
                                    text, back.text, "${back.source.flag} ${back.source.displayName} → 🇮🇩 Indonesia",
                                )
                                is TextTranslator.Result.AlreadyInTarget -> view.showBoxTranslation(text, "", "")
                                is TextTranslator.Result.Error -> view.showBoxTranslation(text, "", back.message)
                            }
                        }
                    }
                is TextTranslator.Result.Error -> view.showBoxTranslation(text, "", result.message)
            }
        }
    }

    /** The text on the clipboard, if any, unless it is too long to be a chat message. */
    private fun copiedText(): String? {
        val text = try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (e: SecurityException) {
            null
        }
        return text?.takeIf { it.isNotBlank() && it.length <= MAX_CLIP_CHARS }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        recognizer?.cancel()
        keyboardView?.stopKeyRepeat()
        keyboardView?.setListening(false)
        keyboardView?.clearMessage()
        keyboardView?.closePanels()
        resetLive()
        keyboardView?.clearLiveText()
        keyboardView?.setInlineSuggestions(emptyList())
        saveUserWords()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The text or cursor changed (typing, a tap, a swipe on the space bar, or a translation): re-check
        // capitals and suggestions. This runs after every key, so the key handlers don't repeat it.
        refreshTypingState()
        keyboardView?.onFieldChanged()
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        val separator = text.length == 1 && text[0] in WORD_SEPARATORS
        // Reading the field asks the app and can stall when it is busy, so plain letters skip it.
        val before = if (separator) ic.getTextBeforeCursor(WORD_LOOKBACK, 0) ?: "" else ""
        ic.beginBatchEdit()
        // A shortcut like "ttd" becomes its full text; otherwise the word may be corrected. Styled 𝓐 letters
        // are left alone.
        val expansion = if (separator && settings.shortcuts) shortcuts.expand(before) else null
        if (expansion != null) {
            ic.deleteSurroundingText(expansion.first, 0)
            ic.commitText(expansion.second, 1)
            lastCorrection = null
        } else if (separator && keyboardView?.fancyActive != true) {
            finishWord(currentWord(before), text)
        }
        if (text == " " && settings.doubleSpacePeriod && SentenceRules.shouldAddPeriod(before)) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else {
            ic.commitText(text, 1)
        }
        ic.endBatchEdit()
        if (!separator) lastCorrection = null
    }

    /** A word was just ended by [separator]: learn it, or fix it if it looks like a typo. */
    private fun finishWord(word: String, separator: String) {
        lastCorrection = null
        if (word.isEmpty() || !smartTypingAllowed()) return
        val words = predictor ?: return
        val fixed = if (settings.autocorrect) words.correction(word) else null
        if (fixed != null) {
            currentInputConnection?.deleteSurroundingText(word.length, 0)
            currentInputConnection?.commitText(fixed, 1)
            lastCorrection = Correction(word, fixed, separator)
        } else {
            words.learn(word)
        }
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val correction = lastCorrection
        lastCorrection = null
        if (correction != null) {
            // Backspace right after a correction puts back what the user typed, and remembers it.
            val typed = correction.corrected + correction.separator
            if ((ic.getTextBeforeCursor(typed.length, 0) ?: "") == typed) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(typed.length, 0)
                ic.commitText(correction.original + correction.separator, 1)
                ic.endBatchEdit()
                repeat(WordPredictor.LEARNED_AFTER) { predictor?.learn(correction.original) }
                return
            }
        }
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onKeyCode(keyCode: Int) {
        lastCorrection = null
        sendDownUpKeyEvents(keyCode)
    }

    override fun onSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val current = currentWord(ic.getTextBeforeCursor(WORD_LOOKBACK, 0) ?: "")
        lastCorrection = null
        ic.beginBatchEdit()
        ic.deleteSurroundingText(current.length, 0)
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        predictor?.learn(word)
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        lastCorrection = null
        // The keyboard starts a new live line; what is in the field stays and the next one goes after it.
        if (liveTranslate) resetLive()
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    override fun onPickLanguage(language: Language) {
        target = language
        saveChoice()
        keyboardView?.setStatus("Terjemahkan ke ${target.displayName} · ${style.displayName}")
    }

    override fun onPickStyle(style: TranslationStyle) {
        this.style = style
        saveChoice()
        keyboardView?.setStatus("Gaya bahasa: ${style.displayName}")
    }

    /** Remembers the language and style, for this app too when that setting is on, and shows them. */
    private fun saveChoice() {
        Language.saveTarget(this, target)
        Language.saveStyle(this, style)
        val app = currentInputEditorInfo?.packageName
        if (settings.perAppLanguage && app != null) Language.saveForApp(this, app, target, style)
        keyboardView?.setTargetLanguage(target, style)
    }

    /** Switches to the language and style last picked in [app], so e.g. WhatsApp and e-mail can differ. */
    private fun restoreAppLanguage(app: String?) {
        keyboardView?.pickerHint = pickerHint()
        if (!settings.perAppLanguage || app == null) return
        val (language, savedStyle) = Language.loadForApp(this, app) ?: return
        if (language == target && savedStyle == style) return
        target = language
        style = savedStyle
        keyboardView?.setTargetLanguage(target, style)
        keyboardView?.setStatus("Terjemahkan ke ${target.displayName} · ${style.displayName}")
    }

    private fun pickerHint() =
        if (settings.perAppLanguage) "Pilihan ini diingat untuk aplikasi ini. Kalimat yang sudah dalam bahasa tujuan akan dirapikan."
        else "Kalimat yang sudah dalam bahasa tujuan akan dirapikan tata bahasanya."

    /** Translates everything in the text field and replaces it with the result. */
    override fun onTranslate() {
        val ic = currentInputConnection ?: return
        val view = keyboardView ?: return
        val live = view.liveText
        if (liveTranslate && live.isNotBlank()) {
            // The AI's translation of the live line replaces the quick one in the field.
            view.setTranslating(true)
            view.setStatus("AI merapikan terjemahan…")
            translator.translate(live, target, TextTranslator.Options(style = style)) { result ->
                if (result !is TextTranslator.Result.Success || !result.pending) view.setTranslating(false)
                if (view.liveText != live) return@translate
                when (result) {
                    is TextTranslator.Result.Success -> {
                        // Further typing builds on the AI's translation.
                        liveSource = live
                        liveTranslation = result.text
                        writeLive(result.text)
                        if (!result.pending) view.setStatus(if (result.offline) "⚡ Offline · AI tidak terjangkau" else "✨ Dirapikan AI · ${style.displayName}")
                    }
                    is TextTranslator.Result.AlreadyInTarget -> view.setStatus("Sudah dalam bahasa ${result.language.displayName}")
                    is TextTranslator.Result.Error -> view.setStatus(result.message)
                }
            }
            return
        }
        val original = currentText() ?: return
        if (original.isBlank()) {
            view.setStatus("Ketik dulu kalimatnya")
            return
        }
        val goal = target
        val options = TextTranslator.Options(style = style, explain = settings.showMeaning, polishSameLanguage = true)
        view.setTranslating(true)
        view.setStatus("Menerjemahkan ke ${goal.displayName}…")
        // The offline result goes in first; the AI's then replaces it, unless the user edited it meanwhile.
        var shown: String? = null
        translator.translate(original, goal, options) { result ->
            if (result is TextTranslator.Result.Success && result.offline && result.text == shown) {
                // The AI couldn't be reached: the offline result already in the field stays.
                view.setTranslating(false)
                view.setStatus("⚡ Terjemahan offline · AI tidak terjangkau")
                return@translate
            }
            if (!(result is TextTranslator.Result.Success && result.pending)) view.setTranslating(false)
            when (result) {
                is TextTranslator.Result.Success -> {
                    val route = if (result.polished) "${goal.displayName} dirapikan · ${options.style.displayName}"
                    else "${result.source.displayName} → ${goal.displayName} · ${options.style.displayName}"
                    val label = when {
                        result.pending -> "⚡ $route · AI merapikan…"
                        result.offline -> "⚡ $route (offline)"
                        else -> route
                    }
                    val meaning = result.meaning
                    val from = shown ?: original
                    if (shown != null && currentText() != shown) {
                        // The user already edited the offline result, so it's theirs now.
                        view.setStatus(label)
                        return@translate
                    }
                    if (settings.translatePreview) {
                        view.setStatus(label)
                        val body = if (meaning != null) "${result.text}\n\nArtinya: $meaning" else result.text
                        view.showMessage("🌐", "Hasil (${label})", body, "Pakai") {
                            view.hideMessagePanel()
                            val current = shown ?: original
                            if (replaceAll(ic, current, result.text, label, undoTo = original)) shown = result.text
                        }
                    } else if (replaceAll(ic, from, result.text, label, undoTo = original)) {
                        shown = result.text
                        if (meaning != null) {
                            // The full meaning is one tap away on the 💡 chip or the status line.
                            view.setStatus("Artinya: $meaning")
                            view.showMessage("💡", "Artinya dalam bahasa Indonesia", meaning, open = false)
                        }
                    }
                }
                is TextTranslator.Result.AlreadyInTarget ->
                    view.setStatus("Teks sudah dalam bahasa ${result.language.displayName}")
                is TextTranslator.Result.Error -> view.setStatus(result.message)
            }
        }
    }

    /** Replaces the whole field, but only if it still holds [original]. Returns whether it did. */
    private fun replaceAll(
        ic: android.view.inputmethod.InputConnection, original: String, replacement: String, label: String,
        /** What ↶ goes back to, when it isn't [original] (e.g. the user's text under an offline result). */
        undoTo: String = original,
    ): Boolean {
        val view = keyboardView ?: return false
        if (currentInputConnection !== ic || currentText() != original) {
            view.setStatus("Teks berubah, coba lagi")
            return false
        }
        ic.beginBatchEdit()
        ic.setSelection(0, original.length)
        ic.commitText(replacement, 1)
        ic.endBatchEdit()
        lastTranslation = Pair(undoTo, replacement)
        view.setUndoVisible(true)
        view.setStatus(label)
        return true
    }

    override fun onUndoTranslate() {
        val ic = currentInputConnection ?: return
        val view = keyboardView ?: return
        val (original, translated) = lastTranslation ?: return
        view.setUndoVisible(false)
        lastTranslation = null
        if (currentText() != translated) {
            view.setStatus("Teks sudah berubah, tidak bisa dibatalkan")
            return
        }
        ic.beginBatchEdit()
        ic.setSelection(0, translated.length)
        ic.commitText(original, 1)
        ic.endBatchEdit()
        view.setStatus("Terjemahan dibatalkan")
    }

    /** Adds the text on the clipboard to the 📋 history, unless the copying app marked it as sensitive. */
    private fun rememberClip() {
        if (!settings.clipboardHistory) return
        val clip = try {
            clipboard?.primaryClip
        } catch (e: SecurityException) {
            null
        } ?: return
        if (clip.itemCount == 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        ) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        clipHistory.add(text)
    }

    override fun clipboardItems(): List<String> = clipHistory.items

    override fun onClipboardRemove(text: String) = clipHistory.remove(text)

    override fun onClipboardClear() = clipHistory.clear()

    /** Offers the clipboard as a chip above the keys if it was copied in the last few minutes. */
    private fun offerRecentClip() {
        val view = keyboardView ?: return
        recentClip = findRecentClip()?.takeIf { it.key != spentClipKey }
        view.setRecentClip(
            when (val clip = recentClip) {
                is RecentClip.Text -> "📋 " + clip.text.replace(Regex("\\s+"), " ").trim()
                is RecentClip.Image -> "🖼️ Gambar"
                null -> null
            },
        )
    }

    private fun findRecentClip(): RecentClip? {
        if (!settings.clipboardHistory) return null
        val clip = try {
            clipboard?.primaryClip
        } catch (e: SecurityException) {
            null
        } ?: return null
        if (clip.itemCount == 0) return null
        val description = clip.description
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        ) return null
        // Older Android doesn't say when the clip was copied; there it stays offered until used or dismissed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            System.currentTimeMillis() - description.timestamp > RECENT_CLIP_MS
        ) return null
        val item = clip.getItemAt(0)
        val imageType = (0 until description.mimeTypeCount).map { description.getMimeType(it) }
            .firstOrNull { it.startsWith("image/") }
        if (item.uri != null && imageType != null) return RecentClip.Image(item.uri, imageType)
        val text = item.coerceToText(this)?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return RecentClip.Text(text)
    }

    override fun onRecentClip() {
        val clip = recentClip ?: return
        when (clip) {
            is RecentClip.Text -> currentInputConnection?.commitText(clip.text, 1)
            is RecentClip.Image -> pasteImage(clip)
        }
        onRecentClipDismiss()
    }

    override fun onRecentClipDismiss() {
        spentClipKey = recentClip?.key
        recentClip = null
        keyboardView?.setRecentClip(null)
    }

    /**
     * Sends a copied picture to the app being typed in. The picture is copied into the keyboard's own
     * storage first, because the app that copied it only let the keyboard (not other apps) read it.
     */
    private fun pasteImage(clip: RecentClip.Image) {
        val editor = currentInputEditorInfo ?: return
        val accepted = EditorInfoCompat.getContentMimeTypes(editor)
        if (accepted.none { ClipDescription.compareMimeTypes(clip.mimeType, it) }) {
            keyboardView?.setStatus("Aplikasi ini tidak bisa menerima gambar dari keyboard")
            return
        }
        thread(name = "paste-image") {
            val uri = try {
                copyToSharedFile(clip)
            } catch (e: Exception) {
                null
            }
            keyboardView?.post {
                if (uri == null) {
                    keyboardView?.setStatus("Gambar tidak bisa dibaca")
                    return@post
                }
                val ic = currentInputConnection ?: return@post
                val content = InputContentInfoCompat(uri, ClipDescription("Gambar", arrayOf(clip.mimeType)), null)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                } else {
                    grantUriPermission(editor.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    0
                }
                if (!InputConnectionCompat.commitContent(ic, editor, content, flags, null)) {
                    keyboardView?.setStatus("Aplikasi ini tidak bisa menerima gambar dari keyboard")
                }
            }
        }
    }

    private fun copyToSharedFile(clip: RecentClip.Image): Uri {
        val dir = File(cacheDir, PASTED_IMAGES_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val extension = clip.mimeType.substringAfter('/').substringBefore('+').ifEmpty { "png" }
        val file = File(dir, "gambar_${System.currentTimeMillis()}.$extension")
        contentResolver.openInputStream(clip.uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(this, "$packageName.files", file)
    }

    /** When the user copies a message in another language, show its Indonesian meaning above the keys. */
    private fun translateClipboard() {
        if (!settings.clipboardTranslate) return
        val view = keyboardView ?: return
        val text = try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (e: SecurityException) {
            null
        } ?: return
        if (text.isBlank() || text == lastClipText || text.length > MAX_CLIP_CHARS) return
        lastClipText = text
        // Links, numbers and codes are copied all the time and need no translation.
        if ("://" in text || text.count { it.isLetter() } < 4 || ' ' !in text.trim()) return
        translator.translate(text, Language.INDONESIAN) { result ->
            if (result !is TextTranslator.Result.Success) return@translate
            showMeaning(view, "💬", "Pesan yang kamu salin (${result.source.displayName}) artinya:", result.text)
        }
    }

    override fun onVoice() {
        val view = keyboardView ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            view.setStatus("Izinkan mikrofon dulu di aplikasi ZidnyKeyboard")
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_ASK_MICROPHONE, true)
            })
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            view.setStatus("Pengenal suara tidak tersedia di HP ini")
            return
        }
        val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                view.setListening(true)
                view.setStatus("Silakan bicara…")
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    view.setStatus(it)
                }
            }
            override fun onResults(results: Bundle?) {
                view.setListening(false)
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (spoken.isNullOrBlank()) {
                    view.setStatus("Suara tidak terdengar, coba lagi")
                    return
                }
                val ic = currentInputConnection ?: return
                val before = ic.getTextBeforeCursor(1, 0) ?: ""
                val spacer = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
                ic.commitText(spacer + spoken, 1)
                onTranslate()
            }
            override fun onError(error: Int) {
                view.setListening(false)
                view.setStatus("Suara tidak terdengar, coba lagi")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    /** The whole text in the field: before the cursor, the selection, and after the cursor. */
    private fun currentText(): String? {
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(MAX_CHARS, 0) ?: return null
        val selected = ic.getSelectedText(0) ?: ""
        val after = ic.getTextAfterCursor(MAX_CHARS, 0) ?: ""
        return "$before$selected$after"
    }

    /** The letters right before the cursor, i.e. the word being typed. */
    private fun currentWord(before: CharSequence): String = before.takeLastWhile { it.isLetter() || it == '\'' }.toString()

    /** Updates capitals and suggestions from one read of the text before the cursor. */
    private fun refreshTypingState() {
        val view = keyboardView ?: return
        val before = currentInputConnection?.getTextBeforeCursor(WORD_LOOKBACK, 0)
        updateAutoCaps(view, before)
        updateSuggestions(view, before ?: "")
        // The app emptied its field, e.g. the message was sent: the next one starts fresh.
        if (liveTranslate && liveWritten != null && currentText().isNullOrEmpty()) {
            resetLive()
            view.clearLiveText()
        }
    }

    override fun onToggleLiveTranslate() {
        liveTranslate = !liveTranslate
        // Kept outside KeyboardSettings, so switching it doesn't rebuild the keyboard.
        getSharedPreferences(KeyboardSettings.PREFS, MODE_PRIVATE).edit().putBoolean(KEY_LIVE, liveTranslate).apply()
        val view = keyboardView ?: return
        view.setLiveTranslate(liveTranslate)
        view.setStatus(
            if (liveTranslate) "⚡ Terjemahan langsung nyala · tahan Terjemahkan untuk mematikan"
            else "Terjemahan langsung mati",
        )
        resetLive()
        livePrefix = currentText() ?: ""
        if (liveTranslate && !settings.offlineTranslate) {
            view.setStatus("⚡ Nyalakan Terjemahan kilat offline di ⚙️ biar terjemahan langsung bisa jalan")
        }
    }

    override fun onLiveInput(text: String) {
        if (text.isEmpty()) {
            liveSource = ""
            liveTranslation = ""
        }
        // The field follows the key press at once; the real translation follows as soon as it is made.
        showLive()
        liveHandler.removeCallbacks(runLiveTranslate)
        liveHandler.postDelayed(runLiveTranslate, LIVE_DELAY_MS)
    }

    /** Puts [translation] after [livePrefix] in the field, unless the user changed the field some other way. */
    private fun writeLive(translation: String) {
        val ic = currentInputConnection ?: return
        val current = currentText() ?: return
        val expected = liveWritten ?: livePrefix
        if (current != expected) {
            // Edited outside the live line (a paste, an emoji, a tap on a suggestion): keep that as the new start.
            livePrefix = current
        }
        val spacer = if (livePrefix.isEmpty() || livePrefix.last().isWhitespace() || translation.isEmpty()) "" else " "
        val wanted = livePrefix + spacer + translation
        if (wanted == current) return
        // Only the changed end is replaced, so typing and backspace don't make the whole field flicker.
        val keep = LiveText.keep(current, wanted)
        ic.beginBatchEdit()
        ic.setSelection(keep, current.length)
        ic.commitText(wanted.substring(keep), 1)
        ic.endBatchEdit()
        liveWritten = wanted
    }

    private fun updateSuggestions(view: KeyboardView, before: CharSequence) {
        val words = predictor
        // With live translation the keys don't type into the field, so suggestions for it would mislead.
        if (!settings.suggestions || words == null || !smartTypingAllowed() || liveTranslate) {
            view.setSuggestions(emptyList())
            return
        }
        view.setSuggestions(words.suggest(currentWord(before)))
    }

    /** Capitalise the next letter at the start of a sentence, like most keyboards do. */
    private fun updateAutoCaps(view: KeyboardView, before: CharSequence?) {
        if (view.shiftState == KeyboardView.ShiftState.LOCKED) return
        val wanted = if (shouldCapitalise(before)) KeyboardView.ShiftState.ON else KeyboardView.ShiftState.OFF
        if (view.shiftState != wanted) view.shiftState = wanted
    }

    /**
     * Decides from the text itself rather than only the app's hint, since many apps never ask for
     * capitals. Fields where a capital would be wrong (e-mail, web address, password) are left alone.
     */
    private fun shouldCapitalise(before: CharSequence?): Boolean {
        if (!settings.autoCaps || !isPlainTextField() || before == null) return false
        return SentenceRules.atSentenceStart(before)
    }

    /** Suggestions, corrections and learning stay off in e-mail, web address and password fields. */
    private fun smartTypingAllowed() = isPlainTextField()

    private fun isPlainTextField(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return type and InputType.TYPE_MASK_VARIATION !in NO_CAPS_VARIATIONS
    }

    private fun readWords(asset: String): List<String> =
        try {
            assets.open(asset).bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (e: java.io.IOException) {
            emptyList()
        }

    private val userWordsFile get() = File(filesDir, "user_words.txt")

    private fun readUserWords(): Map<String, Int> =
        try {
            userWordsFile.readLines().mapNotNull { line ->
                val parts = line.split('\t')
                parts.getOrNull(1)?.toIntOrNull()?.let { parts[0] to it }
            }.toMap()
        } catch (e: java.io.IOException) {
            emptyMap()
        }

    private fun saveUserWords() {
        val words = predictor?.userWords()?.toMap() ?: return
        thread(name = "save-words") {
            // Keep the file small: only the most used words.
            val top = words.entries.sortedByDescending { it.value }.take(MAX_USER_WORDS)
            userWordsFile.writeText(top.joinToString("\n") { "${it.key}\t${it.value}" })
        }
    }

    private companion object {
        const val KEY_LIVE = "live_translate"
        const val INLINE_HEIGHT_DP = 32
        const val INLINE_MIN_WIDTH_DP = 80
        const val LIVE_DELAY_MS = 40L
                const val MAX_CHARS = 5000
        const val MAX_CLIP_CHARS = 1000
        const val CLIPBOARD_FILE = "clipboard_history.json"
        const val PASTED_IMAGES_DIR = "pasted"
        const val RECENT_CLIP_MS = 3 * 60 * 1000L
        const val MAX_USER_WORDS = 3000
        const val WORD_LOOKBACK = 48
        const val WORD_SEPARATORS = " .,!?;:"
        const val MAX_TOPIC_CHARS = 200
        val NO_CAPS_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
    }
}
