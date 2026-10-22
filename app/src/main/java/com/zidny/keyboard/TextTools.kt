package com.zidny.keyboard

import java.util.Locale

/** The 🔤 app's changes to what is in the text field, e.g. "halo SEMUA" → "Halo Semua". Pure, so it can be unit tested. */
object TextTools {

    enum class Action(val label: String) {
        UPPER("ABC  HURUF BESAR"),
        LOWER("abc  huruf kecil"),
        TITLE("Abc  Awal Kapital"),
        SENTENCE("Abc.  Huruf kalimat"),
        SPACES("␣  Hapus spasi dobel"),
    }

    data class Stats(val characters: Int, val words: Int, val lines: Int)

    private val ID = Locale("id")
    private val SPACE_RUN = Regex("[ \\t\\u00A0]+")
    private val SPACE_BEFORE_MARK = Regex(" +([,.!?:;])")
    private val BLANK_LINES = Regex("\\n{3,}")

    fun apply(action: Action, text: String): String = when (action) {
        Action.UPPER -> text.uppercase(ID)
        Action.LOWER -> text.lowercase(ID)
        Action.TITLE -> capitalize(text.lowercase(ID)) { prev -> prev == null || prev.isWhitespace() || prev in "(\"'“‘-/" }
        Action.SENTENCE -> sentenceCase(text)
        Action.SPACES -> cleanSpaces(text)
    }

    fun stats(text: String): Stats = Stats(
        characters = text.codePointCount(0, text.length),
        words = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) },
        lines = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1,
    )

    /** Lower case with a capital at the start and after . ! ? or a new line. */
    private fun sentenceCase(text: String): String {
        val lower = text.lowercase(ID)
        val out = StringBuilder(lower.length)
        var capitalNext = true
        for (c in lower) {
            if (capitalNext && c.isLetter()) {
                out.append(c.titlecaseChar())
                capitalNext = false
            } else {
                out.append(c)
                if (c in ".!?\n") capitalNext = true
                else if (c.isLetterOrDigit()) capitalNext = false
            }
        }
        return out.toString()
    }

    /** One space between words, none at a line's ends or before punctuation, and at most one empty line in a row. */
    private fun cleanSpaces(text: String): String = text
        .lines()
        .joinToString("\n") { it.replace(SPACE_RUN, " ").replace(SPACE_BEFORE_MARK, "$1").trim() }
        .replace(BLANK_LINES, "\n\n")
        .trim()

    private inline fun capitalize(text: String, startsWord: (Char?) -> Boolean): String {
        val out = StringBuilder(text.length)
        text.forEachIndexed { i, c ->
            out.append(if (c.isLetter() && startsWord(text.getOrNull(i - 1))) c.titlecaseChar() else c)
        }
        return out.toString()
    }
}
