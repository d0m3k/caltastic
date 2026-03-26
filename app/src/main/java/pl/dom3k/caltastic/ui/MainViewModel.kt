package pl.dom3k.caltastic.ui

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dom3k.caltastic.data.CalendarInfo
import pl.dom3k.caltastic.data.CalendarRepository
import pl.dom3k.caltastic.parser.DraftEvent
import java.time.LocalDate
import androidx.core.content.edit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val calendarRepository = CalendarRepository(application)
    private val prefs = application.getSharedPreferences("caltastic_prefs", Context.MODE_PRIVATE)
    
    private val _events = MutableStateFlow<Map<LocalDate, List<DraftEvent>>>(emptyMap())
    val events: StateFlow<Map<LocalDate, List<DraftEvent>>> = _events.asStateFlow()

    private val _isLoadingEvents = MutableStateFlow(true)
    val isLoadingEvents: StateFlow<Boolean> = _isLoadingEvents.asStateFlow()

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _selectedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCalendarIds: StateFlow<Set<Long>> = _selectedCalendarIds.asStateFlow()

    private val _defaultCalendarId = MutableStateFlow<Long?>(null)
    val defaultCalendarId: StateFlow<Long?> = _defaultCalendarId.asStateFlow()

    private var currentRange: Pair<LocalDate, LocalDate>? = null

    fun setEventsLoading(loading: Boolean) {
        _isLoadingEvents.value = loading
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            refreshEvents()
        }
    }
    
    init {
        refresh()
        application.contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,
            observer
        )
        // Also observe Instances, as changes to recurrence or colors might show up there first
        application.contentResolver.registerContentObserver(
            CalendarContract.Instances.CONTENT_URI,
            true,
            observer
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    fun refresh() {
        viewModelScope.launch {
            val fetchedCalendars = calendarRepository.getCalendars()
            _calendars.value = fetchedCalendars
            
            val savedIds = prefs.getStringSet("selected_calendar_ids", null)
            if (savedIds != null) {
                _selectedCalendarIds.value = savedIds.map { it.toLong() }.toSet()
            } else {
                // If it's first load and no prefs, we trust the "visible" flag from system
                _selectedCalendarIds.value = fetchedCalendars.filter { it.isVisible }.map { it.id }.toSet()
            }
            
            val savedId = prefs.getLong("default_calendar_id", -1L)
            if (savedId != -1L && fetchedCalendars.any { it.id == savedId }) {
                _defaultCalendarId.value = savedId
            } else {
                _defaultCalendarId.value = fetchedCalendars.firstOrNull { it.isVisible }?.id
            }
            
            refreshEvents()
        }
    }

    fun toggleCalendar(id: Long) {
        _selectedCalendarIds.update { current ->
            val newSet = if (current.contains(id)) current - id else current + id
            prefs.edit {
                putStringSet(
                    "selected_calendar_ids",
                    newSet.map { it.toString() }.toSet()
                )
            }
            newSet
        }
        refreshEvents()
    }

    fun setDefaultCalendar(id: Long) {
        _defaultCalendarId.value = id
        prefs.edit { putLong("default_calendar_id", id) }
    }
    
    fun loadEvents(startDate: LocalDate, endDate: LocalDate) {
        currentRange = startDate to endDate
        _isLoadingEvents.value = true
        refreshEvents()
    }

    private fun refreshEvents() {
        val range = currentRange ?: return
        viewModelScope.launch {
            try {
                val fetchedEvents = calendarRepository.getEvents(range.first, range.second, _selectedCalendarIds.value)
                _events.value = fetchedEvents
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoadingEvents.value = false
            }
        }
    }
    
    fun addEvent(draftEvent: DraftEvent) {
        viewModelScope.launch {
            try {
                calendarRepository.addEvent(draftEvent, _defaultCalendarId.value)
                // ContentObserver will trigger refreshEvents() automatically
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
