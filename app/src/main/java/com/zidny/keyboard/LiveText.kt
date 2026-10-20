package com.zidny.keyboard

/** What live translation shows in the app's field between translations. Pure, so it can be unit tested. */
object LiveText {

    /**
     * What to show right away for [typed], before its own translation is ready, so the field follows every key
     * press: the last translation, [translation] of [source], plus the letters typed since. After a backspace
     * into the translated part, as many letters go from the end of the translation, until the new one arrives.
     */
    fun provisional(source: String, translation: String, typed: String): String {
        if (source.isEmpty() || typed.isEmpty()) return typed
        if (typed.startsWith(source)) return translation + typed.substring(source.length)
        val common = source.commonPrefixWith(typed).length
        val removed = source.length - common
        return translation.dropLast(removed) + typed.substring(common)
    }

    /** How much of [current] stays when it becomes [wanted]: only the rest is replaced, so the field doesn't flicker. */
    fun keep(current: String, wanted: String): Int = current.commonPrefixWith(wanted).length
}
