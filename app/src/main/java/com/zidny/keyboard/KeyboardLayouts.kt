package com.zidny.keyboard

/** A single key on the keyboard. [weight] is its width relative to a normal letter key. */
sealed class Key(val weight: Float = 1f) {
    class Char(val value: String, weight: Float = 1f) : Key(weight)
    object Shift : Key(1.5f)
    object Delete : Key(1.5f)
    object Symbols : Key(1.5f)
    object Letters : Key(1.5f)
    object Space : Key(4f)
    object Enter : Key(1.5f)
}

object KeyboardLayouts {

    private fun row(chars: String): List<Key> = chars.map { Key.Char(it.toString()) }

    val letters: List<List<Key>> = listOf(
        row("1234567890"),
        row("qwertyuiop"),
        row("asdfghjkl"),
        listOf<Key>(Key.Shift) + row("zxcvbnm") + Key.Delete,
        listOf<Key>(Key.Symbols, Key.Char(","), Key.Space, Key.Char("."), Key.Enter),
    )

    val symbols: List<List<Key>> = listOf(
        row("1234567890"),
        row("@#$%&-+()/"),
        row("*\"':;!?_="),
        row("~[]{}<>\\|") + Key.Delete,
        listOf<Key>(Key.Letters, Key.Char(","), Key.Space, Key.Char("."), Key.Enter),
    )
}
