package com.zidny.keyboard

import com.zidny.keyboard.TextTools.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class TextToolsTest {

    @Test
    fun changesCase() {
        assertEquals("HALO SEMUA", TextTools.apply(Action.UPPER, "halo Semua"))
        assertEquals("halo semua", TextTools.apply(Action.LOWER, "HALO Semua"))
        assertEquals("Budi Santoso (Jakarta)", TextTools.apply(Action.TITLE, "BUDI santoso (jakarta)"))
    }

    @Test
    fun sentenceCaseCapitalizesEachSentence() {
        assertEquals(
            "Halo kak. Ready? Iya ada.\nSiap kirim!",
            TextTools.apply(Action.SENTENCE, "HALO KAK. READY? iya ADA.\nsiap kirim!"),
        )
        assertEquals("Harga 10.000 ya", TextTools.apply(Action.SENTENCE, "harga 10.000 YA"))
    }

    @Test
    fun cleansSpaces() {
        assertEquals(
            "Halo kak, ready?\n\nIya ada.",
            TextTools.apply(Action.SPACES, "  Halo   kak ,  ready ?  \n\n\n\nIya\tada. "),
        )
    }

    @Test
    fun countsCharactersWordsAndLines() {
        assertEquals(TextTools.Stats(0, 0, 0), TextTools.stats(""))
        assertEquals(TextTools.Stats(18, 3, 2), TextTools.stats("halo kak 😊\nsiap - "))
    }
}
