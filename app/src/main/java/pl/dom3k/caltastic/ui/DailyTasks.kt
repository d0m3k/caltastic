package pl.dom3k.caltastic.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.dom3k.caltastic.R
import pl.dom3k.caltastic.parser.DraftEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.runtime.Immutable

@Immutable
data class ImmutableDays(val items: List<LocalDate>)

@Immutable
data class ImmutableEvents(val items: Map<LocalDate, List<DraftEvent>>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyTasks(
    allDays: ImmutableDays,
    groupedEvents: ImmutableEvents,
    onVisibleDayChanged: (LocalDate) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onScrollProgress: ((Float) -> Unit)? = null
) {
    val daysList = allDays.items
    val eventsMap = groupedEvents.items
    var expandedEventId by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val today = LocalDate.now()

    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            currentTime = now
            val millisUntilNextMinute = (60 - now.second) * 1000L - (now.nano / 1_000_000L)
            delay(millisUntilNextMinute.coerceAtLeast(1))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
        )
    ) {
        daysList.forEach { date ->
            val events = eventsMap[date] ?: emptyList()
            val (allDayEvents, timedEvents) = events.partition { it.isAllDay }
            val isDayPast = date < today

            stickyHeader(key = "header_$date") {
                DayHeader(date = date, isPast = isDayPast)
            }

            if (allDayEvents.isNotEmpty()) {
                item(key = "allday_$date") {
                    AllDayEventsRow(events = allDayEvents, isPast = isDayPast)
                }
            }

            if (timedEvents.isNotEmpty()) {
                val sortedEvents = timedEvents.sortedBy { it.startTime }
                
                sortedEvents.forEachIndexed { index, event ->
                    val isPast = date < today || (date == today && event.endTime?.let { it < currentTime } ?: (event.startTime?.let { it < currentTime } ?: false))
                    val isCurrent = date == today && event.startTime?.let { it <= currentTime } == true && (event.endTime?.let { it >= currentTime } ?: true)

                    if (date == today) {
                        val prevEvent = if (index > 0) sortedEvents[index - 1] else null
                        val shouldShowIndicatorBefore = when {
                            index == 0 && event.startTime?.let { it > currentTime } == true -> true
                            prevEvent != null && prevEvent.endTime?.let { it < currentTime } == true && event.startTime?.let { it > currentTime } == true -> true
                            else -> false
                        }
                        
                        if (shouldShowIndicatorBefore) {
                            item(key = "time_indicator_$date") {
                                TimeIndicatorRow()
                            }
                        }
                    }

                    item(key = "event_${event.date}_${event.instanceId ?: "${event.id}_${event.startTime}_${event.title}"}") {
                        val eventKey = "event_${event.date}_${event.instanceId ?: "${event.id}_${event.startTime}_${event.title}"}"
                        EventItem(
                            event = event,
                            isExpanded = expandedEventId == eventKey,
                            isPast = isPast,
                            isCurrent = isCurrent,
                            onExpandToggled = {
                                expandedEventId = if (expandedEventId == eventKey) null else eventKey
                            }
                        )
                    }
                    
                    if (date == today && index == sortedEvents.size - 1 && event.endTime?.let { it < currentTime } == true) {
                        item(key = "time_indicator_after_$date") {
                            TimeIndicatorRow()
                        }
                    }
                }
            } else {
                if (allDayEvents.isEmpty()) {
                    item(key = "empty_$date") {
                        EmptyDayPlaceholder(isPast = isDayPast)
                    }
                }
                if (date == today) {
                    item(key = "time_indicator_empty_$date") {
                        TimeIndicatorRow()
                    }
                }
            }
            
            item(key = "divider_$date") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
    
    val itemHeights = androidx.compose.runtime.remember(daysList, eventsMap) {
        val heights = mutableListOf<Float>()
        val today = LocalDate.now()
        for (day in daysList) {
            heights.add(40f) // Header
            val dayEvents = eventsMap[day] ?: emptyList()
            val (allDay, timed) = dayEvents.partition { it.isAllDay }
            if (allDay.isNotEmpty()) heights.add(40f)
            timed.forEach { _ -> heights.add(60f) }
            if (dayEvents.isEmpty()) heights.add(40f)
            if (day == today) heights.add(2f)
            heights.add(1f) // Divider
        }
        heights.toFloatArray()
    }

    val dayStartIndices = androidx.compose.runtime.remember(daysList, eventsMap) {
        val indices = IntArray(daysList.size + 1)
        var current = 0
        for (i in daysList.indices) {
            indices[i] = current
            current += calculateItemsForDay(daysList[i], eventsMap)
        }
        indices[daysList.size] = current
        indices
    }

    LaunchedEffect(listState, daysList) {
        snapshotFlow { 
            val idx = listState.firstVisibleItemIndex
            val off = listState.firstVisibleItemScrollOffset
            val h = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            Triple(idx, off, h)
        }.collect { (firstVisibleItem, offset, itemHeight) ->
            if (daysList.isNotEmpty()) {
                var dayIndex = dayStartIndices.binarySearch(firstVisibleItem)
                if (dayIndex < 0) dayIndex = -dayIndex - 2
                
                if (dayIndex >= 0 && dayIndex < daysList.size) {
                    val date = daysList[dayIndex]
                    onVisibleDayChanged(date)
                    
                    var totalHeight = 0f
                    var accumulated = 0f
                    val startIdx = dayStartIndices[dayIndex]
                    val endIdx = dayStartIndices[dayIndex + 1]
                    for (i in startIdx until endIdx) {
                        val h = itemHeights.getOrElse(i) { 50f }
                        totalHeight += h
                        if (i < firstVisibleItem) accumulated += h
                    }
                    
                    // Normalize the exact current item's pixel offset relatively
                    val pixelOffset = offset.toFloat().coerceAtMost(itemHeights.getOrElse(firstVisibleItem) { itemHeight.toFloat() })
                    val fraction = (accumulated + pixelOffset) / totalHeight.coerceAtLeast(1f)
                    
                    onScrollProgress?.invoke(dayIndex + fraction)
                }
            }
        }
    }
}

@Composable
fun TimeIndicatorRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, end = 16.dp),
            thickness = 1.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun DayHeader(date: LocalDate, isPast: Boolean = false) {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val dateStr = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
    val isToday = date == LocalDate.now()
    val contentAlpha = if (isPast) 0.5f else 1f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .alpha(contentAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AllDayEventsRow(events: List<DraftEvent>, isPast: Boolean = false) {
    val context = LocalContext.current
    val contentAlpha = if (isPast) 0.6f else 1f
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
    ) {
        items(events, key = { it.instanceId ?: "${it.id}_${it.title}" }) { event ->
            val color = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primaryContainer
            Surface(
                color = color.copy(alpha = 0.2f),
                contentColor = color,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                modifier = Modifier
                    .height(32.dp)
                    .clickable {
                        event.id?.let { id ->
                            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                            val intent = Intent(Intent.ACTION_VIEW).setData(uri)
                            context.startActivity(intent)
                        }
                    }
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun EventItem(
    event: DraftEvent,
    isExpanded: Boolean,
    isPast: Boolean = false,
    isCurrent: Boolean = false,
    onExpandToggled: () -> Unit
) {
    val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    
    val surfaceAlpha = if (isPast) 0.5f else 1f
    val containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .clickable { onExpandToggled() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(surfaceAlpha)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(52.dp)) {
                if (event.startTime != null) {
                    Text(
                        text = event.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (event.endTime != null) {
                        Text(
                            text = event.endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) 
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .width(if (isCurrent) 6.dp else 4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(if (isCurrent) 3.dp else 2.dp))
                    .background(eventColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (event.calendarName != null) {
                    Text(
                        text = event.calendarName,
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (isCurrent) MaterialTheme.colorScheme.primary else eventColor).copy(alpha = 0.8f)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 78.dp, top = 8.dp, bottom = 4.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!event.location.isNullOrBlank()) {
                    DetailRow(Icons.Default.LocationOn, event.location)
                }
                if (!event.description.isNullOrBlank()) {
                    DetailRow(Icons.AutoMirrored.Filled.Notes, event.description)
                }
                
                if (event.id != null) {
                    Button(
                        onClick = {
                            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
                            val intent = Intent(Intent.ACTION_VIEW).setData(uri)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(top = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.open_in_calendar), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyDayPlaceholder(isPast: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (isPast) 0.5f else 1f)
    ) {
        Text(
            stringResource(R.string.no_events_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
