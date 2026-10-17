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
}
