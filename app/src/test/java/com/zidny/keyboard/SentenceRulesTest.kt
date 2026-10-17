package com.zidny.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceRulesTest {

    @Test
    fun capitalisesAtStartOfTextAndAfterSentenceEnd() {
        assertTrue(SentenceRules.atSentenceStart(""))
        assertTrue(SentenceRules.atSentenceStart("Halo. "))
        assertTrue(SentenceRules.atSentenceStart("Kamu di mana? "))
        assertTrue(SentenceRules.atSentenceStart("baris satu\n"))
    }

    @Test
    fun doesNotCapitaliseInsideASentence() {
        assertFalse(SentenceRules.atSentenceStart("Halo"))
        assertFalse(SentenceRules.atSentenceStart("Halo "))
        assertFalse(SentenceRules.atSentenceStart("Halo."))
    }

    @Test
    fun doubleSpaceAddsPeriodOnlyAfterAWord() {
        assertTrue(SentenceRules.shouldAddPeriod("Halo "))
        assertFalse(SentenceRules.shouldAddPeriod("Halo. "))
        assertFalse(SentenceRules.shouldAddPeriod(" "))
        assertFalse(SentenceRules.shouldAddPeriod("Halo"))
    }
}
