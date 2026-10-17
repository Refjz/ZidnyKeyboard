package com.zidny.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/** The keyboard service: turns key presses from [KeyboardView] into edits in the focused text field. */
class ZidnyKeyboardService : InputMethodService(), KeyboardView.Listener {

    private var keyboardView: KeyboardView? = null

    override fun onCreateInputView(): View =
        KeyboardView(this, this).also { keyboardView = it }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
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

    /** Capitalise the next letter at the start of a sentence, like most keyboards do. */
    private fun updateAutoCaps() {
        val view = keyboardView ?: return
        if (view.shiftState == KeyboardView.ShiftState.LOCKED) return
        val info = currentInputEditorInfo ?: return
        val caps = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
        val wanted = if (caps != 0) KeyboardView.ShiftState.ON else KeyboardView.ShiftState.OFF
        if (view.shiftState != wanted) view.shiftState = wanted
    }
}
