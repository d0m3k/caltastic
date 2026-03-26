package pl.dom3k.caltastic.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import pl.dom3k.caltastic.parser.DraftEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarInfo(
    val id: Long,
    val name: String,
    val color: Int,
    val isVisible: Boolean
)

class CalendarRepository(private val context: Context) {

    fun getCalendars(): List<CalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )

        val calendars = mutableListOf<CalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val colorIndex = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
            val visibleIndex = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)

            while (cursor.moveToNext()) {
                calendars.add(
                    CalendarInfo(
                        id = cursor.getLong(idIndex),
                        name = cursor.getString(nameIndex) ?: "Unknown",
                        color = cursor.getInt(colorIndex),
                        isVisible = cursor.getInt(visibleIndex) == 1
                    )
                )
            }
        }
        return calendars
    }

    private fun getDefaultCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"
        
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val primaryIndex = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                
                var firstId: Long? = null
                do {
                    val id = cursor.getLong(idIndex)
                    if (firstId == null) firstId = id
                    
                    if (primaryIndex >= 0 && cursor.getInt(primaryIndex) == 1) {
                        return id
                    }
                } while (cursor.moveToNext())
                
                return firstId
            }
        }
        return null
    }

    fun getEvents(startDate: LocalDate, endDate: LocalDate, calendarIds: Set<Long>? = null): Map<LocalDate, List<DraftEvent>> {
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_ID
        )

        val eventsMap = mutableMapOf<LocalDate, MutableList<DraftEvent>>()
        
        val selection = calendarIds?.let { ids ->
            if (ids.isEmpty()) "0" else "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",")})"
        }

        context.contentResolver.query(
            builder.build(),
            projection,
            selection,
            null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            val instanceIdIndex = cursor.getColumnIndex(CalendarContract.Instances._ID)
            val eventIdIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndex(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val colorIndex = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)
            val descIndex = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val locIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val calNameIndex = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val instanceId = cursor.getLong(instanceIdIndex)
                val eventId = cursor.getLong(eventIdIndex)
                val title = cursor.getString(titleIndex) ?: "Szkic wydarzenia"
                val beginMillis = cursor.getLong(beginIndex)
                val endMillis = cursor.getLong(endIndex)
                val isAllDay = cursor.getInt(allDayIndex) == 1
                val color = cursor.getInt(colorIndex)
                val description = cursor.getString(descIndex)
                val location = cursor.getString(locIndex)
                val calendarName = cursor.getString(calNameIndex)
                
                val startZone = if (isAllDay) ZoneId.of("UTC") else ZoneId.systemDefault()
                val startZonedDateTime = Instant.ofEpochMilli(beginMillis).atZone(startZone)
                
                val endMillisAdjusted = if (endMillis > beginMillis) endMillis - 1 else endMillis
                val endZonedDateTime = Instant.ofEpochMilli(endMillisAdjusted).atZone(startZone)

                val eventStartAsDate = startZonedDateTime.toLocalDate()
                val eventEndAsDate = endZonedDateTime.toLocalDate()
                
                val totalDays = java.time.temporal.ChronoUnit.DAYS.between(eventStartAsDate, eventEndAsDate).toInt() + 1
                
                for (dayOffset in 0 until totalDays) {
                    val currentDate = eventStartAsDate.plusDays(dayOffset.toLong())
                    val multiDayPosition = if (totalDays > 1) (dayOffset + 1) to totalDays else null
                    
                    val isFirstDay = dayOffset == 0
                    val isLastDay = dayOffset == totalDays - 1
                    
                    // Standardize middle days into all-day events so they natively group at the top.
                    val eventIsAllDayForThisDay = isAllDay || (!isFirstDay && !isLastDay)
                    
                    val computedStartTime = when {
                        eventIsAllDayForThisDay -> null // Handled in AllDay row
                        isFirstDay -> startZonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
                        else -> java.time.LocalTime.MIN // Spills from yesterday, starts fully at midnight
                    }
                    
                    val computedEndTime = when {
                        eventIsAllDayForThisDay -> null
                        isLastDay -> Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                        else -> java.time.LocalTime.MAX // Extends till midnight
                    }

                    val isSpanningStart = !eventIsAllDayForThisDay && isFirstDay && totalDays > 1
                    val isSpanningEnd = !eventIsAllDayForThisDay && isLastDay && totalDays > 1
                    
                    val event = DraftEvent(
                        id = eventId,
                        instanceId = instanceId,
                        title = title,
                        date = currentDate,
                        startTime = computedStartTime,
                        endTime = computedEndTime,
                        isAllDay = eventIsAllDayForThisDay,
                        color = color,
                        calendarName = calendarName,
                        location = location,
                        description = description,
                        originalText = title,
                        multiDayPosition = multiDayPosition,
                        isSpanningStart = isSpanningStart,
                        isSpanningEnd = isSpanningEnd
                    )

                    eventsMap.getOrPut(currentDate) { mutableListOf() }.add(event)
                }
            }
        }

        return eventsMap
    }

    fun addEvent(draftEvent: DraftEvent, calendarId: Long? = null): Boolean {
        val targetCalendarId = calendarId ?: getDefaultCalendarId() ?: return false
        
        val date = draftEvent.date ?: LocalDate.now()
        val startMillis: Long
        val endMillis: Long

        if (draftEvent.isAllDay) {
            startMillis = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
            endMillis = date.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        } else {
            val startDateTime = if (draftEvent.startTime != null) {
                date.atTime(draftEvent.startTime)
            } else {
                date.atStartOfDay()
            }
            startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            endMillis = if (draftEvent.endTime != null) {
                date.atTime(draftEvent.endTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                startMillis + (60 * 60 * 1000) // Default 1 hour
            }
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, draftEvent.title)
            put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (draftEvent.isAllDay) "UTC" else ZoneId.systemDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (draftEvent.isAllDay) 1 else 0)
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri != null
    }
}
