package com.example.pulsetransit.journey

import com.example.pulsetransit.data.journey.JourneyAlert
import com.example.pulsetransit.data.journey.JourneyProgress
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.WatchText
import com.example.pulsetransit.platform.WatchWording
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.getString

/** Words a journey for the watch exactly as the notifications word it. */
internal class AndroidWatchWording(
    clockFormatter: ClockFormatter,
) : WatchWording {
    private val progressContent = JourneyProgressContent(clockFormatter)
    private val alertContent = JourneyAlertContent(clockFormatter)

    override suspend fun title(progress: JourneyProgress): String = getString(Res.string.journey_progress_title, progress.destinationName)

    override suspend fun progress(progress: JourneyProgress): WatchText = WatchText(progressContent.headline(progress), progressContent.details(progress))

    override suspend fun alert(alert: JourneyAlert): WatchText = alertContent.text(alert).let { (title, text) -> WatchText(title, text) }
}
