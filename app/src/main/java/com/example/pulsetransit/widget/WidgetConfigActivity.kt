package com.example.pulsetransit.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.widget.BoardWidgetSource
import com.example.pulsetransit.data.widget.pickWidgetPlace
import com.example.pulsetransit.theme.PulseTransitTheme
import com.example.pulsetransit.ui.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.android.ext.android.inject

/**
 * Sets which favourite stop a departures widget shows, or which destination a route widget routes
 * to. Optional (the widgets fall back to the first one) and reachable again by long-pressing the
 * widget, from Android 12. Before that the launcher opens it when the widget is placed, so the
 * result is OK from the start: backing out keeps the widget, on its default.
 */
class WidgetConfigActivity : ComponentActivity() {
    private val source: BoardWidgetSource by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId =
            intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val kind = WidgetKind.ofProvider(AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) {
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val places = if (kind == WidgetKind.ROUTE) source.destinations() else source.stops()
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)

        enableEdgeToEdge()
        lifecycleScope.launch {
            val configured = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)[BoardWidget.PLACE_ID]
            val selectedId = pickWidgetPlace(places, configured)?.id
            setContent {
                PulseTransitTheme {
                    PlacePicker(
                        isRoute = kind == WidgetKind.ROUTE,
                        places = places,
                        selectedId = selectedId,
                        onPick = { place ->
                            lifecycleScope.launch {
                                updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[BoardWidget.PLACE_ID] = place.id }
                                kind.widget().refresh(this@WidgetConfigActivity, glanceId)
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacePicker(
    isRoute: Boolean,
    places: List<StopPlace>,
    selectedId: String?,
    onPick: (StopPlace) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isRoute) Res.string.widget_choose_destination else Res.string.widget_choose_stop)) },
            )
        },
    ) { padding ->
        if (places.isEmpty()) {
            Text(stringResource(Res.string.widget_choose_none), modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(places, key = { it.id }) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = place.locality?.let { { Text(it) } },
                    trailingContent =
                        if (place.id == selectedId) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    modifier = Modifier.clickable { onPick(place) },
                )
                HorizontalDivider()
            }
        }
    }
}
