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
import androidx.compose.material.icons.filled.Settings
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
import kotlinx.coroutines.launch
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Caltastic", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { showCalendarSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Kalendarze")
                    }
                },
                navigationIcon = {
                    TextButton(onClick = {
                        selectedDate = today
                        coroutineScope.launch {
                            val targetKey = "header_$today"
                            val targetIndex = findIndexByDateKey(dailyTasksListState, targetKey, days, events)
                            if (targetIndex != -1) {
                                dailyTasksListState.animateScrollToItem(targetIndex)
                            }
                        }
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
                onDateSelected = { date ->
                    selectedDate = date
                    coroutineScope.launch {
                        val targetKey = "header_$date"
                        val targetIndex = findIndexByDateKey(dailyTasksListState, targetKey, days, events)
                        if (targetIndex != -1) {
                            dailyTasksListState.animateScrollToItem(targetIndex)
                        }
                    }
                }
            )

            DailyTasks(
                allDays = days,
                groupedEvents = events,
                onVisibleDayChanged = { date ->
                    if (selectedDate != date) {
                        selectedDate = date
                    }
                },
                listState = dailyTasksListState,
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

private fun findIndexByDateKey(
    state: androidx.compose.foundation.lazy.LazyListState,
    key: String,
    allDays: List<LocalDate>,
    events: Map<LocalDate, List<pl.dom3k.caltastic.parser.DraftEvent>>
): Int {
    var currentIndex = 0
    for (date in allDays) {
        val headerKey = "header_$date"
        if (headerKey == key) return currentIndex
        currentIndex++ // Header
        
        val dayEvents = events[date] ?: emptyList()
        val (allDay, timed) = dayEvents.partition { it.isAllDay }
        
        if (allDay.isNotEmpty()) currentIndex++ // AllDayRow
        currentIndex += timed.size // Timed items
        if (dayEvents.isEmpty() && allDay.isEmpty()) currentIndex++ // Empty placeholder
    }
    return -1
}
