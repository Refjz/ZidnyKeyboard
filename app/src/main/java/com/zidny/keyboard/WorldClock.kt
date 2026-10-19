package com.zidny.keyboard

import java.util.Calendar
import java.util.TimeZone

/** The time in other cities, for the 🧰 Tools menu, e.g. "20.00 WIB = 22.00 di Tokyo". Pure, so it can be unit tested. */
object WorldClock {

    data class City(val name: String, val flag: String, val zone: String)

    /** Indonesia's three zones first, then the places Indonesians most often chat with. */
    val cities = listOf(
        City("Jakarta (WIB)", "🇮🇩", "Asia/Jakarta"),
        City("Bali, Makassar (WITA)", "🇮🇩", "Asia/Makassar"),
        City("Jayapura (WIT)", "🇮🇩", "Asia/Jayapura"),
        City("Singapura", "🇸🇬", "Asia/Singapore"),
        City("Kuala Lumpur", "🇲🇾", "Asia/Kuala_Lumpur"),
        City("Bangkok", "🇹🇭", "Asia/Bangkok"),
        City("Manila", "🇵🇭", "Asia/Manila"),
        City("Hong Kong", "🇭🇰", "Asia/Hong_Kong"),
        City("Taipei", "🇹🇼", "Asia/Taipei"),
        City("Beijing", "🇨🇳", "Asia/Shanghai"),
        City("Seoul", "🇰🇷", "Asia/Seoul"),
        City("Tokyo", "🇯🇵", "Asia/Tokyo"),
        City("Sydney", "🇦🇺", "Australia/Sydney"),
        City("Makkah", "🇸🇦", "Asia/Riyadh"),
        City("Dubai", "🇦🇪", "Asia/Dubai"),
        City("Istanbul", "🇹🇷", "Europe/Istanbul"),
        City("Amsterdam", "🇳🇱", "Europe/Amsterdam"),
        City("Paris, Berlin", "🇪🇺", "Europe/Paris"),
        City("London", "🇬🇧", "Europe/London"),
        City("New York", "🇺🇸", "America/New_York"),
        City("Los Angeles", "🇺🇸", "America/Los_Angeles"),
    )

    /** The clock time at [millis] in [zone], written the Indonesian way, e.g. "20.00". */
    fun time(millis: Long, zone: String): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = millis }
        return "%02d.%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    /** How many days [zone]'s date is ahead of [home]'s at [millis]: 1 for tomorrow, -1 for yesterday. */
    fun dayShift(millis: Long, home: String, zone: String): Int {
        fun day(id: String) = Calendar.getInstance(TimeZone.getTimeZone(id)).apply { timeInMillis = millis }.let {
            it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR)
        }
        return (day(zone) - day(home)).coerceIn(-1, 1)
    }

    /** "+2 jam" or "−5,5 jam": how far [zone] is ahead of [home] at [millis], or "" for the same time. */
    fun difference(millis: Long, home: String, zone: String): String {
        val minutes = (TimeZone.getTimeZone(zone).getOffset(millis) - TimeZone.getTimeZone(home).getOffset(millis)) / 60_000
        if (minutes == 0) return ""
        val hours = Math.abs(minutes) / 60
        val rest = Math.abs(minutes) % 60
        val amount = if (rest == 0) "$hours" else "$hours," + (rest * 100 / 60).toString().trimEnd('0')
        return (if (minutes > 0) "+" else "−") + amount + " jam"
    }

    /** A short name for [zone]: WIB, WITA or WIT in Indonesia, otherwise the offset like "GMT+9". */
    fun zoneLabel(zone: String, millis: Long): String = when (TimeZone.getTimeZone(zone).getOffset(millis) / 60_000) {
        7 * 60 -> if (zone in INDONESIAN_ZONES) "WIB" else gmt(zone, millis)
        8 * 60 -> if (zone in INDONESIAN_ZONES) "WITA" else gmt(zone, millis)
        9 * 60 -> if (zone in INDONESIAN_ZONES) "WIT" else gmt(zone, millis)
        else -> gmt(zone, millis)
    }

    private fun gmt(zone: String, millis: Long): String {
        val minutes = TimeZone.getTimeZone(zone).getOffset(millis) / 60_000
        val sign = if (minutes < 0) "-" else "+"
        val abs = Math.abs(minutes)
        return "GMT$sign${abs / 60}" + if (abs % 60 != 0) ":%02d".format(abs % 60) else ""
    }

    /** What is typed into the chat, e.g. "20.00 WIB = 22.00 di Tokyo (besok)". */
    fun sentence(millis: Long, home: String, city: City): String {
        val shift = when (dayShift(millis, home, city.zone)) {
            1 -> " (besok)"
            -1 -> " (kemarin)"
            else -> ""
        }
        return "${time(millis, home)} ${zoneLabel(home, millis)} = ${time(millis, city.zone)} di ${city.name}$shift"
    }

    private val INDONESIAN_ZONES = setOf(
        "Asia/Jakarta", "Asia/Pontianak", "Asia/Makassar", "Asia/Jayapura",
    )
}
