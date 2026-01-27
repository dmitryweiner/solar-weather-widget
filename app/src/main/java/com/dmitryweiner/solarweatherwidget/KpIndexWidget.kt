package com.dmitryweiner.solarweatherwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dmitryweiner.solarweatherwidget.data.DataError
import com.dmitryweiner.solarweatherwidget.data.SolarDataRepository
import com.dmitryweiner.solarweatherwidget.data.WidgetSettings
import com.dmitryweiner.solarweatherwidget.ui.ChartRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Clean up cached bitmaps and settings when widgets are deleted
        for (appWidgetId in appWidgetIds) {
            val cacheFile = File(context.cacheDir, "widget_chart_$appWidgetId.png")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            WidgetSettings.delete(context, appWidgetId)
        }
    }

    companion object {
        private const val TAG = "KpIndexWidget"
        const val ACTION_UPDATE = "com.example.kpwidget.ACTION_UPDATE"

        private val timeFormat by lazy {
            SimpleDateFormat("HH:mm", Locale.getDefault())
        }

        private fun getLocalizedErrorMessage(context: Context, error: Exception): String {
            return when (error) {
                is DataError.NoInternet -> context.getString(R.string.error_no_internet)
                is DataError.Timeout -> context.getString(R.string.error_timeout)
                is DataError.InvalidData -> context.getString(R.string.error_invalid_data)
                is DataError.ServerError -> context.getString(R.string.error_server)
                is DataError.Unknown -> context.getString(R.string.error_unknown)
                else -> context.getString(R.string.error_prefix, error.message ?: context.getString(R.string.error_unknown))
            }
        }

        private fun getCacheFile(context: Context, appWidgetId: Int): File {
            return File(context.cacheDir, "widget_chart_$appWidgetId.png")
        }

        private suspend fun saveBitmapToCache(context: Context, appWidgetId: Int, bitmap: Bitmap) {
            withContext(Dispatchers.IO) {
                try {
                    val file = getCacheFile(context, appWidgetId)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cache bitmap for widget $appWidgetId", e)
                }
            }
        }

        private suspend fun loadBitmapFromCache(context: Context, appWidgetId: Int): Bitmap? {
            return withContext(Dispatchers.IO) {
                try {
                    val file = getCacheFile(context, appWidgetId)
                    if (file.exists()) {
                        FileInputStream(file).use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load cached bitmap for widget $appWidgetId", e)
                    null
                }
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            widgetWidth: Int = 250,
            widgetHeight: Int = 110
        ) {
            val views = RemoteViews(context.packageName, R.layout.kp_index_widget)
            val settings = WidgetSettings.load(context, appWidgetId)

            val intent = Intent(context, KpIndexWidget::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // Adaptive layout based on widget height and user settings
            // Height <= 1 cell (~70dp): show only chart (override settings)
            // Height <= 2 cells (~110dp): hide title (override settings), show info row based on settings
            // Height > 2 cells: respect user settings
            when {
                widgetHeight <= 70 -> {
                    // Very small widget - show only chart
                    views.setViewVisibility(R.id.widget_title, View.GONE)
                    views.setViewVisibility(R.id.last_update, View.GONE)
                }
                widgetHeight <= 110 -> {
                    // Small widget - hide title, info row based on settings
                    views.setViewVisibility(R.id.widget_title, View.GONE)
                    views.setViewVisibility(
                        R.id.last_update,
                        if (settings.showInfoRow) View.VISIBLE else View.GONE
                    )
                }
                else -> {
                    // Normal size - respect user settings
                    views.setViewVisibility(
                        R.id.widget_title,
                        if (settings.showTitle) View.VISIBLE else View.GONE
                    )
                    views.setViewVisibility(
                        R.id.last_update,
                        if (settings.showInfoRow) View.VISIBLE else View.GONE
                    )
                }
            }

            val dataPointsCount = when {
                widgetWidth >= 300 -> 24
                widgetWidth >= 200 -> 16
                widgetWidth >= 120 -> 8
                else -> 4
            }

            CoroutineScope(Dispatchers.Main).launch {
                // First, load cached bitmap to show immediately (preserves data on resize)
                val cachedBitmap = loadBitmapFromCache(context, appWidgetId)
                if (cachedBitmap != null) {
                    views.setImageViewBitmap(R.id.chart_image, cachedBitmap)
                }
                views.setTextViewText(R.id.last_update, context.getString(R.string.loading))
                appWidgetManager.updateAppWidget(appWidgetId, views)

                try {
                    val data = SolarDataRepository.fetchData(settings.dataSource, dataPointsCount)
                    val bitmap = ChartRenderer.createChartBitmap(data, context, dataPointsCount)

                    // Save bitmap to cache for future use
                    saveBitmapToCache(context, appWidgetId, bitmap)

                    views.setImageViewBitmap(R.id.chart_image, bitmap)
                    views.setTextViewText(R.id.last_update, context.getString(R.string.updated_at, timeFormat.format(Date())))
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget update failed for appWidgetId=$appWidgetId: ${e.javaClass.simpleName} - ${e.message}", e)
                    val errorMessage = getLocalizedErrorMessage(context, e)
                    // Keep the cached bitmap (already set above), just update the error message
                    views.setTextViewText(R.id.last_update, errorMessage)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
