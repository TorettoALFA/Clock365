package com.siegfried.clock365

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Time
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Locale

class Clock365RemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {
        return Clock365RemoteViewsFactory(applicationContext)
    }

    class Clock365RemoteViewsFactory(private val context: Context) : RemoteViewsFactory {

        private val tag = "CalendarSvc"

        private class CalendarListEntry(
            val eventId: Long,
            val eventTitle: String,
            var eventBegin: Long,
            var eventEnd: Long,
            val eventAllDay: Boolean,
            val eventColor: Int,
        ) {
            init {
                if (eventAllDay) { // android stores all day events in UTC time instead of local... sigh
                    eventBegin = utcToLocal(eventBegin)
                    eventEnd = utcToLocal(eventEnd)
                }
            }

            private fun utcToLocal(utc: Long): Long {
                val t = Time()
                t.timezone = Time.TIMEZONE_UTC
                t.set(utc)
                t.timezone = Time.getCurrentTimezone()
                return t.normalize(true)
            }
        }

        private val entries = mutableListOf<CalendarListEntry>()

        private companion object {
            val HOUR_IN_MS = 60L * 60 * 1000
            val DAY_IN_MS = 24L * HOUR_IN_MS
        }

        override fun onCreate() {
            updateCalendarInfo()
        }

        override fun onDataSetChanged() {
            updateCalendarInfo()
        }

        override fun onDestroy() {
            entries.clear()
        }

        private fun updateCalendarInfo() {
            Log.v(tag, "Updating calendar info")
            Clock365Widget.updatePending = false
            if (context.checkPermission(Manifest.permission.READ_CALENDAR, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val uri = Uri.withAppendedPath(
                        CalendarContract.Instances.CONTENT_URI,
                        String.format(Locale.ENGLISH, "%d/%d", System.currentTimeMillis(), System.currentTimeMillis() + 14 * DAY_IN_MS)
                    )
                    val cursor = context.contentResolver.query(
                        uri,
                        arrayOf(
                            CalendarContract.Instances.EVENT_ID,
                            CalendarContract.Events.TITLE,
                            CalendarContract.Instances.BEGIN,
                            CalendarContract.Instances.END,
                            CalendarContract.Events.ALL_DAY,
                            CalendarContract.Events.CALENDAR_COLOR
                        ),
                        null, null,
                        CalendarContract.Instances.BEGIN + " ASC"
                    )
                    entries.clear()
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            entries.add(
                                CalendarListEntry(
                                    cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)),
                                    cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)),
                                    cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)),
                                    cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)),
                                    cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)) != 0,
                                    cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_COLOR))
                                )
                            )
                        }
                        cursor.close()
                    }
                    Log.v(tag, "Calendar has ${entries.size} upcoming events")
                } catch (t: Throwable) {
                    Log.v(tag, "Error updating calendar")
                    entries.clear()
                }
            } else {
                Log.v(tag, "Not allowed to read calendar")
                entries.clear()
            }
            scheduleRefresh()
        }

        private fun scheduleRefresh() {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val refreshIntent = Intent(context, Clock365Widget::class.java).apply {
                action = Clock365Widget.ACTION_REFRESH
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            var refreshAt = System.currentTimeMillis() + HOUR_IN_MS
            for (e in entries) {
                if (e.eventBegin >= System.currentTimeMillis() && e.eventBegin <= refreshAt) {
                    refreshAt = e.eventBegin
                }
                if (e.eventEnd >= System.currentTimeMillis() && e.eventEnd <= refreshAt) {
                    refreshAt = e.eventEnd
                }
            }
            am.set(AlarmManager.RTC, refreshAt, pi)
            Log.v(tag, "Auto refresh calendar at " + DateFormat.getTimeFormat(context).format(refreshAt))
        }

        override fun getCount(): Int {
            return if (entries.isEmpty()) 1 else entries.size
        }

        override fun getViewAt(position: Int): RemoteViews {
            if (entries.isEmpty()) {
                val v = RemoteViews(context.packageName, R.layout.calendar_entry)
                v.setTextViewText(R.id.event_title, context.getString(R.string.no_events))
                v.setTextViewText(R.id.event_date, context.getString(R.string.tap_calendar))
                v.setInt(R.id.color_bar, "setBackgroundColor", 0x00000000)
                return v
            }
            val data = entries[position]
            val v = RemoteViews(context.packageName, R.layout.calendar_entry)
            v.setTextViewText(R.id.event_title, data.eventTitle)
            val formattedDate: String = if (data.eventAllDay) {
                if (data.eventEnd - data.eventBegin > DAY_IN_MS) {
                    DateUtils.formatDateRange(context, data.eventBegin, data.eventEnd, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
                } else {
                    DateUtils.formatDateTime(context, data.eventBegin, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL)
                }
            } else {
                if (DateUtils.isToday(data.eventBegin) && DateUtils.isToday(data.eventEnd)) {
                    DateUtils.formatDateRange(context, data.eventBegin, data.eventEnd, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_NO_NOON or DateUtils.FORMAT_NO_MIDNIGHT)
                } else {
                    DateUtils.formatDateRange(context, data.eventBegin, data.eventEnd, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_NO_NOON or DateUtils.FORMAT_NO_MIDNIGHT)
                }
            }
            v.setTextViewText(R.id.event_date, formattedDate)
            v.setInt(R.id.color_bar, "setBackgroundColor", data.eventColor)
            val openEvent = Intent().apply {
                setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, data.eventId))
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                )
            }
            v.setOnClickFillInIntent(R.id.calendar_entry, openEvent)
            return v
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long {
            return if (entries.isEmpty()) -1L else entries[position].eventId
        }

        override fun hasStableIds(): Boolean = true
    }
}
