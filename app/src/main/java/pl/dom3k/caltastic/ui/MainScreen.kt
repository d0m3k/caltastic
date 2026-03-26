package pl.dom3k.caltastic.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.dom3k.caltastic.R
import pl.dom3k.caltastic.parser.DraftEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val events by viewModel.events.collectAsState()
    val calendars by viewModel.calendars.collectAsState()
    val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsState()
    val defaultCalendarId by viewModel.defaultCalendarId.collectAsState()

    val today = LocalDate.now()
    val days = remember {
        // Extended range: 1 year back, 2 years forward
        (-365..730).map { today.plusDays(it.toLong()) }
    }
    val immutableDays = remember(days) { ImmutableDays(days) }
    val immutableEvents = remember(events) { ImmutableEvents(events) }
    
    val isLoadingEvents by viewModel.isLoadingEvents.collectAsState()

    var selectedDate by remember { mutableStateOf(today) }
    val dailyTasksListState = remember(isLoadingEvents) {
        androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = if (isLoadingEvents) 1095 else findIndexByDate(today, days, events, smartToday = true))
    }
    val dayTickerListState = remember(isLoadingEvents) {
        androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = maxOf(0, days.indexOf(today) - 2))
    }
    val coroutineScope = rememberCoroutineScope()
    var showCalendarSettings by remember { mutableStateOf(false) }
    var showSmartAdd by remember { mutableStateOf(false) }

    // Listen to lifecycle changes to refresh data when app is reopened
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        if (readGranted) {
            viewModel.loadEvents(today.minusYears(1), today.plusYears(2))
        } else {
            viewModel.setEventsLoading(false)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadEvents(today.minusYears(1), today.plusYears(2))
        } else {
            viewModel.setEventsLoading(false)
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
    }

    val onDateSelected: (LocalDate) -> Unit = { date ->
        selectedDate = date
        coroutineScope.launch {
            val targetIndex = findIndexByDate(date, days, events, smartToday = date == today)
            if (targetIndex != -1) {
                dailyTasksListState.animateScrollToItem(targetIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val month = selectedDate.format(DateTimeFormatter.ofPattern("LLLL", Locale.getDefault()))
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    val year = selectedDate.year.toString()
                    
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                                append(month)
                            }
                            append(" ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.primary)) {
                                append(year)
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showCalendarSettings = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.calendars_content_description))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onDateSelected(today)
                    }) {
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("d.MM")),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showSmartAdd) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                            context.startActivity(intent)
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.open_in_calendar), modifier = Modifier.size(18.dp))
                    }
                    FloatingActionButton(
                        onClick = { showSmartAdd = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.smart_add_title))
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                if (isLoadingEvents) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    DayTicker(
                        days = days,
                        events = events,
                        selectedDate = selectedDate,
                        onDateSelected = onDateSelected,
                    onDateFocused = { date ->
                        // ticker is being scrolled by user
                        if (selectedDate != date) {
                            selectedDate = date
                            coroutineScope.launch {
                                val targetIndex = findIndexByDate(date, days, events)
                                if (targetIndex != -1) {
                                    dailyTasksListState.scrollToItem(targetIndex)
                                }
                            }
                        }
                    },
                    listState = dayTickerListState
                )

                    DailyTasks(
                        allDays = immutableDays,
                        groupedEvents = immutableEvents,
                        onVisibleDayChanged = remember {
                            { date ->
                                if (selectedDate != date) {
                                    selectedDate = date
                                }
                            }
                        },
                        listState = dailyTasksListState,
                        modifier = Modifier.weight(1f),
                        onScrollProgress = remember {
                            val jobHolder = object { var job: Job? = null }
                            { fractionalDay: Float ->
                                if (!dayTickerListState.isScrollInProgress) {
                                    val dayIndex = fractionalDay.toInt()
                                    val fraction = fractionalDay - dayIndex
                                    val tickerItemWidthPx = with(density) { 56.dp.toPx() }
                                    val extraOffset = (fraction * tickerItemWidthPx).toInt()
                                    // Cancel any previous racing scroll updates
                                    jobHolder.job?.cancel()
                                    jobHolder.job = coroutineScope.launch {
                                        dayTickerListState.scrollToItem(dayIndex, -150 + extraOffset)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (showSmartAdd) {
                // Dimmed background to allow dismissal by clicking outside
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { showSmartAdd = false }
                )

                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SmartAddInput(
                        onAddEvent = { draftEvent ->
                            viewModel.addEvent(draftEvent)
                            showSmartAdd = false
                        },
                        defaultCalendarName = calendars.find { it.id == defaultCalendarId }?.name ?: stringResource(R.string.default_calendar_name)
                    )
                }
            }
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
        title = { Text(stringResource(R.string.calendar_settings_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.calendar_settings_description),
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done_button)) }
        }
    )
}

fun findIndexByDate(
    targetDate: LocalDate,
    allDays: List<LocalDate>,
    events: Map<LocalDate, List<DraftEvent>>,
    smartToday: Boolean = false
): Int {
    var currentIndex = 0
    val today = LocalDate.now()
    val currentTime = LocalTime.now()

    for (date in allDays) {
        if (date == targetDate) {
            if (smartToday && date == today) {
                // Smart scroll for Today
                val dayEvents = events[date] ?: emptyList()
                val (allDay, timed) = dayEvents.partition { it.isAllDay }
                val sortedTimed = timed.sortedBy { it.startTime }

                // Find the first event that is NOT past (current or future)
                var firstNonPastIndex = -1
                for (i in sortedTimed.indices) {
                    val event = sortedTimed[i]
                    val isPast = event.endTime?.let { it < currentTime } ?: (event.startTime?.let { it < currentTime } ?: false)
                    if (!isPast) {
                        firstNonPastIndex = i
                        break
                    }
                }

                return if (firstNonPastIndex != -1) {
                    // Start with Header
                    var offset = 1 
                    if (allDay.isNotEmpty()) offset++ // AllDayRow
                    
                    // We want to scroll to "current/indicator minus one event"
                    // If firstNonPastIndex is 0, we just scroll to header (offset 0 relative to day start)
                    // If it's > 0, we scroll to the event before it.
                    
                    if (firstNonPastIndex > 0) {
                        currentIndex + offset + (firstNonPastIndex - 1)
                    } else {
                        currentIndex // Just top of Today
                    }
                } else {
                    currentIndex // Just top of Today
                }
            }
            return currentIndex
        }
        currentIndex += calculateItemsForDay(date, events)
    }
    return -1
}

fun calculateItemsForDay(date: LocalDate, events: Map<LocalDate, List<DraftEvent>>): Int {
    var count = 1 // Header
    val dayEvents = events[date] ?: emptyList()
    val (allDay, timed) = dayEvents.partition { it.isAllDay }
    val isToday = date == LocalDate.now()
    
    if (allDay.isNotEmpty()) count++ // AllDayRow
    count += timed.size // Timed items
    if (dayEvents.isEmpty()) count++ // Empty placeholder
    
    // Account for TimeIndicatorRow on today
    if (isToday) count++ 

    count++ // Divider
    return count
}
