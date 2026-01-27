package com.dmitryweiner.solarweatherwidget.data

import android.content.Context

enum class DataSource {
    KP_INDEX,
    PROTON_FLUX,
    XRAY_FLUX
}

data class WidgetSettings(
    val showTitle: Boolean = true,
    val showInfoRow: Boolean = true,
    val dataSource: DataSource = DataSource.KP_INDEX
) {
    companion object {
        private const val PREFS_NAME = "widget_settings"
        private const val KEY_SHOW_TITLE = "show_title_"
        private const val KEY_SHOW_INFO_ROW = "show_info_row_"
        private const val KEY_DATA_SOURCE = "data_source_"

        fun load(context: Context, appWidgetId: Int): WidgetSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return WidgetSettings(
                showTitle = prefs.getBoolean(KEY_SHOW_TITLE + appWidgetId, true),
                showInfoRow = prefs.getBoolean(KEY_SHOW_INFO_ROW + appWidgetId, true),
                dataSource = DataSource.entries.getOrNull(
                    prefs.getInt(KEY_DATA_SOURCE + appWidgetId, 0)
                ) ?: DataSource.KP_INDEX
            )
        }

        fun save(context: Context, appWidgetId: Int, settings: WidgetSettings) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_SHOW_TITLE + appWidgetId, settings.showTitle)
                putBoolean(KEY_SHOW_INFO_ROW + appWidgetId, settings.showInfoRow)
                putInt(KEY_DATA_SOURCE + appWidgetId, settings.dataSource.ordinal)
                apply()
            }
        }

        fun delete(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove(KEY_SHOW_TITLE + appWidgetId)
                remove(KEY_SHOW_INFO_ROW + appWidgetId)
                remove(KEY_DATA_SOURCE + appWidgetId)
                apply()
            }
        }
    }
}
