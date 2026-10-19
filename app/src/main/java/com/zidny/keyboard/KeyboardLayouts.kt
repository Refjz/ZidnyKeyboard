package com.zidny.keyboard

/** A single key on the keyboard. [weight] is its width relative to a normal letter key. */
sealed class Key(val weight: Float = 1f) {
    /**
     * Types [value]. Holding it offers [alternates], such as a digit or accented letters. With Shift on it
     * types [shifted] (like ! on the 1 key of a PC keyboard), or the capital letter when there is none.
     */
    class Char(val value: String, weight: Float = 1f, val alternates: String = "", val shifted: String? = null) : Key(weight)
    object Shift : Key(1.5f)
    object CapsLock : Key(1.5f)
    object Tab : Key(1.5f)
    object Delete : Key(1.5f)
    object Symbols : Key(1.5f)
    object Emoji : Key(1f)
    object Letters : Key(1.5f)
    object Space : Key(4f)
    object Enter : Key(1.5f)
    object Left : Key(1f)
    object Right : Key(1f)
    object Escape : Key(1f)
    /** F1 to F12 on the PC layout. */
    class Function(val number: Int) : Key(1f)
}

/** The letter arrangements the user can pick in the settings. */
enum class KeyboardLayout(val displayName: String) {
    QWERTY("QWERTY"),
    AZERTY("AZERTY (Prancis)"),
    QWERTZ("QWERTZ (Jerman)"),
    DESKTOP("Desktop (seperti keyboard PC)"),
}

object KeyboardLayouts {

    /** Accented letters offered when a letter key is held. */
    private val ACCENTS = mapOf(
        'a' to "àáâäæãåā", 'c' to "çćč", 'e' to "èéêëēę", 'g' to "ğ", 'i' to "îïíīì",
        'l' to "ł", 'n' to "ñń", 'o' to "ôöòóœøōõ", 's' to "ßśš", 'u' to "ûüùúū",
        'y' to "ÿý", 'z' to "žźż",
    )

    /** The symbol each letter offers when held, in the same places as on Gboard and Samsung keyboards. */
    private val LETTER_SYMBOLS = mapOf(
        'a' to "@", 's' to "#", 'd' to "$", 'f' to "%", 'g' to "&", 'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
        'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";", 'n' to "!", 'm' to "?",
    )

    private const val PERIOD_ALTERNATES = "?!,;:'\"-…"

    private fun row(chars: String): List<Key> =
        chars.map { Key.Char(it.toString(), alternates = (LETTER_SYMBOLS[it] ?: "") + (ACCENTS[it] ?: "")) }

    /** The top letter row: holding a key also offers the digit above it, like on Samsung keyboards. */
    private fun topRow(chars: String): List<Key> = chars.mapIndexed { i, c ->
        Key.Char(c.toString(), alternates = "${(i + 1) % 10}" + (ACCENTS[c] ?: ""))
    }

    /** Keys that type the matching character of [shifted] with Shift on, as on a PC keyboard. */
    private fun pcRow(chars: String, shifted: String): List<Key> =
        chars.mapIndexed { i, c -> Key.Char(c.toString(), shifted = shifted[i].toString()) }

    private val numberRow = row("1234567890")

    // @ sits next to the space bar because e-mail addresses are typed so often; emoji are on the toolbar.
    private fun bottomRow(): List<Key> =
        listOf(Key.Symbols, Key.Char("@"), Key.Char(",", alternates = "!?"), Key.Space, Key.Char(".", alternates = PERIOD_ALTERNATES), Key.Enter)

    fun letters(layout: KeyboardLayout, withNumberRow: Boolean): List<List<Key>> {
        val rows = when (layout) {
            KeyboardLayout.QWERTY -> listOf(
                topRow("qwertyuiop"),
                row("asdfghjkl"),
                listOf<Key>(Key.Shift) + row("zxcvbnm") + Key.Delete,
                bottomRow(),
            )
            KeyboardLayout.AZERTY -> listOf(
                topRow("azertyuiop"),
                row("qsdfghjklm"),
                listOf<Key>(Key.Shift) + row("wxcvbn") + Key.Char("'") + Key.Delete,
                bottomRow(),
            )
            KeyboardLayout.QWERTZ -> listOf(
                topRow("qwertzuiop"),
                row("asdfghjkl"),
                listOf<Key>(Key.Shift) + row("yxcvbnm") + Key.Delete,
                bottomRow(),
            )
            // A PC keyboard always shows its number row, so the setting does not apply here.
            KeyboardLayout.DESKTOP -> return listOf(
                listOf<Key>(Key.Escape) + (1..12).map { Key.Function(it) },
                pcRow("`1234567890-=", "~!@#$%^&*()_+"),
                listOf<Key>(Key.Tab) + row("qwertyuiop") + pcRow("[]\\", "{}|"),
                listOf<Key>(Key.CapsLock) + row("asdfghjkl") + pcRow(";'", ":\""),
                listOf<Key>(Key.Shift) + row("zxcvbnm") + pcRow(",./", "<>?") + Key.Delete,
                listOf(Key.Symbols, Key.Char("@"), Key.Left, Key.Space, Key.Right, Key.Enter),
            )
        }
        return if (withNumberRow) listOf(numberRow) + rows else rows
    }

    val symbols: List<List<Key>> = listOf(
        row("1234567890"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/").map {
            Key.Char(it, alternates = when (it) {
                "$" -> "€£¥₹¢"
                "-" -> "_–—"
                "%" -> "‰"
                "(" -> "[{<"
                ")" -> "]}>"
                else -> ""
            })
        },
        row("*\"':;!?_="),
        row("~[]{}<>\\|") + Key.Delete,
        listOf<Key>(Key.Letters, Key.Char(","), Key.Space, Key.Char(".", alternates = PERIOD_ALTERNATES), Key.Enter),
    )
}
