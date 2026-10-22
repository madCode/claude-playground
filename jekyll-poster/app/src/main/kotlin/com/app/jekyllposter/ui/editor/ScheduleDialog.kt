package com.app.jekyllposter.ui.editor

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.app.jekyllposter.ui.home.whenScheduled
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Picks when a post goes out: a day (today, tomorrow or any later one) and a time on the phone's
 * clock. [onSchedule] gets the moment in epoch millis; a moment already past can't be chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(onDismiss: () -> Unit, onSchedule: (Long) -> Unit, now: () -> Long = System::currentTimeMillis) {
    val zone = ZoneId.systemDefault()
    // The next whole hour after an hour from now: a time the writer is likely to change only a little.
    val suggested = remember { Instant.ofEpochMilli(now()).atZone(zone).plusHours(1).truncatedTo(ChronoUnit.HOURS).plusHours(1) }
    val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
    var day by rememberSaveable { mutableLongStateOf(suggested.toLocalDate().toEpochDay()) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    val time = rememberTimePickerState(suggested.hour, suggested.minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    val at = LocalDate.ofEpochDay(day).atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
    // Read again every few seconds: a time chosen just ahead can pass while the dialog is open.
    val clock by produceState(now()) {
        while (true) {
            delay(5_000)
            value = now()
        }
    }
    val ahead = at > clock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("When should it go out?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Day", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = day == today.toEpochDay(), onClick = { day = today.toEpochDay() }, label = { Text("Today") })
                    FilterChip(selected = day == today.toEpochDay() + 1, onClick = { day = today.toEpochDay() + 1 }, label = { Text("Tomorrow") })
                    val later = day > today.toEpochDay() + 1
                    FilterChip(
                        selected = later, onClick = { pickingDay = true },
                        label = { Text(if (later) LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE d MMM")) else "Another day…") },
                    )
                }
                Text("Time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                TimeInput(state = time)
                Text(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    text = if (ahead) "Goes out at ${whenScheduled(at, now())}, if the phone is on and online then; otherwise as soon as it is."
                    else "That time has passed.",
                    color = if (ahead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(enabled = ahead, onClick = { if (at > now()) onSchedule(at) }) { Text("Schedule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDay) {
        // The date picker works in UTC midnights, whatever the phone's zone.
        val firstDay = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= firstDay
                override fun isSelectableYear(year: Int) = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickingDay = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDay = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
}
