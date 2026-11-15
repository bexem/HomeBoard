package com.evofactory.homeboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Hiding what is over: an event leaves the card when it ends, or after the hours asked for, and an
 * event that is still running (or that has no end to pass) stays. Timed against a fixed clock, so
 * the answer never depends on when the suite runs.
 */
class CalendarHidePastTest {
    private val zone = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 11, 15)
    private val calendar = "calendar.work"

    // 10:00 on the test day
    private val now: ZonedDateTime = today.atTime(10, 0).atZone(zone)

    private fun timed(summary: String, fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int) =
        HaEvent(
            calendarId = calendar,
            summary = summary,
            start = today.atTime(fromHour, fromMinute).atZone(zone),
            startDay = today,
            end = today.atTime(toHour, toMinute).atZone(zone),
        )

    private fun allDay(summary: String, day: LocalDate = today) =
        HaEvent(calendarId = calendar, summary = summary, start = null, startDay = day)

    private fun shown(vararg events: HaEvent, hidePast: Boolean = true, hours: Int = 0): List<String> {
        val config = CalendarConfig(
            entities = listOf(calendar),
            days = 3,
            hidePast = hidePast,
            hideAfterHours = hours,
            maxEvents = 20,
        )
        return mergeCalendarEvents(mapOf(calendar to events.toList()), config, today, now).map { it.summary }
    }

    @Test
    fun anEventThatEndedIsGoneTheMinuteItEnds() {
        val over = timed("Ended at 09:59", 9, 0, 9, 59)
        assertEquals(emptyList<String>(), shown(over))
    }

    @Test
    fun anEventRunningNowStays() {
        val running = timed("Running 09:30–10:30", 9, 30, 10, 30)
        assertEquals(listOf("Running 09:30–10:30"), shown(running))
    }

    @Test
    fun theGraceKeepsItForTheHoursAsked() {
        val over = timed("Ended at 08:30", 8, 0, 8, 30)
        // 90 minutes over; one hour of grace has passed, two have not
        assertEquals(emptyList<String>(), shown(over, hours = 1))
        assertEquals(listOf("Ended at 08:30"), shown(over, hours = 2))
    }

    @Test
    fun theSettingOffKeepsEverything() {
        val over = timed("Ended at 08:30", 8, 0, 8, 30)
        assertEquals(listOf("Ended at 08:30"), shown(over, hidePast = false))
    }

    @Test
    fun todaysAllDayEventStaysUntilItsDayIsOver() {
        assertEquals(listOf("Today, all day"), shown(allDay("Today, all day")))
    }

    @Test
    fun noEndMeansNoMomentToPass() {
        val openEnded = HaEvent(
            calendarId = calendar,
            summary = "No end given",
            start = today.atTime(9, 0).atZone(zone),
            startDay = today,
            end = null,
        )
        assertEquals(listOf("No end given"), shown(openEnded))
    }
}
