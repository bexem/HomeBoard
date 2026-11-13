package com.evofactory.homeboard

import androidx.compose.ui.graphics.Color
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * One event from a Home Assistant calendar. All-day events carry the day alone: their [start] and
 * [end] are null, so the widget never prints a time for them.
 */
data class HaEvent(
    val calendarId: String,
    val summary: String,
    val start: ZonedDateTime?,
    val startDay: LocalDate,
    val end: ZonedDateTime? = null,
    val location: String? = null,
) {
    /** All-day: no time to show */
    val allDay: Boolean get() = start == null

    companion object {
        /** A date alone is an all-day event: Home Assistant sends "2026-11-14" and nothing more */
        private val dateOnly = Regex("""\d{4}-\d{2}-\d{2}""")

        // Small private readers: Home Assistant's JSON omits what it has no value for, and a time
        // arrives as a string over the websocket but as an object over REST, so only real strings count
        private fun JSONObject.text(key: String): String? =
            (opt(key) as? String)?.takeIf { it.isNotEmpty() }

        /** One event from either shape Home Assistant sends, or null when it has nothing to show */
        fun from(calendarId: String, json: JSONObject): HaEvent? {
            val summary = json.text("summary") ?: json.text("message") ?: return null
            val startText = json.time("start") ?: return null
            val zone = ZoneId.systemDefault()
            return if (dateOnly.matches(startText)) {
                HaEvent(
                    calendarId = calendarId,
                    summary = summary,
                    start = null,
                    startDay = LocalDate.parse(startText),
                    location = json.text("location"),
                )
            } else {
                val start = parseTime(startText) ?: return null
                HaEvent(
                    calendarId = calendarId,
                    summary = summary,
                    start = start,
                    startDay = start.withZoneSameInstant(zone).toLocalDate(),
                    end = json.time("end")?.let(::parseTime),
                    location = json.text("location"),
                )
            }
        }

        /** A time as the websocket sends it (a string) or the REST API does ({date} or {dateTime}) */
        private fun JSONObject.time(key: String): String? {
            text(key)?.let { return it }
            val value = optJSONObject(key) ?: return null
            return value.text("dateTime") ?: value.text("date")
        }

        private fun parseTime(text: String): ZonedDateTime? =
            runCatching { ZonedDateTime.parse(text) }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }.getOrNull()
    }
}

/**
 * A calendar's events between [start] and [end], read over Home Assistant's calendar API:
 * `GET /api/calendars/<entity_id>`. The websocket command for this (`calendar/get_events`) is not
 * registered on this server (it answers "Unknown command"), the REST endpoint is documented and
 * stable. Null on any failure: the card stays away rather than reporting something a TV can't fix.
 */
suspend fun haCalendarEvents(server: HaServer, entityId: String, start: Instant, end: Instant): List<HaEvent>? =
    withContext(Dispatchers.IO) {
        val url = "${server.url}/api/calendars/$entityId" +
            "?start=${URLEncoder.encode(start.toString(), "UTF-8")}" +
            "&end=${URLEncoder.encode(end.toString(), "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${server.accessToken()}")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    AppLog.w("Calendar", "$entityId: HTTP ${response.code} $body")
                    return@withContext null
                }
                val array = JSONArray(body)
                val events = List(array.length()) { index ->
                    array.optJSONObject(index)?.let { HaEvent.from(entityId, it) }
                }.filterNotNull()
                if (events.size != array.length()) {
                    AppLog.w("Calendar", "$entityId: ${array.length()} sent, ${events.size} parsed; first: ${body.take(200)}")
                }
                events
            }
        } catch (e: Exception) {
            AppLog.w("Calendar", "$entityId: ${e.message}")
            null
        }
    }

/**
 * The colours the calendar card offers. Home Assistant keeps no colour for a calendar entity over the
 * API (the state carries only times and the message), so the colour is chosen here; the order matches
 * the colours Home Assistant's own calendar card hands out, for the calendars nobody changes.
 */
val CalendarPalette = listOf(
    ColourEntry("Green", 0xFF7CB342.toInt()),
    ColourEntry("Pink", 0xFFD81B60.toInt()),
    ColourEntry("Blue", 0xFF1E88E5.toInt()),
    ColourEntry("Red", 0xFFE53935.toInt()),
    ColourEntry("Orange", 0xFFFB8C00.toInt()),
    ColourEntry("Teal", 0xFF00897B.toInt()),
    ColourEntry("Purple", 0xFF8E24AA.toInt()),
    ColourEntry("Amber", 0xFFFFB300.toInt()),
)

/** The colour chosen for [id], or null to use the palette colour of its position */
fun calendarColour(choices: List<String>, id: String): String? =
    choices.firstOrNull { it.startsWith("$id=") }?.substringAfter("=", "")?.takeIf { it.isNotEmpty() }

/** "#RRGGBB" from the palette, or the palette's first colour when nothing is chosen */
fun colourOf(hex: String?): Color {
    val text = hex?.removePrefix("#") ?: return Color(0xFF7CB342)
    val value = runCatching { text.toLong(16) }.getOrNull() ?: return Color(0xFF7CB342)
    return Color(0xFF000000 or value)
}

