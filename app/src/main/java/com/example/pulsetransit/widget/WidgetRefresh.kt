package com.example.pulsetransit.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** The app's widgets, each with its receiver. */
enum class WidgetKind(
    val receiver: Class<out GlanceAppWidgetReceiver>,
) {
    DEPARTURES(DeparturesWidgetReceiver::class.java),
    ROUTE(RouteWidgetReceiver::class.java),
    GLANCE(GlanceWidgetReceiver::class.java),
    ;

    fun widget(): BoardWidget<*> =
        when (this) {
            DEPARTURES -> DeparturesWidget()
            ROUTE -> RouteWidget()
            GLANCE -> AtAGlanceWidget()
        }

    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()

    companion object {
        fun ofProvider(provider: ComponentName?): WidgetKind? = entries.firstOrNull { it.receiver.name == provider?.className }
    }
}

class DeparturesWidgetReceiver : BoardWidgetReceiver(WidgetKind.DEPARTURES)

class RouteWidgetReceiver : BoardWidgetReceiver(WidgetKind.ROUTE)

class GlanceWidgetReceiver : BoardWidgetReceiver(WidgetKind.GLANCE)

/** Keeps the periodic refresh scheduled while any widget is placed. */
abstract class BoardWidgetReceiver(
    kind: WidgetKind,
) : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = kind.widget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefresh.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresh.cancelIfUnused(context)
    }
}

/**
 * Refreshes every placed widget through WorkManager, every [PERIOD_MINUTES]: WorkManager's minimum,
 * and the most a home screen needs, since the widgets print clock times rather than countdowns and
 * drop departures that have left. The repositories' caches make each refresh cheap, and the weather
 * one keeps it from asking MET before its `Expires` time. No network constraint: offline, a refresh
 * still drops departed rows and says the data is from the cache.
 */
object WidgetRefresh {
    private const val PERIODIC = "widget-refresh"
    private const val ONCE = "widget-refresh-now"
    private const val PERIOD_MINUTES = 15L

    fun schedule(context: Context) {
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIOD_MINUTES, TimeUnit.MINUTES).build(),
            )
    }

    /** Refreshes the widgets now, if there are any: after the app was used, its settings may have changed. */
    fun refreshNow(context: Context) {
        if (WidgetKind.entries.none { it.hasWidgets(context) }) return
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }

    fun cancelIfUnused(context: Context) {
        if (WidgetKind.entries.any { it.hasWidgets(context) }) return
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** Reloads and redraws every placed widget. */
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        for (kind in WidgetKind.entries) {
            val widget = kind.widget()
            for (id in manager.getGlanceIds(widget.javaClass)) widget.refresh(context, id)
        }
    }
}

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetRefresh.refreshAll(applicationContext)
        return Result.success()
    }
}

/** The widgets' refresh button. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val kind = parameters[KIND]?.let { name -> WidgetKind.entries.firstOrNull { it.name == name } } ?: return
        kind.widget().refresh(context, glanceId)
    }

    companion object {
        val KIND = ActionParameters.Key<String>("kind")
    }
}
