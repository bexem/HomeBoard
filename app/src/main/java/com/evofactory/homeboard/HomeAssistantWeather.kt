package com.evofactory.homeboard

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private const val HOURS_SHOWN = 6

/**
 * A weather entity: the current conditions and temperature, today's high, low and rain (from the
 * daily forecast), then the next hours (from the hourly forecast). Each part is left out when
 * this weather service doesn't give it.
 */
@Composable
fun WeatherReading(entity: HaEntity?, hourly: List<HaForecast>?, daily: List<HaForecast>?) {
    val condition = entity?.state
    val today = daily?.firstOrNull { it.time?.toLocalDate() == LocalDate.now() }
    // From the current hour on
    val now = ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS)
    val hours = hourly.orEmpty().filter { it.time?.isBefore(now) == false }.take(HOURS_SHOWN)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val rainUnit = entity?.attributes?.optString("precipitation_unit").orEmpty().ifEmpty { "mm" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MdiIcon(weatherIcon(condition), size = 32.dp)
            Text(entity?.temperature?.let(::degrees) ?: "—", style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    weatherLabel(condition) ?: entity?.name ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (today != null) {
                    // e.g. "14° / 6° · rain 30 %" or "14° / 6° · no rain"; nothing about rain if the service doesn't say
                    val range = listOfNotNull(today.temperature, today.lowTemperature).joinToString(" / ", transform = ::degrees)
                    val rainToday = rain(today, rainUnit)?.let { "rain $it" }
                        ?: "no rain".takeIf { today.rainChance != null || today.rain != null }
                    Text(
                        listOfNotNull(range.ifEmpty { null }, rainToday).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (hours.isNotEmpty()) {
            val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
            val hourFormat = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "ha")
            // The rain line only when some hour has rain, so dry days take less room
            val rains = hours.map { rain(it, rainUnit) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                hours.forEachIndexed { index, hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(hour.time?.format(hourFormat).orEmpty(), style = MaterialTheme.typography.labelSmall, color = dim)
                        MdiIcon(weatherIcon(hour.condition), size = 20.dp)
                        Text(hour.temperature?.let(::degrees) ?: "—", style = MaterialTheme.typography.bodyMedium)
                        if (rains.any { it != null }) {
                            Text(rains[index].orEmpty(), style = MaterialTheme.typography.labelSmall, color = dim)
                        }
                    }
                }
            }
        }
    }
}

private fun degrees(value: Double) = "${value.roundToInt()}°"

/** Chance of rain, e.g. "30 %", or else the amount, e.g. "0.4 mm"; null when dry */
private fun rain(forecast: HaForecast, unit: String): String? {
    forecast.rainChance?.let { return if (it > 0) "${it.roundToInt()} %" else null }
    val amount = forecast.rain ?: return null
    return if (amount > 0) "${"%.1f".format(amount)} $unit" else null
}

/**
 * The weather's coming days, opened with OK on its card: one row per day with what it will do, its high
 * and low, and its rain. The days come from Home Assistant's own forecast (followed for every chosen
 * weather, see the panel), so nothing is invented and a server that gives no forecast shows the reason.
 */
@Composable
fun WeatherForecastDialog(
    entity: HaEntity?,
    daily: List<HaForecast>?,
    hourly: List<HaForecast>?,
    onDismiss: () -> Unit,
) {
    val close = remember { FocusRequester() }
    val rainUnit = entity?.attributes?.optString("precipitation_unit").orEmpty().ifEmpty { "mm" }
    val today = LocalDate.now()
    val nowTemperature = entity?.temperature
    val days = daily.orEmpty().filter { it.time != null }
    val hours = hourly.orEmpty().filter { it.time?.isBefore(ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS)) == false }.take(NEXT_HOURS)
    TopDialog(
        title = entity?.name ?: "Weather",
        width = 720.dp,
        onDismiss = onDismiss,
        subtitle = weatherLabel(entity?.state)?.let { now ->
            listOfNotNull(now, nowTemperature?.let(::degrees)).joinToString(" · ")
        },
        buttons = listOf(DialogButton("Close", primary = true, focus = close, onClick = onDismiss)),
        contentFocus = { close },
    ) {
        if (days.isEmpty() && hours.isEmpty()) {
            Text(
                "Home Assistant gave no forecast for this weather. Its own integration decides whether it has one.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        if (days.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                days.forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            dayName(day.time!!.toLocalDate(), today),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(120.dp),
                        )
                        MdiIcon(weatherIcon(day.condition), size = 22.dp)
                        Text(
                            weatherLabel(day.condition).orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(day.temperature?.let(::degrees), day.lowTemperature?.let(::degrees))
                                .joinToString(" / "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            rain(day, rainUnit).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(70.dp),
                        )
                    }
                }
            }
        }
        if (hours.isNotEmpty()) {
            Text(
                "Next hours",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                hours.forEach { hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            hour.time?.format(DateTimeFormatter.ofPattern("HH:mm")).orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MdiIcon(weatherIcon(hour.condition), size = 20.dp)
                        Text(hour.temperature?.let(::degrees) ?: "—", style = MaterialTheme.typography.bodySmall)
                        Text(
                            rain(hour, rainUnit).orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** "Today", "Tomorrow", or the day's own name */
private fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

private const val NEXT_HOURS = 8
