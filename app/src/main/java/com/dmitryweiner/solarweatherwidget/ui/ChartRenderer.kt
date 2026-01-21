package com.dmitryweiner.solarweatherwidget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.dmitryweiner.solarweatherwidget.R
import com.dmitryweiner.solarweatherwidget.data.KpData
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ChartRenderer {
    private const val CHART_WIDTH = 900
    private const val CHART_HEIGHT = 420
    private const val MAX_KP = 9.0

    private val bgPaint by lazy {
        Paint().apply {
            color = 0xFF1E1E1E.toInt()
            style = Paint.Style.FILL
        }
    }

    private val barPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    private val textPaint by lazy {
        Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 18f
            isAntiAlias = true
        }
    }

    private val axisTextPaint by lazy {
        Paint().apply {
            color = 0xFFAAAAAA.toInt()
            textSize = 20f
            isAntiAlias = true
        }
    }

    private val dateTextPaint by lazy {
        Paint().apply {
            color = 0xFF8BC34A.toInt()
            textSize = 24f
            isAntiAlias = true
        }
    }

    private val gridPaint by lazy {
        Paint().apply {
            color = 0xFF444444.toInt()
            strokeWidth = 1f
        }
    }

    private val inputDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val outputDateFormat by lazy {
        SimpleDateFormat("dd.MM", Locale.getDefault())
    }

    fun createChartBitmap(data: List<KpData>, context: Context, dataPointsCount: Int = 24): Bitmap {
        val bitmap = Bitmap.createBitmap(CHART_WIDTH, CHART_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRect(0f, 0f, CHART_WIDTH.toFloat(), CHART_HEIGHT.toFloat(), bgPaint)

        if (data.isEmpty()) {
            canvas.drawText(context.getString(R.string.no_data), CHART_WIDTH / 2f - 50f, CHART_HEIGHT / 2f, textPaint)
            return bitmap
        }

        val paddingLeft = 35f
        val paddingRight = 15f
        val paddingBottom = 45f
        val chartWidth = CHART_WIDTH - paddingLeft - paddingRight
        val chartHeight = CHART_HEIGHT - 15f - paddingBottom
        val barSpacing = 2f
        val barWidth = (chartWidth / data.size) - barSpacing

        for (i in 0..9) {
            val y = CHART_HEIGHT - paddingBottom - (chartHeight / 9 * i)
            canvas.drawLine(paddingLeft, y, CHART_WIDTH - paddingRight, y, gridPaint)
            canvas.drawText(i.toString(), 10f, y + 5f, axisTextPaint)
        }

        val maxLabels = when {
            dataPointsCount >= 16 -> 5
            dataPointsCount >= 8 -> 4
            else -> 3
        }
        val labelInterval = maxOf(1, data.size / maxLabels)
        var lastDateStr = ""

        data.forEachIndexed { index, kpData ->
            val barHeight = (kpData.kpIndex / MAX_KP * chartHeight).toFloat()
            val x = paddingLeft + index * (barWidth + barSpacing)
            val y = CHART_HEIGHT - paddingBottom - barHeight

            barPaint.color = getKpColor(kpData.kpIndex)

            val rect = RectF(x, y, x + barWidth, CHART_HEIGHT - paddingBottom)
            canvas.drawRect(rect, barPaint)

            if (index % labelInterval == 0) {
                try {
                    val date = inputDateFormat.parse(kpData.timeTag)
                    if (date != null) {
                        val dateStr = outputDateFormat.format(date)
                        if (dateStr != lastDateStr) {
                            val labelX = x + barWidth / 2
                            val labelY = CHART_HEIGHT - paddingBottom + 28f
                            val textWidth = dateTextPaint.measureText(dateStr)
                            canvas.drawText(dateStr, labelX - textWidth / 2, labelY, dateTextPaint)
                            lastDateStr = dateStr
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        return bitmap
    }

    private fun getKpColor(kpIndex: Double): Int = when {
        kpIndex < 4 -> 0xFF4CAF50.toInt()
        kpIndex < 6 -> 0xFFFFC107.toInt()
        kpIndex < 8 -> 0xFFFF9800.toInt()
        else -> 0xFFF44336.toInt()
    }
}
