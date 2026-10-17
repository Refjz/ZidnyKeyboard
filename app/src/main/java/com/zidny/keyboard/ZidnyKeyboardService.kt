package com.zidny.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/** The keyboard service: turns key presses from [KeyboardView] into edits in the focused text field, and runs translations. */
class ZidnyKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var keyboardView: KeyboardView? = null
    private val translator by lazy { TextTranslator() }
    private lateinit var target: Language
    private var settings = KeyboardSettings()

    override fun onCreate() {
        super.onCreate()
        target = Language.loadTarget(this)
    }

    override fun onDestroy() {
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
        updateAutoCaps()
    }

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
        updateAutoCaps()
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoCaps()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
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
        translator.translate(
            original,
            goal,
            onDownloading = { view.setStatus("Mengunduh paket bahasa ${goal.displayName}…") },
        ) { result ->
            view.setTranslating(false)
            when (result) {
                is TextTranslator.Result.Success -> {
                    // Only replace if the user has not changed the text in the meantime.
                    if (currentInputConnection !== ic || currentText() != original) {
                        view.setStatus("Teks berubah, coba lagi")
                        return@translate
                    }
                    ic.beginBatchEdit()
                    ic.setSelection(0, original.length)
                    ic.commitText(result.text, 1)
                    ic.endBatchEdit()
                    view.setStatus("${result.source.displayName} → ${goal.displayName}")
                }
                is TextTranslator.Result.AlreadyInTarget ->
                    view.setStatus("Teks sudah dalam bahasa ${result.language.displayName}")
                is TextTranslator.Result.Error -> view.setStatus(result.message)
            }
        }
    }

    /** The whole text in the field: before the cursor, the selection, and after the cursor. */
    private fun currentText(): String? {
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(MAX_CHARS, 0) ?: return null
        val selected = ic.getSelectedText(0) ?: ""
        val after = ic.getTextAfterCursor(MAX_CHARS, 0) ?: ""
        return "$before$selected$after"
    }

    /** Capitalise the next letter at the start of a sentence, like most keyboards do. */
    private fun updateAutoCaps() {
        val view = keyboardView ?: return
        if (view.shiftState == KeyboardView.ShiftState.LOCKED) return
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        val wanted = if (caps != 0) KeyboardView.ShiftState.ON else KeyboardView.ShiftState.OFF
        if (view.shiftState != wanted) view.shiftState = wanted
    }

    private companion object {
        const val MAX_CHARS = 5000
    }
}
