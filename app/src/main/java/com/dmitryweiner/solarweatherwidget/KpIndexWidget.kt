package com.dmitryweiner.solarweatherwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class KpIndexWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
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
        const val ACTION_UPDATE = "com.dmitryweiner.kpwidget.ACTION_UPDATE"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.kp_index_widget)

            // Настройка обновления по клику
            val intent = Intent(context, KpIndexWidget::class.java).apply {
                action = ACTION_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Загрузка данных
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val data = fetchKpData()
                    val bitmap = createChartBitmap(data, context)
                    views.setImageViewBitmap(R.id.chart_image, bitmap)
                    views.setTextViewText(R.id.last_update, "Обновлено: ${getCurrentTime()}")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    views.setTextViewText(R.id.last_update, "Ошибка загрузки")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private suspend fun fetchKpData(): List<KpData> = withContext(Dispatchers.IO) {
            val url = URL("https://services.swpc.noaa.gov/json/planetary_k_index_1m.json")
            val jsonString = url.readText()
            val jsonArray = JSONArray(jsonString)

            val dataList = mutableListOf<KpData>()
            // Берем последние 12 записей
            val startIndex = maxOf(0, jsonArray.length() - 12)
            for (i in startIndex until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                dataList.add(
                    KpData(
                        timeTag = obj.getString("time_tag"),
                        kpIndex = obj.getDouble("Kp")
                    )
                )
            }
            dataList
        }

        private fun createChartBitmap(data: List<KpData>, _context: Context): Bitmap {
            val width = 800
            val height = 400
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Настройка красок
            val bgPaint = Paint().apply {
                color = 0xFF1E1E1E.toInt()
                style = Paint.Style.FILL
            }

            val barPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 24f
                isAntiAlias = true
            }

            val gridPaint = Paint().apply {
                color = 0xFF444444.toInt()
                strokeWidth = 1f
            }

            // Фон
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            if (data.isEmpty()) {
                canvas.drawText("Нет данных", width / 2f - 50f, height / 2f, textPaint)
                return bitmap
            }

            // Параметры графика
            val padding = 60f
            val chartWidth = width - 2 * padding
            val chartHeight = height - 2 * padding
            val barWidth = chartWidth / data.size - 10f
            val maxKp = 9.0 // Максимальное значение Kp индекса

            // Сетка
            for (i in 0..9) {
                val y = height - padding - (chartHeight / 9 * i)
                canvas.drawLine(padding, y, width - padding, y, gridPaint)
                canvas.drawText(i.toString(), 10f, y + 5f, textPaint)
            }

            // Столбцы
            data.forEachIndexed { index, kpData ->
                val barHeight = (kpData.kpIndex / maxKp * chartHeight).toFloat()
                val x = padding + index * (barWidth + 10f)
                val y = height - padding - barHeight

                // Цвет в зависимости от значения Kp
                barPaint.color = when {
                    kpData.kpIndex < 4 -> 0xFF4CAF50.toInt() // Зеленый
                    kpData.kpIndex < 6 -> 0xFFFFC107.toInt() // Желтый
                    kpData.kpIndex < 8 -> 0xFFFF9800.toInt() // Оранжевый
                    else -> 0xFFF44336.toInt() // Красный
                }

                val rect = RectF(x, y, x + barWidth, height - padding)
                canvas.drawRect(rect, barPaint)

                // Значение над столбцом
                val valueText = String.format("%.1f", kpData.kpIndex)
                canvas.drawText(valueText, x + barWidth / 2 - 15f, y - 5f, textPaint)
            }

            return bitmap
        }

        private fun getCurrentTime(): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}

data class KpData(
    val timeTag: String,
    val kpIndex: Double
)