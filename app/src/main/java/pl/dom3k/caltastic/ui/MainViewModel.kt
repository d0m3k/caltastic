package pl.dom3k.caltastic.ui

import android.app.Application
import android.content.Context
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val calendarRepository = CalendarRepository(application)
    private val prefs = application.getSharedPreferences("caltastic_prefs", Context.MODE_PRIVATE)
    
    private val _events = MutableStateFlow<Map<LocalDate, List<DraftEvent>>>(emptyMap())
    val events: StateFlow<Map<LocalDate, List<DraftEvent>>> = _events.asStateFlow()

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _selectedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCalendarIds: StateFlow<Set<Long>> = _selectedCalendarIds.asStateFlow()

    private val _defaultCalendarId = MutableStateFlow<Long?>(null)
    val defaultCalendarId: StateFlow<Long?> = _defaultCalendarId.asStateFlow()
    
    init {
        loadCalendars()
    }

    private fun loadCalendars() {
        viewModelScope.launch {
            val fetchedCalendars = calendarRepository.getCalendars()
            _calendars.value = fetchedCalendars
            _selectedCalendarIds.value = fetchedCalendars.filter { it.isVisible }.map { it.id }.toSet()
            
            // Try to load saved default calendar, otherwise find a sensible default
            val savedId = prefs.getLong("default_calendar_id", -1L)
            if (savedId != -1L && fetchedCalendars.any { it.id == savedId }) {
                _defaultCalendarId.value = savedId
            } else {
                _defaultCalendarId.value = fetchedCalendars.firstOrNull { it.isVisible }?.id
            }
        }
    }

    fun toggleCalendar(id: Long) {
        _selectedCalendarIds.update { current ->
            if (current.contains(id)) current - id else current + id
        }
    }

    fun setDefaultCalendar(id: Long) {
        _defaultCalendarId.value = id
        prefs.edit().putLong("default_calendar_id", id).apply()
    }
    
    fun loadEvents(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            try {
                val fetchedEvents = calendarRepository.getEvents(startDate, endDate)
                _events.value = fetchedEvents
            } catch (e: Exception) {
                // Handle missing permissions or errors
            }
        }
    }
    
    fun addEvent(draftEvent: DraftEvent) {
        viewModelScope.launch {
            try {
                val success = calendarRepository.addEvent(draftEvent, _defaultCalendarId.value)
                if (success) {
                    val eventDate = draftEvent.date ?: LocalDate.now()
                    loadEvents(eventDate.minusMonths(1), eventDate.plusMonths(1))
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
