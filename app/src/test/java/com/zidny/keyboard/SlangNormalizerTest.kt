package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SlangNormalizerTest {

    @Test
    fun rewritesCasualQuestion() {
        assertEquals("kamu sudah di mana?", SlangNormalizer.normalize("kamu udah dimana?"))
    }

    @Test
    fun keepsCapitalisationAndDropsParticles() {
        assertEquals("Saya tidak bisa datang.", SlangNormalizer.normalize("Gue gak bisa datang dong."))
    }

    @Test
    fun leavesFormalTextAlone() {
        assertEquals("Saya sedang makan siang.", SlangNormalizer.normalize("Saya sedang makan siang."))
    }

    @Test
    fun understandsJavaneseMixedIn() {
        assertEquals("bagaimana kabarnya bro, sudah makan?", SlangNormalizer.normalize("piye kabare bro, wis mangan?"))
    }

    @Test
    fun understandsSundaneseMixedIn() {
        assertEquals("Bagaimana, sehat? Saya ingin ke sana sekarang", SlangNormalizer.normalize("Kumaha, damang? Abdi hayang ke ditu ayeuna"))
    }

    @Test
    fun replacesPhrasesBeforeWords() {
        assertEquals("Terima kasih, tidak apa-apa", SlangNormalizer.normalize("Matur nuwun, ora popo"))
        assertEquals("terima kasih pisangnya", SlangNormalizer.normalize("hatur nuhun pisangnya"))
    }
}
