package com.zidny.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView

/**
 * The keyboard itself. For now it only shows a placeholder; the QWERTY layout
 * comes in step 2 and the translate button in step 3.
 */
class ZidnyKeyboardService : InputMethodService() {

    override fun onCreateInputView(): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        return TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 18f
            setPadding(pad, pad, pad, pad)
        }
    }
}
