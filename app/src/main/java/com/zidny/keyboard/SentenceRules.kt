package com.zidny.keyboard

/** Small text rules the keyboard applies while typing. Pure functions so they can be unit tested. */
object SentenceRules {

    /** True when the next letter starts a sentence, given the text before the cursor. */
    fun atSentenceStart(before: CharSequence): Boolean {
        if (before.isEmpty() || before.last() == '\n') return true
        val trimmed = before.trimEnd(' ')
        // Right after a letter or digit: we are in the middle of a word.
        if (trimmed.length == before.length) return false
        return trimmed.isEmpty() || trimmed.last() in ".!?\n"
    }

    /** True when typing a space after [before] should turn the earlier space into ". ". */
    fun shouldAddPeriod(before: CharSequence): Boolean =
        before.length >= 2 && before.last() == ' ' && before[before.length - 2].isLetterOrDigit()
}
