package com.siegfried.clock365

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import com.siegfried.clock365.databinding.Clock365WidgetConfigureBinding

/**
 * The configuration screen for the {@link Clock365Widget Clock365Widget} AppWidget.
 */
class Clock365WidgetConfigureActivity : Activity() {

    companion object {
        private const val PREFS_NAME = "com.siegfried.clock365.Clock365Widget"
        private const val PREF_PREFIX_KEY = "appwidget_"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var binding: Clock365WidgetConfigureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = Clock365WidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Find the widget id from the intent.
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkPermission(Manifest.permission.READ_CALENDAR, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED) {
                proceedWithWidgetCreation()
                return
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                proceedWithWidgetCreation()
            } else {
                failWidgetCreation()
            }
        }
    }

    private fun proceedWithWidgetCreation() {
        // It is the responsibility of the configuration activity to update the app widget
        val appWidgetManager = AppWidgetManager.getInstance(this)
        Clock365Widget().updateAppWidget(this, appWidgetManager, appWidgetId)
        // Make sure we pass back the original appWidgetId
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private fun failWidgetCreation() {
        // Make sure we pass back the original appWidgetId
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, resultValue)
        finish()
    }
}
