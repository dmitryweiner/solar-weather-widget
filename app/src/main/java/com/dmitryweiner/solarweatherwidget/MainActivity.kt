package com.dmitryweiner.solarweatherwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
        } else {
            // Widget already exists - show a message
            Toast.makeText(
                this,
                getString(R.string.widget_already_added),
                Toast.LENGTH_SHORT
            ).show()
        }
        
        // Close the activity immediately
        finish()
    }
}
