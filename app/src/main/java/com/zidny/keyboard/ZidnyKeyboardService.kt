package com.zidny.keyboard

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import java.io.File
import kotlin.concurrent.thread

/** The keyboard service: turns key presses from [KeyboardView] into edits in the focused text field, and runs translations. */
class ZidnyKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var keyboardView: KeyboardView? = null
    private val translator by lazy { TextTranslator(this) }
    private lateinit var target: Language
    private var settings = KeyboardSettings()

    /** Loaded in the background when the service starts; null until then. */
    @Volatile private var predictor: WordPredictor? = null

    /** The last automatic correction, so an immediate backspace can undo it. */
    private var lastCorrection: Correction? = null

    /** The text before and after the last translation, for the undo button. */
    private var lastTranslation: Pair<String, String>? = null

    private var lastClipText: String? = null
    private val clipboard by lazy { getSystemService(ClipboardManager::class.java) }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (isInputViewShown) translateClipboard()
    }

    private var recognizer: SpeechRecognizer? = null

    private data class Correction(val original: String, val corrected: String, val separator: String)

    override fun onCreate() {
        super.onCreate()
        target = Language.loadTarget(this)
        settings = KeyboardSettings.load(this)
        clipboard?.addPrimaryClipChangedListener(clipListener)
        thread(name = "load-words") {
            val loaded = WordPredictor(listOf(readWords("words_id.txt"), readWords("words_en.txt")))
            loaded.addUserWords(readUserWords())
            predictor = loaded
        }
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        recognizer?.destroy()
        translator.close()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        settings = KeyboardSettings.load(this)
        return KeyboardView(this, this, settings).also {
            keyboardView = it
            it.setTargetLanguage(target)
            it.setStatus("Ketik, lalu tekan Terjemahkan")
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Rebuild the keys if the user changed size or theme in the settings screen.
        if (KeyboardSettings.load(this) != settings) setInputView(onCreateInputView())
        keyboardView?.enterLabel = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Kirim"
            EditorInfo.IME_ACTION_SEARCH -> "Cari"
            EditorInfo.IME_ACTION_GO -> "Buka"
            EditorInfo.IME_ACTION_NEXT -> "Lanjut"
            EditorInfo.IME_ACTION_DONE -> "Selesai"
            else -> "⏎"
        }
        lastCorrection = null
        updateAutoCaps()
        updateSuggestions()
        translateClipboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        recognizer?.cancel()
        keyboardView?.setListening(false)
        keyboardView?.clearMessage()
        saveUserWords()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // The cursor moved (a tap, a swipe on the space bar, or a translation): re-check capitals and suggestions.
        updateAutoCaps()
        updateSuggestions()
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(WORD_LOOKBACK, 0) ?: ""
        ic.beginBatchEdit()
        if (text.length == 1 && text[0] in WORD_SEPARATORS) finishWord(currentWord(before), text)
        if (text == " " && settings.doubleSpacePeriod && SentenceRules.shouldAddPeriod(before)) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else {
            ic.commitText(text, 1)
        }
        ic.endBatchEdit()
        if (text.length != 1 || text[0] !in WORD_SEPARATORS) lastCorrection = null
        updateAutoCaps()
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
        updateAutoCaps()
    }

    override fun onKeyCode(keyCode: Int) {
        lastCorrection = null
        sendDownUpKeyEvents(keyCode)
        updateAutoCaps()
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
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction = info != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        updateAutoCaps()
    }

    override fun onCycleLanguage() {
        target = target.next()
        Language.saveTarget(this, target)
        keyboardView?.setTargetLanguage(target)
        keyboardView?.setStatus("Terjemahkan ke ${target.displayName}")
    }

    /** Translates everything in the text field and replaces it with the result. */
    override fun onTranslate() {
        val ic = currentInputConnection ?: return
        val view = keyboardView ?: return
        val original = currentText() ?: return
        if (original.isBlank()) {
            view.setStatus("Ketik dulu kalimatnya")
            return
        }
        val goal = target
        view.setTranslating(true)
        view.setStatus("Menerjemahkan ke ${goal.displayName}…")
        translator.translate(original, goal) { result ->
            view.setTranslating(false)
            when (result) {
                is TextTranslator.Result.Success -> {
                    val label = "${result.source.displayName} → ${goal.displayName}"
                    if (settings.translatePreview) {
                        view.setStatus(label)
                        view.showMessage("🌐", "Hasil terjemahan ($label)", result.text, "Pakai") {
                            view.hideMessagePanel()
                            replaceAll(ic, original, result.text, label)
                        }
                    } else {
                        replaceAll(ic, original, result.text, label)
                    }
                }
                is TextTranslator.Result.AlreadyInTarget ->
                    view.setStatus("Teks sudah dalam bahasa ${result.language.displayName}")
                is TextTranslator.Result.Error -> view.setStatus(result.message)
            }
        }
    }

    /** Replaces the whole field, but only if it still holds [original]. */
    private fun replaceAll(ic: android.view.inputmethod.InputConnection, original: String, replacement: String, label: String) {
        val view = keyboardView ?: return
        if (currentInputConnection !== ic || currentText() != original) {
            view.setStatus("Teks berubah, coba lagi")
            return
        }
        ic.beginBatchEdit()
        ic.setSelection(0, original.length)
        ic.commitText(replacement, 1)
        ic.endBatchEdit()
        lastTranslation = Pair(original, replacement)
        view.setUndoVisible(true)
        view.setStatus(label)
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
            if (result is TextTranslator.Result.Success) {
                view.showMessage("📋", "Pesan yang kamu salin (${result.source.displayName}) artinya:", result.text)
            }
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

    private fun updateSuggestions() {
        val view = keyboardView ?: return
        val words = predictor
        if (!settings.suggestions || words == null || !smartTypingAllowed()) {
            view.setSuggestions(emptyList())
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(WORD_LOOKBACK, 0) ?: ""
        view.setSuggestions(words.suggest(currentWord(before)))
    }

    /** Capitalise the next letter at the start of a sentence, like most keyboards do. */
    private fun updateAutoCaps() {
        val view = keyboardView ?: return
        if (view.shiftState == KeyboardView.ShiftState.LOCKED) return
        val wanted = if (shouldCapitalise()) KeyboardView.ShiftState.ON else KeyboardView.ShiftState.OFF
        if (view.shiftState != wanted) view.shiftState = wanted
    }

    /**
     * Decides from the text itself rather than only the app's hint, since many apps never ask for
     * capitals. Fields where a capital would be wrong (e-mail, web address, password) are left alone.
     */
    private fun shouldCapitalise(): Boolean {
        if (!settings.autoCaps || !isPlainTextField()) return false
        val before = currentInputConnection?.getTextBeforeCursor(50, 0) ?: return false
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
        const val MAX_CHARS = 5000
        const val MAX_CLIP_CHARS = 1000
        const val MAX_USER_WORDS = 3000
        const val WORD_LOOKBACK = 48
        const val WORD_SEPARATORS = " .,!?;:"
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
