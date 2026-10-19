package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunFeaturesTest {
    @Test
    fun fancyFontsConvertLettersAndKeepTheRest() {
        assertEquals("𝐇𝐢 𝟏!", FancyFont.BOLD.apply("Hi 1!"))
        assertEquals("ⓗⓘ", FancyFont.BUBBLE.apply("hi"))
        assertEquals("ᴋᴀᴋ", FancyFont.SMALL_CAPS.apply("Kak"))
        assertEquals("ℎ𝘪", FancyFont.ITALIC.apply("hi"))
        assertEquals("ｈｉ\u3000ｙａ", FancyFont.WIDE.apply("hi ya"))
        assertEquals("a\u0336 b\u0336", FancyFont.STRIKE.apply("a b"))
        assertEquals("tetap", FancyFont.NORMAL.apply("tetap"))
    }

    @Test
    fun shortcutsExpandTheLastWordIgnoringCase() {
        val shortcuts = TextShortcuts(listOf("ttd" to "Jl. Melati 5, Bandung", "rek" to "BCA 123"))
        assertEquals(3 to "Jl. Melati 5, Bandung", shortcuts.expand("kirim ke TTD"))
        assertNull(shortcuts.expand("kirim ke ttdx"))
        assertNull(shortcuts.expand("ttd "))
    }

    @Test
    fun shortcutsSurviveSaving() {
        val shortcuts = TextShortcuts(listOf("almt" to "Baris 1\nBaris 2 = ok")).with("rek", "BCA 123")
        val loaded = TextShortcuts.decode(shortcuts.encode())
        assertEquals(shortcuts.entries, loaded.entries)
        assertEquals(listOf("rek" to "BCA 123"), loaded.without("almt").entries)
        assertEquals(emptyList<Pair<String, String>>(), TextShortcuts.decode(null).entries)
    }
}
