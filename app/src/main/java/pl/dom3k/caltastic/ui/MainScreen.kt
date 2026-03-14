package pl.dom3k.caltastic.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    val today = LocalDate.now()
    val days = remember {
        (-30..90).map { today.plusDays(it.toLong()) }
    }
    
    var selectedDate by remember { mutableStateOf(today) }
    val dailyTasksListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showCalendarSettings by remember { mutableStateOf(false) }
    
    // Flag to ignore scroll sync events during programmatic scrolling
    var isProgrammaticScroll by remember { mutableStateOf(false) }
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
        if (!initialScrollDone && days.isNotEmpty()) {
            delay(300) // Ensure layout is settled
            val targetIndex = findIndexByDate(today, days, events)
            if (targetIndex != -1) {
                isProgrammaticScroll = true
                dailyTasksListState.scrollToItem(targetIndex)
                delay(100)
                isProgrammaticScroll = false
            }
            if (events.isNotEmpty()) {
                initialScrollDone = true
            }
        }
    }

    val onDateSelected: (LocalDate) -> Unit = { date ->
        // Always trigger scroll even if selectedDate is the same, to allow re-centering/snapping
        selectedDate = date
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            isProgrammaticScroll = true
            val targetIndex = findIndexByDate(date, days, events)
            if (targetIndex != -1) {
                // Use a slightly faster animation or just scrollToItem if it feels laggy
                dailyTasksListState.animateScrollToItem(targetIndex)
            }
            // Wait for scroll to fully settle before re-enabling sync from list to ticker
            delay(600) 
            isProgrammaticScroll = false
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
                }
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
                onDateSelected = onDateSelected
            )

            DailyTasks(
                allDays = days,
                groupedEvents = events,
                onVisibleDayChanged = { date ->
                    if (!isProgrammaticScroll && selectedDate != date) {
                        selectedDate = date
                    }
                },
                listState = dailyTasksListState,
                isProgrammaticScroll = isProgrammaticScroll,
                modifier = Modifier.weight(1f)
            )
        }

        if (showCalendarSettings) {
            CalendarSettingsDialog(
                calendars = calendars,
                selectedIds = selectedCalendarIds,
                onToggle = { viewModel.toggleCalendar(it) },
                onDismiss = { showCalendarSettings = false }
            )
        }
    }
}

@Composable
fun CalendarSettingsDialog(
    calendars: List<pl.dom3k.caltastic.data.CalendarInfo>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz kalendarze") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                calendars.forEach { calendar ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(calendar.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedIds.contains(calendar.id),
                            onCheckedChange = { onToggle(calendar.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(calendar.color), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(calendar.name, modifier = Modifier.weight(1f))
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
