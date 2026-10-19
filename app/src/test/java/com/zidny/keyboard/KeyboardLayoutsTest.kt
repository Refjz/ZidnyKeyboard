package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutsTest {
    @Test
    fun desktopLayoutStartsWithEscapeAndFunctionKeys() {
        val top = KeyboardLayouts.letters(KeyboardLayout.DESKTOP, withNumberRow = false).first()
        assertEquals(Key.Escape, top.first())
        assertEquals((1..12).toList(), top.drop(1).map { (it as Key.Function).number })
    }

    @Test
    fun heldLettersOfferTheirSymbol() {
        val rows = KeyboardLayouts.letters(KeyboardLayout.QWERTY, withNumberRow = false)
        val keys = rows.flatten().filterIsInstance<Key.Char>().associateBy { it.value }
        assertTrue(keys.getValue("a").alternates.startsWith("@"))
        assertTrue(keys.getValue("m").alternates.startsWith("?"))
        assertTrue(keys.getValue("q").alternates.startsWith("1"))
    }
}