/** A day's events under its heading */
data class HaEventGroup(val title: String, val events: List<HaEvent>)

/**
 * What the calendar card shows. Read once where the prefs are, so the panel and the widget only
 * deal in values.
 */
data class CalendarConfig(
    val entities: List<String> = emptyList(),
    val days: Int = CALENDAR_DAYS_DEFAULT,
    val maxEvents: Int = CALENDAR_MAX_EVENTS_DEFAULT,
    val groupStyle: String = CALENDAR_TODAY_TOMORROW,
    val showAllDay: Boolean = true,
    val hideEmpty: Boolean = true,
    val colours: List<String> = emptyList(),
    val grouping: String = CALENDAR_BY_TIME,
)

/**
 * The events of [eventsByCalendar] inside the window, in the order the calendars are listed, sorted
 * by day and then by time with all-day events first, capped at [maxEvents].
 */
fun mergeCalendarEvents(
    eventsByCalendar: Map<String, List<HaEvent>>,
    config: CalendarConfig,
    today: LocalDate,
): List<HaEvent> {
    val lastDay = today.plusDays((config.days - 1).toLong())
    val rank = config.entities.withIndex().associate { (index, id) -> id to index }
    return config.entities
        .flatMap { id -> eventsByCalendar[id].orEmpty() }
        .filter { !it.startDay.isBefore(today) && !it.startDay.isAfter(lastDay) }
        .filter { config.showAllDay || !it.allDay }
        .sortedWith(
            // By priority, every event of the first calendar comes before the next calendar's, so a
            // work shift is not pushed out by a birthday; by time, the card reads as a diary
            if (config.grouping == CALENDAR_BY_PRIORITY) {
                compareBy<HaEvent> { rank[it.calendarId] ?: Int.MAX_VALUE }
                    .thenBy { it.startDay }
                    .thenBy { it.start?.toLocalTime() }
                    .thenBy { it.summary }
            } else {
                compareBy<HaEvent> { it.startDay }
                    .thenBy { it.start?.toLocalTime() }
                    .thenBy { rank[it.calendarId] ?: Int.MAX_VALUE }
                    .thenBy { it.summary }
            },
        )
        .take(config.maxEvents)
}

/**
 * The events under day headings: Today and Tomorrow when the style asks for it, otherwise the
 * weekday and date. An all-day event sorts before the timed ones on its day.
 */
fun groupCalendarEvents(
    events: List<HaEvent>,
    config: CalendarConfig,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): List<HaEventGroup> {
    val weekday = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    return events
        .groupBy { it.startDay }
        .toSortedMap()
        .map { (day, dayEvents) ->
            val title = when {
                config.groupStyle == CALENDAR_DATE -> day.format(weekday)
                day == today -> "Today"
                day == today.plusDays(1) -> "Tomorrow"
                else -> day.format(weekday)
            }
            // The merge already ordered them; all-day first is what it does within a day
            HaEventGroup(title, dayEvents.sortedWith(compareBy<HaEvent> { it.start != null }.thenBy { it.start }))
        }
}

/**
 * The line under an event's title, as the calendar card reads it: "All day" for an all-day event,
 * "19:00 – 20:00" when it ends the same day, "19:00, ends tomorrow at 7:00" when it runs overnight,
 * and the day first when it isn't today ("Tomorrow 19:00, ends …").
 */
fun HaEvent.detailLine(today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    val eventDay = startDay
    val dayPrefix = when {
        eventDay == today -> ""
        eventDay == today.plusDays(1) -> "Tomorrow "
        else -> "${eventDay.format(DateTimeFormatter.ofPattern("EEE", locale))} "
    }
    val start = start ?: return dayPrefix + "All day"
    val startText = start.format(time)
    val finish = end ?: return dayPrefix + startText
    val endDay = finish.withZoneSameInstant(start.zone).toLocalDate()
    val endDayName = endDay.format(DateTimeFormatter.ofPattern("EEE", locale))
    return when {
        endDay == eventDay && finish != start -> "$dayPrefix$startText – ${finish.format(time)}"
        // "tomorrow" only reads right when the event starts today; otherwise name the day it ends
        eventDay == today && endDay == today.plusDays(1) ->
            "$dayPrefix$startText, ends tomorrow at ${finish.format(time)}"
        endDay.isAfter(eventDay) -> "$dayPrefix$startText, ends $endDayName at ${finish.format(time)}"
        else -> "$dayPrefix$startText"
    }
}

/** "18:30" for a timed event */
fun HaEvent.timeLabel(locale: Locale = Locale.getDefault()): String =
    start?.format(DateTimeFormatter.ofPattern("HH:mm", locale)).orEmpty()

/** "Fri 14 Nov" for a heading, in the locale's words */
fun LocalDate.dayLabel(locale: Locale = Locale.getDefault()): String =
    format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

/** The weekday name alone: what a heading needs when the date is already clear */
fun LocalDate.weekdayLabel(locale: Locale = Locale.getDefault()): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
