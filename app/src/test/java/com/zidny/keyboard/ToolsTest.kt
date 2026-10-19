package com.zidny.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ToolsTest {

    private fun unit(category: UnitConverter.Category, code: String) = category.units.first { it.code == code }

    @Test
    fun convertsMoneyThroughTheDollar() {
        val money = UnitConverter.Category.MONEY
        val rates = mapOf("USD" to 1.0, "IDR" to 16_300.0, "SGD" to 1.3)
        assertEquals(1_630_000.0, UnitConverter.convert(money, 100.0, unit(money, "USD"), unit(money, "IDR"), rates)!!, 0.001)
        assertEquals(10.0, UnitConverter.convert(money, 13.0, unit(money, "SGD"), unit(money, "USD"), rates)!!, 0.001)
        assertNull(UnitConverter.convert(money, 1.0, unit(money, "USD"), unit(money, "JPY"), rates))
    }

    @Test
    fun convertsMeasurements() {
        val length = UnitConverter.Category.LENGTH
        assertEquals(3.10686, UnitConverter.convert(length, 5.0, unit(length, "km"), unit(length, "mil"))!!, 0.0001)
        val temperature = UnitConverter.Category.TEMPERATURE
        assertEquals(95.0, UnitConverter.convert(temperature, 35.0, unit(temperature, "°C"), unit(temperature, "°F"))!!, 0.0001)
        assertEquals(0.0, UnitConverter.convert(temperature, 273.15, unit(temperature, "K"), unit(temperature, "°C"))!!, 0.0001)
        val weight = UnitConverter.Category.WEIGHT
        assertEquals(5.0, UnitConverter.convert(weight, 5.0, unit(weight, "ons"), unit(weight, "g"))!! / 100, 0.0001)
    }

    @Test
    fun formatsTheIndonesianWay() {
        val money = UnitConverter.Category.MONEY
        assertEquals("1.630.000", UnitConverter.format(1_630_000.4, unit(money, "IDR"), money))
        assertEquals("12,5", UnitConverter.format(12.5, unit(money, "USD"), money))
        val length = UnitConverter.Category.LENGTH
        assertEquals("3,107", UnitConverter.format(3.10686, unit(length, "mil"), length))
        assertEquals("160.934", UnitConverter.format(160_934.4, unit(length, "m"), length))
        assertEquals("100 USD ≈ 1.630.000 IDR",
            UnitConverter.sentence(money, "100", unit(money, "USD"), 1_630_000.0, unit(money, "IDR")))
    }

    /** 13:00 UTC on 19 October 2026. */
    private val afternoon = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, Calendar.OCTOBER, 19, 13, 0)
    }.timeInMillis

    @Test
    fun tellsTheTimeElsewhere() {
        assertEquals("20.00", WorldClock.time(afternoon, "Asia/Jakarta"))
        assertEquals("22.00", WorldClock.time(afternoon, "Asia/Tokyo"))
        assertEquals("WIB", WorldClock.zoneLabel("Asia/Jakarta", afternoon))
        assertEquals("WITA", WorldClock.zoneLabel("Asia/Makassar", afternoon))
        assertEquals("GMT+9", WorldClock.zoneLabel("Asia/Tokyo", afternoon))
        assertEquals("+2 jam", WorldClock.difference(afternoon, "Asia/Jakarta", "Asia/Tokyo"))
        assertEquals("−4 jam", WorldClock.difference(afternoon, "Asia/Jakarta", "Asia/Riyadh"))
        val tokyo = WorldClock.cities.first { it.name == "Tokyo" }
        assertEquals("20.00 WIB = 22.00 di Tokyo", WorldClock.sentence(afternoon, "Asia/Jakarta", tokyo))
    }

    @Test
    fun saysWhenItIsAnotherDay() {
        // 20.00 in Jakarta is 09.00 the same morning in New York, and 23.30 in Jakarta is already tomorrow in Tokyo.
        assertEquals(0, WorldClock.dayShift(afternoon, "Asia/Jakarta", "America/New_York"))
        val late = afternoon + 210 * 60_000L
        assertEquals(1, WorldClock.dayShift(late, "Asia/Jakarta", "Asia/Tokyo"))
        val tokyo = WorldClock.cities.first { it.name == "Tokyo" }
        assertEquals("23.30 WIB = 01.30 di Tokyo (besok)", WorldClock.sentence(late, "Asia/Jakarta", tokyo))
    }

    @Test
    fun laysOutMonthsFromSunday() {
        // 1 October 2026 is a Thursday.
        val october = CalendarTools.monthGrid(2026, Calendar.OCTOBER)
        assertEquals(listOf(null, null, null, null, 1, 2, 3), october.take(7))
        assertEquals(35, october.size)
        assertEquals(31, october.filterNotNull().last())
        assertEquals(28, CalendarTools.monthGrid(2026, Calendar.FEBRUARY).filterNotNull().size)
        assertEquals("Selasa, 20 Oktober 2026", CalendarTools.formatDate(2026, Calendar.OCTOBER, 20))
    }

    @Test
    fun findsFreeTime() {
        val events = listOf(
            CalendarTools.Event("Rapat", 10 * 60, 12 * 60, false),
            CalendarTools.Event("Makan siang", 11 * 60 + 30, 13 * 60, false),
            CalendarTools.Event("Ulang tahun Ani", 0, 24 * 60, true),
            CalendarTools.Event("Kopi", 16 * 60 + 45, 17 * 60, false),
        )
        val slots = CalendarTools.freeSlots(events, 8 * 60, 21 * 60)
        assertEquals(listOf(8 * 60 to 10 * 60, 13 * 60 to 16 * 60 + 45, 17 * 60 to 21 * 60), slots)
        assertEquals("Selasa aku kosong jam 08.00–10.00, 13.00–16.45 dan 17.00–21.00", CalendarTools.freeSentence("Selasa", slots))
        assertEquals("Selasa aku udah penuh, gimana kalau hari lain?", CalendarTools.freeSentence("Selasa", emptyList()))
        assertEquals(14 * 60 + 30, CalendarTools.dayStart(true, 14 * 60 + 5))
        assertEquals(8 * 60, CalendarTools.dayStart(false, 14 * 60 + 5))
    }
}
