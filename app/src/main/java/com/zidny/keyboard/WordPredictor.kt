package com.zidny.keyboard

/**
 * Suggests completions and spelling corrections for the word being typed.
 *
 * [lists] are word lists ordered from most to least common; earlier lists win, so the first one
 * (Indonesian) is preferred. Words the user types often are learned and suggested too.
 */
class WordPredictor(lists: List<List<String>>) {

    private val scores = HashMap<String, Double>()
    private val sorted: Array<String>
    private val userCounts = HashMap<String, Int>()

    /** Set when [learn] adds to the user's words, so they are only saved again when something changed. */
    var changed = false

    init {
        lists.forEachIndexed { listIndex, words ->
            val weight = if (listIndex == 0) 1.5 else 1.0
            words.forEachIndexed { rank, word ->
                val score = weight / (rank + 1)
                if (score > (scores[word] ?: 0.0)) scores[word] = score
            }
        }
        sorted = scores.keys.sorted().toTypedArray()
    }

    /** Known from the word lists, or typed by the user often enough to count as theirs. */
    fun isKnown(word: String): Boolean {
        val w = word.lowercase()
        return w in scores || (userCounts[w] ?: 0) >= LEARNED_AFTER
    }

    fun learn(word: String) {
        val w = word.lowercase()
        if (w.length < 2 || w.length > 30) return
        userCounts[w] = (userCounts[w] ?: 0) + 1
        changed = true
    }

    fun userWords(): Map<String, Int> = userCounts

    fun addUserWords(words: Map<String, Int>) {
        userCounts.putAll(words)
    }

    /** Up to [limit] words that start with [prefix], most likely first, in the same case as [prefix]. */
    fun suggest(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val candidates = HashMap<String, Double>()

        var i = lowerBound(p)
        while (i < sorted.size && sorted[i].startsWith(p)) {
            if (sorted[i] != p) candidates[sorted[i]] = scores.getValue(sorted[i])
            i++
        }
        for ((word, count) in userCounts) {
            if (word.startsWith(p) && word != p && count >= LEARNED_AFTER) {
                candidates[word] = maxOf(candidates[word] ?: 0.0, count * USER_WORD_WEIGHT)
            }
        }
        val result = candidates.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        // Nothing starts with what was typed, so it is probably a typo: offer the fix instead.
        if (result.isEmpty()) return listOfNotNull(correction(prefix))
        return result.map { matchCase(it, prefix) }
    }

    /** The most likely intended word when [word] looks like a typo, or null when it looks fine. */
    fun correction(word: String): String? {
        val w = word.lowercase()
        if (w.length < 3 || isKnown(w) || w.any { !it.isLetter() }) return null
        val best = edits(w).filter { it in scores }.maxByOrNull { scores.getValue(it) } ?: return null
        return matchCase(best, word)
    }

    /** Every string one deletion, swap, replacement or insertion away from [w]. */
    private fun edits(w: String): Set<String> {
        val out = HashSet<String>()
        for (i in 0..w.length) {
            val left = w.substring(0, i)
            val right = w.substring(i)
            if (right.isNotEmpty()) out.add(left + right.substring(1))
            if (right.length > 1) out.add(left + right[1] + right[0] + right.substring(2))
            for (c in ALPHABET) {
                if (right.isNotEmpty()) out.add(left + c + right.substring(1))
                out.add(left + c + right)
            }
        }
        out.remove(w)
        return out
    }

    private fun lowerBound(p: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < p) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun matchCase(word: String, typed: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercase() }
        else -> word
    }

    companion object {
        const val LEARNED_AFTER = 2
        private const val USER_WORD_WEIGHT = 0.1
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
    }
}
