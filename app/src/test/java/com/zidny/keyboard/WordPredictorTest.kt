package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordPredictorTest {

    private val predictor = WordPredictor(
        listOf(
            listOf("aku", "kamu", "sudah", "makan", "kami", "kampus"),
            listOf("you", "the", "make", "other", "kamar"),
        ),
    )

    @Test
    fun suggestsCompletionsMostCommonFirst() {
        assertEquals(listOf("kamu", "kami", "kampus"), predictor.suggest("kam"))
        assertEquals(listOf("sudah"), predictor.suggest("sudha"))
    }

    @Test
    fun keepsTheCaseOfWhatWasTyped() {
        assertEquals("Kamu", predictor.suggest("Kam").first())
    }

    @Test
    fun correctsOneLetterTypos() {
        assertEquals("sudah", predictor.correction("sudha"))
        assertEquals("makan", predictor.correction("maksn"))
        assertNull(predictor.correction("sudah"))
    }

    @Test
    fun learnedWordsAreNotCorrectedAndAreSuggested() {
        val p = WordPredictor(listOf(listOf("makan")))
        assertEquals("makan", p.correction("makam"))
        repeat(WordPredictor.LEARNED_AFTER) { p.learn("makam") }
        assertNull(p.correction("makam"))
        assertTrue("makam" in WordPredictor(listOf(listOf("makan"))).apply { addUserWords(p.userWords()) }.suggest("mak"))
    }

    @Test
    fun remembersWhetherItLearnedSomethingNew() {
        val fresh = WordPredictor(listOf(listOf("aku")))
        fresh.addUserWords(mapOf("bro" to 3))
        assertEquals(false, fresh.changed)
        fresh.learn("x")
        assertEquals(false, fresh.changed)
        fresh.learn("gaskeun")
        assertEquals(true, fresh.changed)
    }
}
