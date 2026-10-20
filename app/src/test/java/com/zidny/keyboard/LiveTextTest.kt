package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTextTest {

    @Test
    fun showsWhatWasTypedBeforeAnyTranslation() {
        assertEquals("aku", LiveText.provisional("", "", "aku"))
    }

    @Test
    fun addsNewLettersAfterTheLastTranslation() {
        assertEquals("I want m", LiveText.provisional("aku mau", "I want", "aku mau m"))
    }

    @Test
    fun backspaceRemovesLettersAtOnce() {
        assertEquals("I want to ea", LiveText.provisional("aku mau makan", "I want to eat", "aku mau maka"))
        assertEquals("I want ", LiveText.provisional("aku mau m", "I want m", "aku mau "))
    }

    @Test
    fun emptyLineEmptiesTheField() {
        assertEquals("", LiveText.provisional("aku", "I", ""))
    }

    @Test
    fun keepsTheSharedStartOfTheField() {
        assertEquals(7, LiveText.keep("I want m", "I want to"))
        assertEquals(0, LiveText.keep("hi", "yo"))
    }
}
