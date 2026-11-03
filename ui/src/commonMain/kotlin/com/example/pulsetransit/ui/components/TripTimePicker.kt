package com.example.pulsetransit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.pulsetransit.data.TripTime
import com.example.pulsetransit.data.atMillis
import com.example.pulsetransit.data.calendarDaysFrom
import com.example.pulsetransit.data.clockTimeText
import com.example.pulsetransit.data.localTimeOfDay
import com.example.pulsetransit.data.nextTimeOfDay
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** "Leave now", "Leave 08:30" or "Arrive by 08:30 tomorrow". */
@Composable
fun tripTimeLabel(time: TripTime): String =
    when (time) {
        TripTime.Now -> stringResource(Res.string.trip_time_now)
        is TripTime.DepartAt -> stringResource(Res.string.trip_time_leave_at, tripClockText(time.atMillis))
        is TripTime.ArriveBy -> stringResource(Res.string.trip_time_arrive_by, tripClockText(time.atMillis))
    }

/** "08:30", or "08:30 tomorrow" when it is not today. */
@Composable
fun tripClockText(atMillis: Long): String {
    val clock = clockTimeText(atMillis)
    val days = calendarDaysFrom(Clock.System.now().toEpochMilliseconds(), atMillis)
    return if (days >= 1) stringResource(Res.string.trip_time_tomorrow, clock) else clock
}

// Size of the "+1" next-day marker, as on the board's departure and arrival times.
private const val DAY_OFFSET_FONT_SCALE = 0.6f

/** "08:30", with a raised "+1" when it is tomorrow: narrow enough to sit beside a route banner. */
@Composable
private fun compactClockText(atMillis: Long): AnnotatedString {
    val days = calendarDaysFrom(Clock.System.now().toEpochMilliseconds(), atMillis)
    return buildAnnotatedString {
        append(clockTimeText(atMillis))
        if (days >= 1) {
            withStyle(SpanStyle(fontSize = DAY_OFFSET_FONT_SCALE.em, baselineShift = BaselineShift.Superscript)) {
                append("+$days")
            }
        }
    }
}

/** The route search's "when": a chip that opens [TripTimeDialog]. */
@Composable
fun TripTimeChip(
    time: TripTime,
    onTimeChange: (TripTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    AssistChip(
        onClick = { editing = true },
        label = { Text(tripTimeLabel(time), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = modifier,
    )
    if (editing) {
        TripTimeDialog(
            initial = time,
            onConfirm = {
                editing = false
                onTimeChange(it)
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * The temporary route's time beside its banner: a clock to set one, or the time it is set to
 * ("Arrive by" over "08:30"), either opening [TripTimeDialog] to change it.
 */
@Composable
fun TripTimeButton(
    time: TripTime,
    onTimeChange: (TripTime) -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    // Read out with the time, which the visible text gives only in part.
    val description = stringResource(Res.string.trip_time_change) + ", " + tripTimeLabel(time)
    val atMillis = time.atMillis
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { editing = true }
                .semantics { contentDescription = description }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (atMillis == null) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = tint, modifier = Modifier.padding(4.dp))
        } else {
            Text(
                text =
                    stringResource(
                        if (time is TripTime.ArriveBy) Res.string.trip_time_arrive else Res.string.trip_time_depart,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.8f),
                maxLines = 1,
            )
            Text(
                text = compactClockText(atMillis),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = tint,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (editing) {
        TripTimeDialog(
            initial = time,
            onConfirm = {
                editing = false
                onTimeChange(it)
            },
            onDismiss = { editing = false },
        )
    }
}

private enum class TripTimeKind { NOW, DEPART, ARRIVE }

// Minutes ahead the picker starts at when no time is set: far enough to be worth choosing.
private const val DEFAULT_LEAD_MINUTES = 15
private const val ROUND_TO_MINUTES = 5
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/**
 * Leave now, depart at or arrive by a time of day. A time earlier than now means tomorrow,
 * which the line under the picker says before it is confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTimeDialog(
    initial: TripTime,
    onConfirm: (TripTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by rememberSaveable {
        mutableStateOf(
            when (initial) {
                TripTime.Now -> TripTimeKind.NOW
                is TripTime.DepartAt -> TripTimeKind.DEPART
                is TripTime.ArriveBy -> TripTimeKind.ARRIVE
            },
        )
    }
    val start =
        remember {
            initial.atMillis?.let { localTimeOfDay(it) }?.let { it.hour * MINUTES_PER_HOUR + it.minute }
                ?: run {
                    val now = localTimeOfDay(Clock.System.now().toEpochMilliseconds())
                    val ahead = now.hour * MINUTES_PER_HOUR + now.minute + DEFAULT_LEAD_MINUTES
                    ((ahead + ROUND_TO_MINUTES - 1) / ROUND_TO_MINUTES * ROUND_TO_MINUTES) % MINUTES_PER_DAY
                }
        }
    val pickerState =
        rememberTimePickerState(initialHour = start / MINUTES_PER_HOUR, initialMinute = start % MINUTES_PER_HOUR, is24Hour = true)

    fun chosen(): TripTime {
        val at = nextTimeOfDay(pickerState.hour, pickerState.minute, Clock.System.now().toEpochMilliseconds())
        return when (kind) {
            TripTimeKind.NOW -> TripTime.Now
            TripTimeKind.DEPART -> TripTime.DepartAt(at)
            TripTimeKind.ARRIVE -> TripTime.ArriveBy(at)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.trip_time_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val kinds = TripTimeKind.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = kind == option,
                            onClick = { kind = option },
                            shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                            icon = {},
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            TripTimeKind.NOW -> Res.string.trip_time_now
                                            TripTimeKind.DEPART -> Res.string.trip_time_depart
                                            TripTimeKind.ARRIVE -> Res.string.trip_time_arrive
                                        },
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
                if (kind != TripTimeKind.NOW) {
                    TimePicker(state = pickerState)
                    // Says what will be searched for, so "tomorrow" is never a surprise.
                    Row {
                        Text(
                            text = tripTimeLabel(chosen()),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen()) }) { Text(stringResource(Res.string.trip_time_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        },
    )
}
