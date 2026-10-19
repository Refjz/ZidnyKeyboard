package com.zidny.keyboard

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** Converts money and measurements in the 🧰 Tools menu. Pure, so it can be unit tested. */
object UnitConverter {

    /** One unit: [factor] is how many of the category's base unit one of it is (unused for money and temperature). */
    data class Measure(val code: String, val name: String, val factor: Double = 1.0)

    enum class Category(val label: String, val units: List<Measure>) {
        MONEY("💱 Uang", listOf(
            Measure("IDR", "Rupiah"), Measure("USD", "Dolar AS"), Measure("SGD", "Dolar Singapura"), Measure("MYR", "Ringgit"),
            Measure("SAR", "Riyal Saudi"), Measure("EUR", "Euro"), Measure("JPY", "Yen"), Measure("KRW", "Won"),
            Measure("CNY", "Yuan"), Measure("TWD", "Dolar Taiwan"), Measure("HKD", "Dolar Hong Kong"), Measure("AUD", "Dolar Australia"),
            Measure("GBP", "Pound"), Measure("THB", "Baht"), Measure("AED", "Dirham"), Measure("INR", "Rupee"),
        )),
        LENGTH("📏 Panjang", listOf(
            Measure("km", "kilometer", 1000.0), Measure("m", "meter"), Measure("cm", "sentimeter", 0.01), Measure("mm", "milimeter", 0.001),
            Measure("mil", "mil", 1609.344), Measure("yd", "yard", 0.9144), Measure("ft", "kaki", 0.3048), Measure("in", "inci", 0.0254),
        )),
        WEIGHT("⚖️ Berat", listOf(
            Measure("kg", "kilogram"), Measure("g", "gram", 0.001), Measure("ons", "ons", 0.1), Measure("ton", "ton", 1000.0),
            Measure("lb", "pon", 0.45359237), Measure("oz", "ounce", 0.028349523125),
        )),
        TEMPERATURE("🌡️ Suhu", listOf(Measure("°C", "Celsius"), Measure("°F", "Fahrenheit"), Measure("K", "Kelvin"))),
        VOLUME("🧪 Volume", listOf(
            Measure("L", "liter"), Measure("mL", "mililiter", 0.001), Measure("gal", "galon AS", 3.785411784),
            Measure("cup", "cangkir", 0.2365882365), Measure("fl oz", "ons cair", 0.0295735295625),
        )),
        SPEED("🚗 Kecepatan", listOf(
            Measure("km/j", "km per jam"), Measure("mph", "mil per jam", 1.609344), Measure("m/s", "meter per detik", 3.6), Measure("knot", "knot", 1.852),
        )),
        AREA("📐 Luas", listOf(
            Measure("m²", "meter persegi"), Measure("ha", "hektare", 10_000.0), Measure("km²", "kilometer persegi", 1_000_000.0),
            Measure("are", "are", 100.0), Measure("ft²", "kaki persegi", 0.09290304), Measure("acre", "ekar", 4046.8564224),
        )),
    }

    /**
     * [value] in [from] expressed in [to], or null when money is converted without a rate for one of them.
     * [rates] are units of each currency per US dollar.
     */
    fun convert(category: Category, value: Double, from: Measure, to: Measure, rates: Map<String, Double> = emptyMap()): Double? =
        when (category) {
            Category.MONEY -> {
                val fromRate = rates[from.code]
                val toRate = rates[to.code]
                if (fromRate == null || toRate == null || fromRate <= 0) null else value / fromRate * toRate
            }
            Category.TEMPERATURE -> fromCelsius(toCelsius(value, from.code), to.code)
            else -> value * from.factor / to.factor
        }

    private fun toCelsius(value: Double, code: String) = when (code) {
        "°F" -> (value - 32) * 5 / 9
        "K" -> value - 273.15
        else -> value
    }

    private fun fromCelsius(value: Double, code: String) = when (code) {
        "°F" -> value * 9 / 5 + 32
        "K" -> value + 273.15
        else -> value
    }

    /**
     * [value] the Indonesian way, with dots between thousands and a decimal comma, e.g. 1.630.000 or 3,11.
     * Money keeps two decimals (none for currencies without cents); measurements keep four significant digits.
     */
    fun format(value: Double, unit: Measure, category: Category): String {
        val rounded = if (category == Category.MONEY) {
            BigDecimal(value).setScale(if (unit.code in WHOLE_CURRENCIES) 0 else 2, RoundingMode.HALF_UP)
        } else {
            val big = BigDecimal(value)
            // Big numbers keep all their whole digits; small ones keep four significant digits.
            if (big.abs() >= BigDecimal(10_000)) big.setScale(0, RoundingMode.HALF_UP) else big.round(MathContext(4))
        }
        val symbols = DecimalFormatSymbols().apply {
            groupingSeparator = '.'
            decimalSeparator = ','
            minusSign = '-'
        }
        return DecimalFormat("#,##0.##########", symbols).format(rounded.stripTrailingZeros()).let { if (it == "-0") "0" else it }
    }

    /** What is typed into the chat, e.g. "100 USD ≈ 1.630.000 IDR" or "5 km = 3,107 mil". */
    fun sentence(category: Category, amount: String, from: Measure, result: Double, to: Measure): String {
        val sign = if (category == Category.MONEY) "≈" else "="
        return "$amount ${from.code} $sign ${format(result, to, category)} ${to.code}"
    }

    /** Currencies that are not written with cents. */
    private val WHOLE_CURRENCIES = setOf("IDR", "JPY", "KRW")
}
