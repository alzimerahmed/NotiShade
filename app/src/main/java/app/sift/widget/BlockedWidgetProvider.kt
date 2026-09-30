package app.sift.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.RemoteViews
import app.sift.App
import app.sift.R
import app.sift.data.Outcome
import app.sift.ui.MainActivity

/**
 * Classic RemoteViews widget showing the most recent blocked (or rule-removed) notifications.
 * Updated directly from the listener's background path — no WorkManager, no polling.
 */
class BlockedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, manager, appWidgetIds)
    }

    companion object {
        private const val MAX_ROWS = 5

        /** Safe to call from anywhere; no-op when no widget is pinned. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BlockedWidgetProvider::class.java))
            if (ids.isEmpty()) return
            render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val blocked = App.of(context).history.entries.value.filter { it.outcome != Outcome.SHOWN }
            val rv = RemoteViews(context.packageName, R.layout.widget_blocked)
            rv.removeAllViews(R.id.widget_list)
            rv.setTextViewText(R.id.widget_count, if (blocked.isEmpty()) "" else context.resources.getQuantityString(R.plurals.widget_count, blocked.size, blocked.size))
            if (blocked.isEmpty()) {
                rv.setViewVisibility(R.id.widget_list, android.view.View.GONE)
                rv.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.widget_list, android.view.View.VISIBLE)
                rv.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
                blocked.take(5).forEach { e ->
                    val row = RemoteViews(context.packageName, R.layout.widget_blocked_row)
                    row.setTextViewText(R.id.row_title, e.title.ifBlank { e.text.ifBlank { e.app } })
                    row.setTextViewText(
                        R.id.widget_row_meta,
                        listOf(e.app, DateUtils.getRelativeTimeSpanString(e.time)).joinToString(" · "),
                    )
                    rv.addView(R.id.widget_list, row)
                }
            }
            // Tapping anywhere on the widget opens the app (Logs is one tap away from there).
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.widget_root, pi)
            rv.setContentDescription(R.id.widget_root, context.getString(R.string.widget_label))
            manager.updateAppWidget(ids, rv)
        }
    }
}
