package com.example.pulsetransit.widget

import android.content.Intent
import android.net.Uri
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
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import com.example.pulsetransit.theme.DarkLineColors
import com.example.pulsetransit.theme.LightLineColors
import com.example.pulsetransit.theme.PulseDarkColorScheme
import com.example.pulsetransit.theme.PulseLightColorScheme

/** Material You's colours from Android 12, where the launcher provides them; the app's own before. */
private val fallbackColors = ColorProviders(light = PulseLightColorScheme, dark = PulseDarkColorScheme)

@Composable
fun WidgetTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else fallbackColors
    GlanceTheme(colors = colors, content = content)
}

/**
 * The widget's rounded card; a tap anywhere but the refresh button opens the app, on the board or,
 * for a widget missing a stop or destination, on the screen that adds one ([target]).
 */
@Composable
fun WidgetCard(
    target: WidgetTarget = WidgetTarget.BOARD,
    content: @Composable ColumnScope.() -> Unit,
) {
    val intent =
        Intent(LocalContext.current, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, target.name)
            // One intent per target, or the system hands every widget the first one's extras.
            .setData(Uri.parse("pulsetransit://widget/${target.name.lowercase()}"))
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity(intent)),
        content = content,
    )
}

/**
 * A title with the refresh button at its end; [titleLines] lets a narrow widget wrap a long name
 * rather than cut it. The icon is drawn at 20 dp in a 48 dp target.
 */
@Composable
fun WidgetHeader(
    title: String,
    kind: WidgetKind,
    refreshDescription: String,
    titleLines: Int = 1,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = title,
            modifier = GlanceModifier.defaultWeight().padding(top = 2.dp),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = titleLines,
        )
        Box(
            modifier =
                GlanceModifier
                    .size(32.dp)
                    .clickable(actionRunCallback<RefreshAction>(actionParametersOf(RefreshAction.KIND to kind.name))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = refreshDescription,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }
}

/** How old the data is, at the bottom; with the offline icon, in the error colour, when it is not live. */
@Composable
fun WidgetStatus(status: StatusView) {
    val color = if (status.isWarning) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status.isWarning) {
            WidgetIconImage(WidgetIcon.OFFLINE, color, 14)
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(text = status.text, style = TextStyle(color = color, fontSize = 11.sp), maxLines = 1)
    }
}

/** A message in place of the widget's data (T5): an icon, what happened, and what to do. */
@Composable
fun WidgetMessage(
    message: MessageView,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        WidgetIconImage(message.icon, GlanceTheme.colors.onSurfaceVariant, 20)
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = message.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 2,
            )
            message.body?.let {
                Text(text = it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 3)
            }
        }
    }
}

/** One of the bundled icons, tinted. */
@Composable
fun WidgetIconImage(
    icon: WidgetIcon,
    tint: ColorProvider,
    sizeDp: Int,
) {
    Image(
        provider = ImageProvider(icon.drawable),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = GlanceModifier.size(sizeDp.dp),
    )
}

/** A route's legs as a chain: "[walk] 6 min › R14 › FLY2"; clipped, not wrapped, when it does not fit. */
@Composable
fun LegChain(legs: List<LegView>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        legs.forEachIndexed { index, leg ->
            if (index > 0) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_chevron_right),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
            val line = leg.line
            if (line != null) {
                LineBadge(line, leg.transportMode)
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_directions_walk),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                    modifier = GlanceModifier.size(16.dp),
                )
                Text(text = leg.walk.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.width(2.dp))
        }
    }
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
                .background(
                    if (cancelled) {
                        ColorProvider(Color.Gray)
                    } else {
                        ColorProvider(day = LightLineColors.forMode(transportMode), night = DarkLineColors.forMode(transportMode))
                    },
                ).padding(horizontal = 6.dp, vertical = 2.dp),
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
