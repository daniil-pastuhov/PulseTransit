package com.example.pulsetransit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.pulsetransit.data.DestinationSchedule
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.ui.platform.is24HourClock
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Below this width the editor is a bottom sheet; above it, a dialog that doesn't stretch across a
// tablet or a landscape wall display.
private const val DIALOG_MIN_WIDTH_DP = 600
private const val MINUTES_PER_HOUR = 60
private const val DEFAULT_START_MINUTE = 7 * MINUTES_PER_HOUR
private const val DEFAULT_END_MINUTE = 9 * MINUTES_PER_HOUR

/**
 * Edits when [destination]'s route is shown. Saving with "Always" on passes null. A bottom sheet
 * on phones and a dialog on wider screens; both hold the same content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationScheduleEditor(
    destination: StopPlace,
    schedule: DestinationSchedule?,
    onSave: (DestinationSchedule?) -> Unit,
    onDismiss: () -> Unit,
) {
    val widthDp =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
                .value
        }
    if (widthDp >= DIALOG_MIN_WIDTH_DP) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.widthIn(max = 480.dp)) {
                // A dialog has no drag handle above the title, so it gets the room the handle takes.
                ScheduleContent(destination, schedule, onSave, onDismiss, Modifier.padding(top = 16.dp))
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ScheduleContent(destination, schedule, onSave, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleContent(
    destination: StopPlace,
    schedule: DestinationSchedule?,
    onSave: (DestinationSchedule?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var always by remember { mutableStateOf(schedule == null) }
    var days by remember { mutableStateOf(schedule?.isoDays ?: DestinationSchedule.WEEKDAYS) }
    var startMinute by remember { mutableIntStateOf(schedule?.startMinute ?: DEFAULT_START_MINUTE) }
    var endMinute by remember { mutableIntStateOf(schedule?.endMinute ?: DEFAULT_END_MINUTE) }
    var picking by remember { mutableStateOf<TimeField?>(null) }
    val edited = DestinationSchedule(days, startMinute, endMinute)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Text(stringResource(Res.string.schedule_title), style = MaterialTheme.typography.titleLarge)
        Text(
            destination.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.schedule_always), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = always, onCheckedChange = { always = it })
        }

        if (!always) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { days = DestinationSchedule.ALL_DAYS },
                    label = { Text(stringResource(Res.string.schedule_every_day)) },
                )
                AssistChip(
                    onClick = { days = DestinationSchedule.WEEKDAYS },
                    label = { Text(stringResource(Res.string.schedule_weekdays)) },
                )
                AssistChip(onClick = { days = DestinationSchedule.WEEKEND }, label = { Text(stringResource(Res.string.schedule_weekend)) })
            }
            Spacer(Modifier.height(8.dp))
            // One row of seven equal cells, so the week reads as a week at any width.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (day in DestinationSchedule.ALL_DAYS) {
                    DayToggle(dayName(day), selected = day in days, modifier = Modifier.weight(1f)) {
                        days = if (day in days) days - day else days + day
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeButton(Res.string.schedule_from, startMinute, Modifier.weight(1f)) { picking = TimeField.START }
                TimeButton(Res.string.schedule_to, endMinute, Modifier.weight(1f)) { picking = TimeField.END }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (days.isEmpty()) stringResource(Res.string.schedule_no_days) else scheduleSummary(edited),
                style = MaterialTheme.typography.bodyMedium,
                color = if (days.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(Res.string.schedule_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = { onSave(if (always) null else edited) },
                enabled = always || days.isNotEmpty(),
            ) { Text(stringResource(Res.string.save)) }
        }
    }

    picking?.let { field ->
        TimePickerDialog(
            minute = if (field == TimeField.START) startMinute else endMinute,
            onPick = { picked ->
                // Midnight as an end means "until the end of the day", which the schedule stores as 24:00.
                when (field) {
                    TimeField.START -> startMinute = picked
                    TimeField.END -> endMinute = if (picked == 0) DestinationSchedule.MINUTES_PER_DAY else picked
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private enum class TimeField { START, END }

@Composable
private fun DayToggle(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun TimeButton(
    label: StringResource,
    minute: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(timeText(minute), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    minute: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val wrapped = minute % DestinationSchedule.MINUTES_PER_DAY
    val state =
        rememberTimePickerState(
            initialHour = wrapped / MINUTES_PER_HOUR,
            initialMinute = wrapped % MINUTES_PER_HOUR,
            is24Hour = is24HourClock(),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * MINUTES_PER_HOUR + state.minute) }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        text = { TimePicker(state = state) },
    )
}

/** The schedule as one line, e.g. "Mon–Fri · 08:00–09:00", or "Always" when there is none. */
@Composable
fun scheduleSummary(schedule: DestinationSchedule?): String {
    if (schedule == null) return stringResource(Res.string.schedule_always)
    val days =
        if (schedule.isoDays == DestinationSchedule.ALL_DAYS) {
            stringResource(Res.string.schedule_every_day)
        } else {
            val locale = Locale.getDefault()
            schedule.daysText { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) }
        }
    val start = timeText(schedule.startMinute)
    val end = timeText(schedule.endMinute)
    return when {
        schedule.startMinute == 0 && schedule.endMinute == DestinationSchedule.MINUTES_PER_DAY ->
            stringResource(Res.string.schedule_summary_all_day, days)
        schedule.endsNextDay ->
            stringResource(Res.string.schedule_summary_next_day, days, start, end)
        else -> stringResource(Res.string.schedule_summary, days, start, end)
    }
}

@Composable
private fun dayName(isoDay: Int): String = DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** Minutes since midnight in the device's 12- or 24-hour form; 24:00 shows as midnight. */
@Composable
private fun timeText(minute: Int): String {
    val wrapped = minute % DestinationSchedule.MINUTES_PER_DAY
    val pattern = if (is24HourClock()) "HH:mm" else "h:mm a"
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return LocalTime.of(wrapped / MINUTES_PER_HOUR, wrapped % MINUTES_PER_HOUR).format(formatter)
}
