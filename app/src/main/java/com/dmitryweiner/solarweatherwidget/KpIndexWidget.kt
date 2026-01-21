package com.dmitryweiner.solarweatherwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dmitryweiner.solarweatherwidget.data.KpDataRepository
import com.dmitryweiner.solarweatherwidget.ui.ChartRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KpIndexWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            updateAppWidget(context, appWidgetManager, appWidgetId, minWidth, minHeight)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        updateAppWidget(context, appWidgetManager, appWidgetId, minWidth, minHeight)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, KpIndexWidget::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        private const val TAG = "KpIndexWidget"
        const val ACTION_UPDATE = "com.example.kpwidget.ACTION_UPDATE"

        private val timeFormat by lazy {
            SimpleDateFormat("HH:mm", Locale.getDefault())
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            widgetWidth: Int = 250,
            widgetHeight: Int = 110
        ) {
            val views = RemoteViews(context.packageName, R.layout.kp_index_widget)

            val intent = Intent(context, KpIndexWidget::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            if (widgetHeight < 80) {
                views.setViewVisibility(R.id.widget_title, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_title, View.VISIBLE)
            }

            val dataPointsCount = when {
                widgetWidth >= 300 -> 24
                widgetWidth >= 200 -> 16
                widgetWidth >= 120 -> 8
                else -> 4
            }

            views.setTextViewText(R.id.last_update, context.getString(R.string.loading))
            appWidgetManager.updateAppWidget(appWidgetId, views)

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val data = KpDataRepository.fetchKpData(dataPointsCount)
                    val bitmap = ChartRenderer.createChartBitmap(data, context, dataPointsCount)

                    views.setImageViewBitmap(R.id.chart_image, bitmap)
                    views.setTextViewText(R.id.last_update, context.getString(R.string.updated_at, timeFormat.format(Date())))
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget update failed for appWidgetId=$appWidgetId: ${e.javaClass.simpleName} - ${e.message}", e)
                    views.setTextViewText(R.id.last_update, context.getString(R.string.error_prefix, e.message))
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
