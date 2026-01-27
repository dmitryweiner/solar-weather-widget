package com.dmitryweiner.solarweatherwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import com.dmitryweiner.solarweatherwidget.data.DataSource
import com.dmitryweiner.solarweatherwidget.data.WidgetSettings

class WidgetConfigureActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var checkboxShowTitle: CheckBox
    private lateinit var checkboxShowInfo: CheckBox
    private lateinit var radioGroup: RadioGroup
    private lateinit var radioKp: RadioButton
    private lateinit var radioProton: RadioButton
    private lateinit var radioXray: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case user backs out
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_widget_configure)

        // Find the widget ID from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If no valid widget ID, finish
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Initialize views
        checkboxShowTitle = findViewById(R.id.checkbox_show_title)
        checkboxShowInfo = findViewById(R.id.checkbox_show_info)
        radioGroup = findViewById(R.id.radio_group_data_source)
        radioKp = findViewById(R.id.radio_kp)
        radioProton = findViewById(R.id.radio_proton)
        radioXray = findViewById(R.id.radio_xray)

        // Load existing settings (if reconfiguring)
        loadSettings()

        // Set up button listeners
        findViewById<Button>(R.id.button_save).setOnClickListener {
            saveSettingsAndFinish()
        }

        findViewById<Button>(R.id.button_cancel).setOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        val settings = WidgetSettings.load(this, appWidgetId)

        checkboxShowTitle.isChecked = settings.showTitle
        checkboxShowInfo.isChecked = settings.showInfoRow

        when (settings.dataSource) {
            DataSource.KP_INDEX -> radioKp.isChecked = true
            DataSource.PROTON_FLUX -> radioProton.isChecked = true
            DataSource.XRAY_FLUX -> radioXray.isChecked = true
        }
    }

    private fun saveSettingsAndFinish() {
        val dataSource = when (radioGroup.checkedRadioButtonId) {
            R.id.radio_kp -> DataSource.KP_INDEX
            R.id.radio_proton -> DataSource.PROTON_FLUX
            R.id.radio_xray -> DataSource.XRAY_FLUX
            else -> DataSource.KP_INDEX
        }

        val settings = WidgetSettings(
            showTitle = checkboxShowTitle.isChecked,
            showInfoRow = checkboxShowInfo.isChecked,
            dataSource = dataSource
        )

        WidgetSettings.save(this, appWidgetId, settings)

        // Update the widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        KpIndexWidget.updateAppWidget(this, appWidgetManager, appWidgetId)

        // Return success
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}
