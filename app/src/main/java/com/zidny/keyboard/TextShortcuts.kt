package com.zidny.keyboard

/**
 * The user's own abbreviations, e.g. "ttd" for a full address: typing one and then a space or punctuation
 * replaces it with its expansion. Matching ignores case.
 */
class TextShortcuts(entries: List<Pair<String, String>>) {

    val entries: List<Pair<String, String>> = entries
        .map { (short, long) -> short.trim() to long }
        .filter { (short, long) -> short.isNotEmpty() && short.none { it.isWhitespace() } && long.isNotEmpty() }
        .distinctBy { it.first.lowercase() }

    private val byShort = this.entries.associate { (short, long) -> short.lowercase() to long }

    /** The expansion for the last word of [before], with that word's length, or null if it is no shortcut. */
    fun expand(before: CharSequence): Pair<Int, String>? {
        val word = before.takeLastWhile { !it.isWhitespace() }.toString()
        if (word.isEmpty()) return null
        return byShort[word.lowercase()]?.let { word.length to it }
    }

    fun with(short: String, long: String) = TextShortcuts(entries.filterNot { it.first.equals(short.trim(), true) } + (short to long))

    fun without(short: String) = TextShortcuts(entries.filterNot { it.first == short })

    /** One line per shortcut, with separator characters no one types. */
    fun encode(): String = entries.joinToString(RECORD) { (short, long) -> short + FIELD + long }

    companion object {
        /** Where the shortcuts are saved: their own preferences file, so editing them doesn't rebuild the keys. */
        const val PREFS = "shortcuts"
        const val KEY = "entries"
        private const val FIELD = "\u001F"
        private const val RECORD = "\u001E"

        fun decode(saved: String?): TextShortcuts = TextShortcuts(
            saved.orEmpty().split(RECORD).mapNotNull { record ->
                val parts = record.split(FIELD, limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            },
        )
    }
}
