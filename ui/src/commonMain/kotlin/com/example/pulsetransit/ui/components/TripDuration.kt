package com.example.pulsetransit.ui.components

import androidx.compose.runtime.Composable
import com.example.pulsetransit.data.TimeSpanUnit
import com.example.pulsetransit.data.tripDurationParts
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val TimeSpanUnit.label: StringResource
    get() =
        when (this) {
            TimeSpanUnit.YEARS -> Res.string.duration_years
            TimeSpanUnit.MONTHS -> Res.string.duration_months
            TimeSpanUnit.DAYS -> Res.string.duration_days
            TimeSpanUnit.HOURS -> Res.string.duration_hours
            TimeSpanUnit.MINUTES -> Res.string.duration_min
        }

/** A trip or leg duration in the largest units that fit, e.g. "17 h 23 min" (see [tripDurationParts]). */
@Composable
fun tripDurationText(minutes: Int): String = tripDurationParts(minutes).map { stringResource(it.unit.label, it.value) }.joinToString(" ")

/** [tripDurationText] outside composition, for widgets. */
suspend fun tripDurationString(minutes: Int): String = tripDurationParts(minutes).map { getString(it.unit.label, it.value) }.joinToString(" ")
