package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageTest {

    @Test
    fun readsTheChoiceSavedForAnApp() {
        assertEquals(Language.JAPANESE to TranslationStyle.FORMAL, Language.parseAppChoice("JAPANESE|FORMAL"))
    }

    @Test
    fun defaultsToCasualWhenTheStyleIsMissingOrUnknown() {
        assertEquals(Language.FRENCH to TranslationStyle.CASUAL, Language.parseAppChoice("FRENCH"))
        assertEquals(Language.FRENCH to TranslationStyle.CASUAL, Language.parseAppChoice("FRENCH|LOUD"))
    }

    @Test
    fun ignoresNothingSavedOrAnUnknownLanguage() {
        assertNull(Language.parseAppChoice(null))
        assertNull(Language.parseAppChoice("KLINGON|CASUAL"))
    }

    @Test
    fun knowsTheNewLanguagesByTheirCodes() {
        assertEquals(Language.FILIPINO, Language.fromCode("tl"))
        assertEquals(Language.FILIPINO, Language.fromCode("fil"))
        assertEquals(Language.KOREAN, Language.fromCode("ko"))
        assertEquals(Language.RUSSIAN, Language.fromCode("ru"))
    }
}
