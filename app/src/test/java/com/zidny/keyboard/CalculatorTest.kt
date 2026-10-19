package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {

    private fun calc(expression: String) = Calculator.evaluate(expression)?.let(Calculator::format)

    @Test
    fun followsOperatorPrecedenceAndBrackets() {
        assertEquals("14", calc("2+3×4"))
        assertEquals("20", calc("(2+3)×4"))
        assertEquals("2.5", calc("5÷2"))
        assertEquals("-3", calc("2−5"))
        assertEquals("6", calc("-(-6)"))
    }

    @Test
    fun formatsWithoutFloatingPointNoise() {
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("1000000", calc("1000×1000"))
        assertEquals("15", calc("150×10%"))
    }

    @Test
    fun incompleteOrInvalidGivesNothing() {
        assertNull(calc(""))
        assertNull(calc("2+"))
        assertNull(calc("(2+3"))
        assertNull(calc("5÷0"))
        assertNull(calc("2..3"))
    }
}
