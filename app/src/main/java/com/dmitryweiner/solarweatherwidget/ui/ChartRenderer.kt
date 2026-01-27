package com.dmitryweiner.solarweatherwidget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.dmitryweiner.solarweatherwidget.R
import com.dmitryweiner.solarweatherwidget.data.DataSource
import com.dmitryweiner.solarweatherwidget.data.DataSourceConfig
import com.dmitryweiner.solarweatherwidget.data.SolarData
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.log10
import kotlin.math.max

object ChartRenderer {
    private const val CHART_WIDTH = 900
    private const val CHART_HEIGHT = 420

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

    // Alternative date format for GOES data (with 'T' separator)
    private val inputDateFormatISO by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val outputDateFormat by lazy {
        SimpleDateFormat("dd.MM", Locale.getDefault())
    }

    /**
     * Create chart bitmap from SolarData (new unified format)
     */
    fun createChartBitmap(data: List<SolarData>, context: Context, dataPointsCount: Int = 24): Bitmap {
        val bitmap = Bitmap.createBitmap(CHART_WIDTH, CHART_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRect(0f, 0f, CHART_WIDTH.toFloat(), CHART_HEIGHT.toFloat(), bgPaint)

        if (data.isEmpty()) {
            canvas.drawText(context.getString(R.string.no_data), CHART_WIDTH / 2f - 50f, CHART_HEIGHT / 2f, textPaint)
            return bitmap
        }

        val dataSource = data.first().dataSource
        val useLogScale = DataSourceConfig.useLogScale(dataSource)
        val maxValue = DataSourceConfig.getMaxValue(dataSource)
        val minValue = DataSourceConfig.getMinValue(dataSource)

        val paddingLeft = 35f
        val paddingRight = 15f
        val paddingBottom = 45f
        val chartWidth = CHART_WIDTH - paddingLeft - paddingRight
        val chartHeight = CHART_HEIGHT - 15f - paddingBottom
        val barSpacing = 2f
        val barWidth = (chartWidth / data.size) - barSpacing

        // Draw grid lines
        drawGridLines(canvas, dataSource, paddingLeft, paddingRight, paddingBottom, chartHeight)

        val maxLabels = when {
            dataPointsCount >= 16 -> 5
            dataPointsCount >= 8 -> 4
            else -> 3
        }
        val labelInterval = maxOf(1, data.size / maxLabels)
        var lastDateStr = ""

        data.forEachIndexed { index, solarData ->
            val normalizedValue = if (useLogScale) {
                normalizeLogScale(solarData.value, minValue, maxValue)
            } else {
                (solarData.value / maxValue).coerceIn(0.0, 1.0)
            }
            
            val barHeight = (normalizedValue * chartHeight).toFloat()
            val x = paddingLeft + index * (barWidth + barSpacing)
            val y = CHART_HEIGHT - paddingBottom - barHeight

            barPaint.color = getColor(solarData.value, dataSource)

            val rect = RectF(x, y, x + barWidth, CHART_HEIGHT - paddingBottom)
            canvas.drawRect(rect, barPaint)

            if (index % labelInterval == 0) {
                drawDateLabel(canvas, solarData.timeTag, x, barWidth, paddingBottom, lastDateStr)?.let {
                    lastDateStr = it
                }
            }
        }

        return bitmap
    }

    private fun normalizeLogScale(value: Double, minValue: Double, maxValue: Double): Double {
        if (value <= 0) return 0.0
        val logMin = log10(minValue)
        val logMax = log10(maxValue)
        val logValue = log10(max(value, minValue))
        return ((logValue - logMin) / (logMax - logMin)).coerceIn(0.0, 1.0)
    }

    private fun drawGridLines(
        canvas: Canvas,
        dataSource: DataSource,
        paddingLeft: Float,
        paddingRight: Float,
        paddingBottom: Float,
        chartHeight: Float
    ) {
        when (dataSource) {
            DataSource.KP_INDEX -> {
                // Draw 0-9 scale for Kp index
                for (i in 0..9) {
                    val y = CHART_HEIGHT - paddingBottom - (chartHeight / 9 * i)
                    canvas.drawLine(paddingLeft, y, CHART_WIDTH - paddingRight, y, gridPaint)
                    canvas.drawText(i.toString(), 10f, y + 5f, axisTextPaint)
                }
            }
            DataSource.PROTON_FLUX, DataSource.XRAY_FLUX -> {
                // Draw log scale grid lines
                val gridLines = 5
                for (i in 0..gridLines) {
                    val y = CHART_HEIGHT - paddingBottom - (chartHeight / gridLines * i)
                    canvas.drawLine(paddingLeft, y, CHART_WIDTH - paddingRight, y, gridPaint)
                }
            }
        }
    }

    private fun drawDateLabel(
        canvas: Canvas,
        timeTag: String,
        x: Float,
        barWidth: Float,
        paddingBottom: Float,
        lastDateStr: String
    ): String? {
        try {
            // Try standard format first, then ISO format
            val date = try {
                inputDateFormat.parse(timeTag)
            } catch (_: Exception) {
                inputDateFormatISO.parse(timeTag)
            }
            
            if (date != null) {
                val dateStr = outputDateFormat.format(date)
                if (dateStr != lastDateStr) {
                    val labelX = x + barWidth / 2
                    val labelY = CHART_HEIGHT - paddingBottom + 28f
                    val textWidth = dateTextPaint.measureText(dateStr)
                    canvas.drawText(dateStr, labelX - textWidth / 2, labelY, dateTextPaint)
                    return dateStr
                }
            }
        } catch (_: Exception) {
            // Ignore parsing errors
        }
        return null
    }

    private fun getColor(value: Double, dataSource: DataSource): Int {
        return when (dataSource) {
            DataSource.KP_INDEX -> getKpColor(value)
            DataSource.PROTON_FLUX -> getProtonFluxColor(value)
            DataSource.XRAY_FLUX -> getXrayFluxColor(value)
        }
    }

    private fun getKpColor(kpIndex: Double): Int = when {
        kpIndex < 4 -> 0xFF4CAF50.toInt()  // Green - quiet
        kpIndex < 6 -> 0xFFFFC107.toInt()  // Yellow - moderate
        kpIndex < 8 -> 0xFFFF9800.toInt()  // Orange - active
        else -> 0xFFF44336.toInt()          // Red - storm
    }

    private fun getProtonFluxColor(flux: Double): Int {
        // Blue gradient based on flux level
        return when {
            flux < 1 -> 0xFF2196F3.toInt()      // Light blue - low
            flux < 10 -> 0xFF1976D2.toInt()     // Blue - moderate
            flux < 100 -> 0xFF1565C0.toInt()    // Dark blue - elevated
            flux < 1000 -> 0xFF0D47A1.toInt()   // Navy - high
            else -> 0xFF311B92.toInt()           // Deep purple - very high
        }
    }

    private fun getXrayFluxColor(flux: Double): Int {
        // Purple/magenta gradient based on flux level (flare classification)
        return when {
            flux < 1e-7 -> 0xFF9C27B0.toInt()   // Light purple - A/B class
            flux < 1e-6 -> 0xFF7B1FA2.toInt()   // Purple - C class
            flux < 1e-5 -> 0xFF6A1B9A.toInt()   // Dark purple - M class
            flux < 1e-4 -> 0xFFE91E63.toInt()   // Pink/Magenta - X class
            else -> 0xFFF44336.toInt()           // Red - extreme
        }
    }
}
