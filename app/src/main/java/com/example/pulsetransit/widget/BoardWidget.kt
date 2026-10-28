package com.example.pulsetransit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.pulsetransit.R
import com.example.pulsetransit.data.widget.BoardWidgetSource
import com.example.pulsetransit.data.widget.WidgetFreshness
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.ui.resources.*
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * A widget that shows a view of type [V], loaded by [load] and kept as JSON in its Glance state
 * (see `WidgetViews.kt`). [refresh] loads a new one and redraws; the first draw loads one itself
 * when the stored one is missing or older than [RELOAD_AFTER_MILLIS].
 */
abstract class BoardWidget<V : Any> :
    GlanceAppWidget(),
    KoinComponent {
    protected val source: BoardWidgetSource by inject()
    protected val clockFormatter: ClockFormatter by inject()

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // The layouts adapt to any size, by LocalSize, rather than to a few fixed buckets.
    override val sizeMode: SizeMode = SizeMode.Exact

    /** Builds the view from fresh data; [configuredId] is the stop or destination the widget was set to. */
    protected abstract suspend fun load(
        context: Context,
        configuredId: String?,
    ): V

    protected abstract fun encode(view: V): String

    protected abstract fun decode(json: String): V

    @Composable
    protected abstract fun Content(view: V?)

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val loadedAt = state[LOADED_AT]
        if (state[VIEW] == null || loadedAt == null || System.currentTimeMillis() - loadedAt > RELOAD_AFTER_MILLIS) {
            store(context, id)
        }
        provideContent {
            val json = currentState<Preferences>()[VIEW]
            WidgetTheme { Content(json?.let { runCatching { decode(it) }.getOrNull() }) }
        }
    }

    /** Loads fresh data and redraws the widget [id]. */
    suspend fun refresh(
        context: Context,
        id: GlanceId,
    ) {
        store(context, id)
        update(context, id)
    }

    private suspend fun store(
        context: Context,
        id: GlanceId,
    ) {
        val configuredId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[PLACE_ID]
        val json = encode(load(context, configuredId))
        updateAppWidgetState(context, id) { prefs ->
            prefs[VIEW] = json
            prefs[LOADED_AT] = System.currentTimeMillis()
        }
    }

    protected fun time(epochMillis: Long): String = clockFormatter.clockText(epochMillis).time

    protected suspend fun labels() = WidgetLabels(getString(Res.string.refresh), getString(Res.string.widget_realtime))

    protected fun appName(context: Context): String = context.getString(R.string.app_name)

    /** The footer line: when the data is from, and whether that is a problem. */
    protected suspend fun status(freshness: WidgetFreshness): Pair<String, Boolean> {
        val stale = freshness.staleSinceMillis
        return when {
            freshness.failed -> getString(Res.string.widget_unreachable) to true
            stale != null -> getString(Res.string.widget_offline_since, time(stale)) to true
            else -> getString(Res.string.widget_updated, time(System.currentTimeMillis())) to false
        }
    }

    companion object {
        /** The stop or destination id the widget was set to in [WidgetConfigActivity]. */
        val PLACE_ID = stringPreferencesKey("place_id")
        private val VIEW = stringPreferencesKey("view")
        private val LOADED_AT = longPreferencesKey("loaded_at")

        /** A session restart within this of the last load redraws without asking the network. */
        private const val RELOAD_AFTER_MILLIS = 5 * 60_000L

        val json = Json { ignoreUnknownKeys = true }
    }
}
