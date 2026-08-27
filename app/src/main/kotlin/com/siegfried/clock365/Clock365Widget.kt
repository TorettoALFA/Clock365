package com.siegfried.clock365

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link Clock365WidgetConfigureActivity Clock365WidgetConfigureActivity}
 */
class Clock365Widget : AppWidgetProvider() {

    companion object {
        // Guards against a burst of lifecycle/broadcast events firing concurrent updates;
        // the Clock365RemoteViewsService clears this whenever it refreshes.
        var updatePending = false

        private const val TAG = "Clock365WidgetProvider"
        const val ACTION_REFRESH = "com.siegfried.clock365.ACTION_REFRESH"

        private fun clickIntent(context: Context, packageOrIntent: Intent?, flag: Int): PendingIntent? =
            try {
                PendingIntent.getActivity(context, 0, packageOrIntent, PendingIntent.FLAG_UPDATE_CURRENT or flag)
            } catch (t: Throwable) {
                Log.v(TAG, "Failed to register click intent (no app?)")
                null
            }

        private fun immutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            Log.v(TAG, "updateAppWidget: $appWidgetId")
            // Construct the RemoteViews object
            val views = RemoteViews(context.packageName, R.layout.clock365_widget)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            var hideAlarm = false
            var hideCalendar = false
            if (options != null) {
                val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                Log.v(TAG, "Resize: ${w}x$h")
                val anchor = min(w, h)
                var clockFontScale = max(0.4f, min(1f, anchor / 275f))
                var dateFontScale = max(0.7f, min(1f, anchor / 275f))
                if (w < 220) {
                    hideAlarm = true
                    dateFontScale = min(1f, dateFontScale * 1.25f)
                }
                if (h < 80) {
                    hideCalendar = true
                    clockFontScale = min(1f, clockFontScale * 1.5f)
                    dateFontScale = min(1f, dateFontScale * 1.25f)
                }
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                    clockFontScale = clockFontScale * 0.8f
                }
                if (DateFormat.is24HourFormat(context)) {
                    views.setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_DIP, 80 * clockFontScale)
                } else {
                    views.setTextViewTextSize(R.id.clock, TypedValue.COMPLEX_UNIT_DIP, 60 * clockFontScale)
                }
                views.setTextViewTextSize(R.id.date, TypedValue.COMPLEX_UNIT_DIP, 18 * dateFontScale)
                views.setTextViewTextSize(R.id.alarm, TypedValue.COMPLEX_UNIT_DIP, 18 * dateFontScale)
            }
            val pm = context.packageManager
            val clockApp = clickIntent(context, pm.getLaunchIntentForPackage("com.android.deskclock"), immutableFlag())
            if (clockApp != null) views.setOnClickPendingIntent(R.id.clock, clockApp)
            val calendarApp = clickIntent(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR), immutableFlag())
            if (calendarApp != null) views.setOnClickPendingIntent(R.id.calendar_icon, calendarApp)
            updateAlarm(context, pm, views)
            updateCalendarContainer(context, appWidgetManager, views)
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (t: Throwable) {
            Log.e(TAG, "Internal error: $t")
        }
    }

    private fun updateAlarm(context: Context, pm: PackageManager, views: RemoteViews) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmClock = am.getNextAlarmClock()
        if (alarmClock != null) {
            views.setViewVisibility(R.id.alarm, View.VISIBLE)
            val alarm = if (DateFormat.is24HourFormat(context)) {
                DateFormat.format("⏰ E HH:mm", alarmClock.getTriggerTime())
            } else {
                DateFormat.format("⏰ E hh:mma", alarmClock.getTriggerTime())
            }
            views.setTextViewText(R.id.alarm, alarm)
            val clockApp = clickIntent(context, pm.getLaunchIntentForPackage("com.android.deskclock"), immutableFlag())
            if (clockApp != null) views.setOnClickPendingIntent(R.id.alarm, clockApp)
        } else {
            views.setViewVisibility(R.id.alarm, View.GONE)
        }
    }

    private fun updateCalendarContainer(context: Context, appWidgetManager: AppWidgetManager, views: RemoteViews) {
        views.setViewVisibility(R.id.calendar_container, View.VISIBLE)
        views.setRemoteAdapter(R.id.calendar, Intent(context, Clock365RemoteViewsService::class.java))
        val eventClickTemplate = Intent(Intent.ACTION_VIEW)
        val eventClickPendingIntent = PendingIntent.getActivity(
            context, 0,
            eventClickTemplate,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        views.setPendingIntentTemplate(R.id.calendar, eventClickPendingIntent)
        appWidgetManager.notifyAppWidgetViewDataChanged(AppWidgetManager.INVALID_APPWIDGET_ID, R.id.calendar)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.v(TAG, "onUpdate")
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        onUpdate(context, AppWidgetManager.getInstance(context), AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, Clock365Widget::class.java)))
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}
