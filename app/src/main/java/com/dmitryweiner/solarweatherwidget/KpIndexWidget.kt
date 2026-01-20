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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
                val url = URL("https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json")
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
                
                // Формат: массив массивов, первый элемент — заголовки
                // ["time_tag", "Kp", "a_running", "station_count"]
                // ["2026-01-13 00:00:00.000", "3.67", "22", "8"]
                
                // Пропускаем первый элемент (заголовки), начинаем с 1
                for (i in 1 until jsonArray.length()) {
                    val row = jsonArray.getJSONArray(i)
                    val timeTag = row.getString(0) // "2026-01-13 00:00:00.000"
                    val kpValue = row.getString(1).toDoubleOrNull() ?: 0.0
                    
                    dataList.add(KpData(timeTag, kpValue))
                    Log.d(TAG, "Data point: time=$timeTag, Kp=$kpValue")
                }
                
                // Берём последние 24 периода (3 дня)
                val result = if (dataList.size > 24) {
                    dataList.takeLast(24)
                } else {
                    dataList
                }
                
                connection.disconnect()
                Log.d(TAG, "Total periods: ${result.size}")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching data", e)
                throw e
            }
        }

        private fun createChartBitmap(data: List<KpData>, context: Context): Bitmap {
            Log.d(TAG, "Creating chart bitmap with ${data.size} data points")

            val width = 900
            val height = 420
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
                textSize = 18f
                isAntiAlias = true
            }

            val axisTextPaint = Paint().apply {
                color = 0xFFAAAAAA.toInt()
                textSize = 14f
                isAntiAlias = true
            }

            val dateTextPaint = Paint().apply {
                color = 0xFF8BC34A.toInt() // Зелёный для дат
                textSize = 14f
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
            val paddingLeft = 35f
            val paddingRight = 15f
            val paddingTop = 15f
            val paddingBottom = 110f // Увеличен отступ снизу для вертикальных подписей
            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom
            val barSpacing = 2f
            val barWidth = (chartWidth / data.size) - barSpacing
            val maxKp = 9.0 // Максимальное значение Kp индекса

            // Горизонтальные линии сетки и подписи оси Y
            for (i in 0..9) {
                val y = height - paddingBottom - (chartHeight / 9 * i)
                canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
                canvas.drawText(i.toString(), 10f, y + 5f, axisTextPaint)
            }

            // Формат для парсинга времени из API (формат: "2026-01-13 00:00:00.000")
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

            var lastDateStr = ""

            // Столбцы
            data.forEachIndexed { index, kpData ->
                val barHeight = (kpData.kpIndex / maxKp * chartHeight).toFloat()
                val x = paddingLeft + index * (barWidth + barSpacing)
                val y = height - paddingBottom - barHeight

                // Цвет в зависимости от значения Kp
                barPaint.color = when {
                    kpData.kpIndex < 4 -> 0xFF4CAF50.toInt() // Зелёный
                    kpData.kpIndex < 6 -> 0xFFFFC107.toInt() // Жёлтый
                    kpData.kpIndex < 8 -> 0xFFFF9800.toInt() // Оранжевый
                    else -> 0xFFF44336.toInt() // Красный
                }

                val rect = RectF(x, y, x + barWidth, height - paddingBottom)
                canvas.drawRect(rect, barPaint)

                // Парсим время для подписей оси X
                try {
                    val date = inputFormat.parse(kpData.timeTag)
                    if (date != null) {
                        val time = timeFormat.format(date)
                        val dateStr = dateFormat.format(date)
                        val hour = time.substring(0, 2).toIntOrNull() ?: 0

                        // Вертикальная подпись времени под каждым столбцом
                        val labelX = x + barWidth / 2
                        val labelY = height - paddingBottom + 15f
                        
                        canvas.save()
                        canvas.rotate(-90f, labelX, labelY)
                        canvas.drawText(time, labelX, labelY + 4f, axisTextPaint)
                        canvas.restore()

                        // Показываем дату под временем при смене дня или для первого элемента
                        if (dateStr != lastDateStr) {
                            canvas.save()
                            canvas.rotate(-90f, labelX, labelY + 55f)
                            canvas.drawText(dateStr, labelX, labelY + 59f, dateTextPaint)
                            canvas.restore()
                            lastDateStr = dateStr
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing time: ${kpData.timeTag}", e)
                }
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
