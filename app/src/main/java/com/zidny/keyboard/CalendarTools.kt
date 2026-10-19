package com.zidny.keyboard

import java.util.Calendar

/** Dates and free time for the 📅 calendar in the 🧰 Tools menu. Pure, so it can be unit tested. */
object CalendarTools {

    val MONTHS = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )
    /** Day names from Sunday, as Indonesian calendars start the week. */
    val DAYS = listOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
    val SHORT_DAYS = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    /** An event on the phone's calendar, with its times in minutes from that day's midnight. */
    data class Event(val title: String, val start: Int, val end: Int, val allDay: Boolean)

    /** The day numbers of [month] (0–11) laid out in weeks from Sunday; null fills the days of other months. */
    fun monthGrid(year: Int, month: Int): List<Int?> {
        val first = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
        val blanks = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = List(blanks) { null } + (1..days).toList()
        return cells + List((7 - cells.size % 7) % 7) { null }
    }

    /** E.g. "Selasa, 21 Oktober 2026". */
    fun formatDate(year: Int, month: Int, day: Int): String = "${dayName(year, month, day)}, $day ${MONTHS[month]} $year"

    fun dayName(year: Int, month: Int, day: Int): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month, day)
        }
        return DAYS[calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]
    }

    /**
     * The gaps of at least [minLength] minutes between [from] and [to] (minutes from midnight) that no timed
     * event covers. All-day events, like birthdays and holidays, don't make the user busy.
     */
    fun freeSlots(events: List<Event>, from: Int, to: Int, minLength: Int = 30): List<Pair<Int, Int>> {
        val busy = events.filter { !it.allDay && it.end > from && it.start < to }.sortedBy { it.start }
        val slots = mutableListOf<Pair<Int, Int>>()
        var cursor = from
        for (event in busy) {
            if (event.start - cursor >= minLength) slots.add(cursor to event.start)
            cursor = maxOf(cursor, event.end)
        }
        if (to - cursor >= minLength) slots.add(cursor to to)
        return slots
    }

    /** E.g. "14.30". */
    fun clock(minutes: Int) = "%02d.%02d".format(minutes / 60, minutes % 60)

    /** What is typed into the chat, e.g. "Selasa, 21 Oktober aku kosong jam 08.00–10.00 dan 13.00–21.00". */
    fun freeSentence(dateLabel: String, slots: List<Pair<Int, Int>>): String = when (slots.size) {
        0 -> "$dateLabel aku udah penuh, gimana kalau hari lain?"
        else -> {
            val parts = slots.map { (start, end) -> "${clock(start)}–${clock(end)}" }
            val joined = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " dan " + parts.last()
            "$dateLabel aku kosong jam $joined"
        }
    }

    /** The day's free time starts at 08.00, or for today at the next half hour from [nowMinutes]. */
    fun dayStart(isToday: Boolean, nowMinutes: Int): Int =
        if (!isToday) DAY_START else maxOf(DAY_START, (nowMinutes + 29) / 30 * 30)

    const val DAY_START = 8 * 60
    const val DAY_END = 21 * 60
}
