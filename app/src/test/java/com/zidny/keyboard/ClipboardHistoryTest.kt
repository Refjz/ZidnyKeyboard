package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ClipboardHistoryTest {

    @Test
    fun newestFirstWithoutDuplicates() {
        val history = ClipboardHistory()
        history.add("satu")
        history.add("dua")
        history.add("satu")
        assertEquals(listOf("satu", "dua"), history.items)
    }

    @Test
    fun ignoresBlankAndRepeatedText() {
        val history = ClipboardHistory()
        history.add("halo")
        assertFalse(history.add("halo"))
        assertFalse(history.add("   "))
        assertEquals(listOf("halo"), history.items)
    }

    @Test
    fun keepsOnlyTheNewestItems() {
        val history = ClipboardHistory()
        repeat(ClipboardHistory.MAX_ITEMS + 5) { history.add("teks $it") }
        assertEquals(ClipboardHistory.MAX_ITEMS, history.items.size)
        assertEquals("teks ${ClipboardHistory.MAX_ITEMS + 4}", history.items.first())
    }

    @Test
    fun removeAndClear() {
        val history = ClipboardHistory()
        history.add("a")
        history.add("b")
        history.remove("a")
        assertEquals(listOf("b"), history.items)
        history.clear()
        assertEquals(emptyList<String>(), history.items)
    }
}
