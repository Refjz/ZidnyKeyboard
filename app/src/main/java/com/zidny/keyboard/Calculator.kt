package com.zidny.keyboard

import java.math.BigDecimal
import java.math.MathContext

/** Evaluates the calculator panel's arithmetic, e.g. "12×(3+4)÷2" or "15%". Pure, so it can be unit tested. */
object Calculator {

    /** The value of [expression], or null when it is empty, incomplete or divides by zero. */
    fun evaluate(expression: String): Double? {
        val text = expression.replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.').filterNot { it.isWhitespace() }
        if (text.isEmpty()) return null
        return try {
            val parser = Parser(text)
            val value = parser.expression()
            if (!parser.atEnd() || value.isNaN() || value.isInfinite()) null else value
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** [value] without a needless ".0" and rounded to 12 significant digits, e.g. 0.1+0.2 shows as 0.3. */
    fun format(value: Double): String =
        BigDecimal(value).round(MathContext(12)).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd() = pos == text.length

        fun expression(): Double {
            var value = term()
            while (!atEnd() && text[pos] in "+-") {
                val op = text[pos++]
                val right = term()
                value = if (op == '+') value + right else value - right
            }
            return value
        }

        private fun term(): Double {
            var value = factor()
            while (!atEnd() && text[pos] in "*/") {
                val op = text[pos++]
                val right = factor()
                value = if (op == '*') value * right else value / right
            }
            return value
        }

        private fun factor(): Double {
            require(!atEnd())
            val c = text[pos]
            val value = when {
                c == '-' -> { pos++; -factor() }
                c == '+' -> { pos++; factor() }
                c == '(' -> {
                    pos++
                    val inner = expression()
                    require(!atEnd() && text[pos] == ')')
                    pos++
                    inner
                }
                c.isDigit() || c == '.' -> number()
                else -> throw IllegalArgumentException("unexpected $c")
            }
            if (!atEnd() && text[pos] == '%') {
                pos++
                return value / 100
            }
            return value
        }

        private fun number(): Double {
            val start = pos
            while (!atEnd() && (text[pos].isDigit() || text[pos] == '.')) pos++
            return text.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("bad number")
        }
    }
}
