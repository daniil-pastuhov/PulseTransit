package com.example.pulsetransit.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pulsetransit.R
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StopBoardPanel(
    stopPlace: StopPlace,
    departures: List<Departure>,
    showDelays: Boolean = true,
    showMessages: Boolean = true,
    showCancelled: Boolean = false,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val filteredDepartures = remember(departures, showCancelled) {
        if (showCancelled) departures else departures.filter { !it.isCancelled }
    }

    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stopPlace.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    stopPlace.locality?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // Departures list
            if (isLoading && filteredDepartures.isEmpty()) {
                LoadingPlaceholderList()
            } else if (filteredDepartures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_departures),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredDepartures,
                        key = { it.line + it.destination + it.expectedTime + (it.quayId ?: "") }
                    ) { departure ->
                        DepartureRow(departure, showDelays, showMessages)
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingPlaceholderList() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(6) {
            DepartureRowPlaceholder(alpha = alpha)
        }
    }
}

@Composable
fun DepartureRowPlaceholder(alpha: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Line badge placeholder
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Destination placeholder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Time placeholder
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun DepartureRow(
    departure: Departure,
    showDelays: Boolean,
    showMessages: Boolean
) {
    val transportEmoji = getTransportEmoji(departure.transportMode)
    val pillColor = if (departure.isCancelled) Color.Gray else getTransportColor(departure.transportMode)
    val nowText = stringResource(R.string.now)
    val cancelledText = stringResource(R.string.cancelled)
    val minUnit = stringResource(R.string.unit_minute)
    val remainingTime = if (departure.isCancelled) cancelledText else formatDepartureTime(departure.expectedTime, nowText, minUnit)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (departure.isCancelled) 0.2f else 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Line number badge
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(pillColor)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = departure.line,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textDecoration = if (departure.isCancelled) TextDecoration.LineThrough else null
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Transport Icon & Destination
            Text(
                text = "$transportEmoji  ${departure.destination}",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (departure.isCancelled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (departure.isCancelled) TextDecoration.LineThrough else null
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Delay indicator
            if (!departure.isCancelled && showDelays && departure.delayMinutes > 0) {
                Text(
                    text = "+${departure.delayMinutes}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Remaining/Departure Time
            val isNow = remainingTime == stringResource(R.string.now)
            Text(
                text = remainingTime,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = when {
                        departure.isCancelled -> MaterialTheme.colorScheme.error
                        isNow -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                ),
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        if (showMessages && departure.notices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            departure.notices.forEach { notice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚠️",
                        fontSize = 10.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun getTransportEmoji(mode: String?): String {
    return when (mode?.lowercase()) {
        "bus" -> "🚌"
        "tram" -> "🚃"
        "metro" -> "🚇"
        "rail" -> "🚆"
        "water" -> "⛴️"
        else -> "🚌" // fallback
    }
}

private fun getTransportColor(mode: String?): Color {
    return when (mode?.lowercase()) {
        "bus" -> ColorBus
        "tram" -> ColorTram
        "metro" -> ColorMetro
        "rail" -> ColorTrain
        "airportrail" -> ColorExpressTrain
        "water" -> ColorFerry
        else -> ColorDefaultLine
    }
}

private fun formatDepartureTime(expectedTimeStr: String, nowText: String, minUnit: String): String {
    return try {
        // Simple ISO parser for common formats
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val formatted = expectedTimeStr.replace("Z", "+0000").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
        val expectedDate = sdf.parse(formatted)
        val now = Date()
        
        if (expectedDate != null) {
            val diffMs = expectedDate.time - now.time
            val diffSeconds = diffMs / 1000
            when {
                diffSeconds < 15 -> nowText
                diffSeconds < 60 -> "1$minUnit"
                diffSeconds < 1800 -> "${diffSeconds / 60}$minUnit"
                else -> {
                    val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    timeSdf.format(expectedDate)
                }
            }
        } else {
            "?"
        }
    } catch (e: Exception) {
        try {
            expectedTimeStr.substring(11, 16)
        } catch (ex: Exception) {
            "?"
        }
    }
}
