package pl.dom3k.caltastic.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.dom3k.caltastic.parser.DraftEvent
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val calendars by viewModel.calendars.collectAsState()
    val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsState()
    val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()

    val today = LocalDate.now()
    val days = remember {
        (-30..90).map { today.plusDays(it.toLong()) }
    }
    
    var selectedDate by remember { mutableStateOf(today) }
    val dailyTasksListState = rememberLazyListState()
    val dayTickerListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showCalendarSettings by remember { mutableStateOf(false) }
    
    // Separate flags for each component to avoid deadlocks and unresponsiveness
    var isDailyTasksProgrammaticScroll by remember { mutableStateOf(false) }
    var isDayTickerProgrammaticScroll by remember { mutableStateOf(false) }
    
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        if (readGranted) {
            viewModel.loadEvents(today.minusMonths(1), today.plusMonths(3))
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadEvents(today.minusMonths(1), today.plusMonths(3))
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }

    // Scroll to today on first load
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(events) {
        if (!initialScrollDone && days.isNotEmpty() && events.isNotEmpty()) {
            delay(300) // Ensure layout is settled
            val targetIndex = findIndexByDate(today, days, events)
            if (targetIndex != -1) {
                isDailyTasksProgrammaticScroll = true
                dailyTasksListState.scrollToItem(targetIndex)
                delay(100)
                isDailyTasksProgrammaticScroll = false
                initialScrollDone = true
            }
        }
    }

    val onDateSelected: (LocalDate) -> Unit = { date ->
        selectedDate = date
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            try {
                isDailyTasksProgrammaticScroll = true
                isDayTickerProgrammaticScroll = true
                
                val targetIndex = findIndexByDate(date, days, events)
                val tickerIndex = days.indexOf(date)
                
                if (targetIndex != -1) {
                    dailyTasksListState.animateScrollToItem(targetIndex)
                }
                if (tickerIndex != -1) {
                    dayTickerListState.animateScrollToItem(tickerIndex, scrollOffset = -150)
                }
                delay(600)
            } finally {
                isDailyTasksProgrammaticScroll = false
                isDayTickerProgrammaticScroll = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Caltastic", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { showCalendarSettings = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Kalendarze")
                    }
                },
                navigationIcon = {
                    TextButton(onClick = {
                        onDateSelected(today)
                    }) {
                        Text("Dzisiaj", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        bottomBar = {
            SmartAddInput(
                onAddEvent = { draftEvent ->
                    viewModel.addEvent(draftEvent)
                },
                defaultCalendarName = calendars.find { it.id == defaultCalendarId }?.name ?: "Podstawowy"
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            DayTicker(
                days = days,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
                onDateFocused = { date ->
                    // ticker is being scrolled by user
                    if (!isDayTickerProgrammaticScroll && selectedDate != date) {
                        selectedDate = date
                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch {
                            try {
                                isDailyTasksProgrammaticScroll = true
                                val targetIndex = findIndexByDate(date, days, events)
                                if (targetIndex != -1) {
                                    dailyTasksListState.scrollToItem(targetIndex)
                                }
                                delay(50)
                            } finally {
                                isDailyTasksProgrammaticScroll = false
                            }
                        }
                    }
                },
                isProgrammaticScroll = isDayTickerProgrammaticScroll,
                listState = dayTickerListState
            )

            DailyTasks(
                allDays = days,
                groupedEvents = events,
                onVisibleDayChanged = { date ->
                    // list is being scrolled by user
                    if (!isDailyTasksProgrammaticScroll && selectedDate != date) {
                        selectedDate = date
                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch {
                            try {
                                isDayTickerProgrammaticScroll = true
                                // DayTicker handles its own scroll to selectedDate in its LaunchedEffect(selectedDate)
                                delay(600) 
                            } finally {
                                isDayTickerProgrammaticScroll = false
                            }
                        }
                    }
                },
                listState = dailyTasksListState,
                isProgrammaticScroll = isDailyTasksProgrammaticScroll,
                modifier = Modifier.weight(1f)
            )
        }

        if (showCalendarSettings) {
            CalendarSettingsDialog(
                calendars = calendars,
                selectedIds = selectedCalendarIds,
                defaultId = defaultCalendarId,
                onToggle = { viewModel.toggleCalendar(it) },
                onSetDefault = { viewModel.setDefaultCalendar(it) },
                onDismiss = { showCalendarSettings = false }
            )
        }
    }
}

@Composable
fun CalendarSettingsDialog(
    calendars: List<pl.dom3k.caltastic.data.CalendarInfo>,
    selectedIds: Set<Long>,
    defaultId: Long?,
    onToggle: (Long) -> Unit,
    onSetDefault: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia kalendarzy") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Widoczne kalendarze i domyślny dla szybkich wpisów (gwiazdka)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                calendars.forEach { calendar ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(calendar.id),
                            onCheckedChange = { onToggle(calendar.id) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(calendar.color), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            calendar.name, 
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        RadioButton(
                            selected = defaultId == calendar.id,
                            onClick = { onSetDefault(calendar.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Gotowe") }
        }
    )
}

fun findIndexByDate(
    targetDate: LocalDate,
    allDays: List<LocalDate>,
    events: Map<LocalDate, List<DraftEvent>>
): Int {
    var currentIndex = 0
    for (date in allDays) {
        if (date == targetDate) return currentIndex
        currentIndex += calculateItemsForDay(date, events)
    }
    return -1
}

fun calculateItemsForDay(date: LocalDate, events: Map<LocalDate, List<DraftEvent>>): Int {
    var count = 1 // Header
    val dayEvents = events[date] ?: emptyList()
    val (allDay, timed) = dayEvents.partition { it.isAllDay }
    
    if (allDay.isNotEmpty()) count++ // AllDayRow
    count += timed.size // Timed items
    if (dayEvents.isEmpty()) count++ // Empty placeholder
    
    count++ // Divider
    return count
}
