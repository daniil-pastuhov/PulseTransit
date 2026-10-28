package com.example.pulsetransit.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.pulsetransit.MainActivity
import com.example.pulsetransit.R
import com.example.pulsetransit.theme.PulseDarkColorScheme
import com.example.pulsetransit.theme.PulseLightColorScheme
import com.example.pulsetransit.ui.components.getTransportColor

/** Material You's colours from Android 12, where the launcher provides them; the app's own before. */
private val fallbackColors = ColorProviders(light = PulseLightColorScheme, dark = PulseDarkColorScheme)

@Composable
fun WidgetTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else fallbackColors
    GlanceTheme(colors = colors, content = content)
}

/** The widget's rounded card; a tap anywhere but the refresh button opens the board. */
@Composable
fun WidgetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        content = content,
    )
}

/** A title with the refresh button at its end. */
@Composable
fun WidgetHeader(
    title: String,
    kind: WidgetKind,
    refreshDescription: String,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = refreshDescription,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier =
                GlanceModifier
                    .size(20.dp)
                    .clickable(actionRunCallback<RefreshAction>(actionParametersOf(RefreshAction.KIND to kind.name))),
        )
    }
}

/** When the data is from, at the bottom; in the error colour when it is not live. */
@Composable
fun WidgetStatus(
    text: String,
    isWarning: Boolean,
) {
    Text(
        text = text,
        style =
            TextStyle(
                color = if (isWarning) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
            ),
        maxLines = 1,
    )
}

/** A message in place of the widget's data. */
@Composable
fun WidgetMessage(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        maxLines = 3,
    )
}

/** The line's number on its transport mode's colour, as on the board. */
@Composable
fun LineBadge(
    line: String,
    transportMode: String?,
    cancelled: Boolean = false,
) {
    Box(
        modifier =
            GlanceModifier
                .cornerRadius(6.dp)
                .background(ColorProvider(if (cancelled) Color.Gray else getTransportColor(transportMode)))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = line,
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/**
 * A departure's clock time: bold in the accent colour with a dot when it is from realtime data,
 * plain when it is only the timetable's, struck through when cancelled.
 */
@Composable
fun DepartureTime(
    time: TimeView,
    realtimeDescription: String,
    emphasized: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (time.isRealtime && !time.isCancelled) {
            Box(
                modifier =
                    GlanceModifier
                        .size(6.dp)
                        .cornerRadius(3.dp)
                        .background(GlanceTheme.colors.primary)
                        .semantics { contentDescription = realtimeDescription },
            ) {}
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(
            text = time.text,
            style =
                TextStyle(
                    color = if (time.isRealtime) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
                    fontSize = if (emphasized) 15.sp else 13.sp,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (time.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                ),
            maxLines = 1,
        )
    }
}

@Composable
fun VerticalGap(height: Int) {
    Spacer(GlanceModifier.height(height.dp))
}
