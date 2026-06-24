package com.neversoft.spotlight.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.neversoft.spotlight.R
import com.neversoft.spotlight.SearchActivity
import com.neversoft.spotlight.model.PrimaryFilter

/**
 * The 2x4 home-screen widget: a search bar plus the four primary filter chips.
 * Tapping the bar opens Spotlight focused for typing; tapping a chip opens it
 * pre-filtered to that category.
 */
class SpotlightWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, id))
        }
    }

    private fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_spotlight)

        // Search bar -> open focused for typing.
        views.setOnClickPendingIntent(
            R.id.widgetSearchBar,
            searchPendingIntent(context, widgetId, primary = null, focus = true),
        )

        // Each chip -> open pre-filtered.
        views.setOnClickPendingIntent(
            R.id.widgetFilterAll,
            searchPendingIntent(context, widgetId, PrimaryFilter.ALL, focus = true),
        )
        views.setOnClickPendingIntent(
            R.id.widgetFilterMedia,
            searchPendingIntent(context, widgetId, PrimaryFilter.MEDIA, focus = true),
        )
        views.setOnClickPendingIntent(
            R.id.widgetFilterFiles,
            searchPendingIntent(context, widgetId, PrimaryFilter.FILES, focus = true),
        )
        views.setOnClickPendingIntent(
            R.id.widgetFilterHidden,
            searchPendingIntent(context, widgetId, PrimaryFilter.HIDDEN, focus = true),
        )
        return views
    }

    private fun searchPendingIntent(
        context: Context,
        widgetId: Int,
        primary: PrimaryFilter?,
        focus: Boolean,
    ): PendingIntent {
        val key = primary?.name ?: "SEARCH"
        val intent = Intent(context, SearchActivity::class.java).apply {
            // Distinct data so each chip gets its own PendingIntent (extras alone
            // do not make PendingIntents unique).
            data = Uri.parse("spotlight://widget/$widgetId/$key")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (primary != null) putExtra(SearchActivity.EXTRA_PRIMARY, primary.name)
            putExtra(SearchActivity.EXTRA_FOCUS, focus)
        }
        val requestCode = widgetId * 31 + key.hashCode()
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
