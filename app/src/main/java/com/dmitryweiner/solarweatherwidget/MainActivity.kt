package com.dmitryweiner.solarweatherwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetProvider = ComponentName(this, KpIndexWidget::class.java)
        
        // Check if widget is already on the home screen
        val existingWidgets = appWidgetManager.getAppWidgetIds(widgetProvider)
        
        if (existingWidgets.isEmpty()) {
            // No widgets yet - offer to add one
            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(widgetProvider, null, null)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.widget_add_instruction),
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        } else {
            // Widget already exists - open settings for the first widget
            val intent = Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, existingWidgets.first())
            }
            startActivity(intent)
            finish()
        }
    }
}
