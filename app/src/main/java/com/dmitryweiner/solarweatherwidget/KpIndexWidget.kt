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
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection

class KpIndexWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")
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

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget called for widget $appWidgetId")
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

            // Показываем статус загрузки
            views.setTextViewText(R.id.last_update, "Загрузка...")
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Загрузка данных
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d(TAG, "Starting data fetch...")
                    val data = fetchKpData()
                    Log.d(TAG, "Data fetched successfully: ${data.size} items")

                    val bitmap = createChartBitmap(data, context)
                    Log.d(TAG, "Chart bitmap created")

                    views.setImageViewBitmap(R.id.chart_image, bitmap)
                    views.setTextViewText(R.id.last_update, "Обновлено: ${getCurrentTime()}")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    Log.d(TAG, "Widget updated successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget", e)
                    views.setTextViewText(R.id.last_update, "Ошибка: ${e.message}")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private suspend fun fetchKpData(): List<KpData> = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching data from NOAA API...")
                val url = URL("https://services.swpc.noaa.gov/json/planetary_k_index_1m.json")
                val connection = url.openConnection() as HttpsURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "KpIndexWidget/1.0")
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")

                if (responseCode != 200) {
                    throw Exception("HTTP error: $responseCode")
                }

                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "JSON received, length: ${jsonString.length}")

                val jsonArray = JSONArray(jsonString)
                Log.d(TAG, "JSON parsed, array length: ${jsonArray.length()}")

                val dataList = mutableListOf<KpData>()
                // Берем последние 8 записей (по одной на каждый 3-часовой период)
                // Данные обновляются каждую минуту, поэтому берем каждую 180-ю запись
                val totalRecords = jsonArray.length()
                val recordsToTake = 8
                val step = 180 // 3 часа = 180 минут

                // Берем данные с интервалом в 3 часа (180 минут)
                for (i in 0 until recordsToTake) {
                    val index = maxOf(0, totalRecords - (recordsToTake - i) * step)
                    if (index < totalRecords) {
                        val obj = jsonArray.getJSONObject(index)
                        val timeTag = obj.getString("time_tag")
                        // Используем estimated_kp для более точных значений, fallback на kp_index
                        val kpIndex = if (obj.has("estimated_kp")) {
                            obj.getDouble("estimated_kp")
                        } else {
                            obj.getInt("kp_index").toDouble()
                        }
                        dataList.add(KpData(timeTag, kpIndex))
                        Log.d(TAG, "Data point: time=$timeTag, Kp=$kpIndex")
                    }
                }

                connection.disconnect()
                dataList
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching data", e)
                throw e
            }
        }

        private fun createChartBitmap(data: List<KpData>, context: Context): Bitmap {
            Log.d(TAG, "Creating chart bitmap with ${data.size} data points")

            val width = 800
            val height = 300  // Уменьшена высота для более компактного виджета
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
                textSize = 20f  // Уменьшен размер текста
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
                Log.w(TAG, "No data to display")
                return bitmap
            }

            // Параметры графика
            val padding = 50f  // Уменьшен отступ
            val chartWidth = width - 2 * padding
            val chartHeight = height - 2 * padding
            val barWidth = chartWidth / data.size - 8f  // Уменьшен отступ между столбцами
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
                val x = padding + index * (barWidth + 8f)
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
                val textWidth = textPaint.measureText(valueText)
                canvas.drawText(valueText, x + barWidth / 2 - textWidth / 2, y - 5f, textPaint)
            }

            Log.d(TAG, "Chart bitmap created successfully")
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
