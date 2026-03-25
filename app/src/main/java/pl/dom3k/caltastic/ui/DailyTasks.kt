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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.dom3k.caltastic.R
import pl.dom3k.caltastic.parser.DraftEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyTasks(
    allDays: List<LocalDate>,
    groupedEvents: Map<LocalDate, List<DraftEvent>>,
    onVisibleDayChanged: (LocalDate) -> Unit,
    listState: LazyListState,
    isProgrammaticScroll: Boolean,
    modifier: Modifier = Modifier
) {
    var expandedEventId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
        )
    ) {
        allDays.forEach { date ->
            val events = groupedEvents[date] ?: emptyList()
            val (allDayEvents, timedEvents) = events.partition { it.isAllDay }

            stickyHeader(key = "header_$date") {
                DayHeader(date = date)
            }

            if (allDayEvents.isNotEmpty()) {
                item(key = "allday_$date") {
                    AllDayEventsRow(events = allDayEvents)
                }
            }

            if (timedEvents.isNotEmpty()) {
                items(timedEvents, key = { "event_${it.date}_${it.instanceId ?: "${it.id}_${it.startTime}_${it.title}"}" }) { event ->
                    val eventKey = "event_${event.date}_${event.instanceId ?: "${event.id}_${event.startTime}_${event.title}"}"
                    EventItem(
                        event = event,
                        isExpanded = expandedEventId == eventKey,
                        onExpandToggled = {
                            expandedEventId = if (expandedEventId == eventKey) null else eventKey
                        }
                    )
                }
            } else if (allDayEvents.isEmpty()) {
                item(key = "empty_$date") {
                    EmptyDayPlaceholder()
                }
            }
            
            item(key = "divider_$date") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
    
    // Sync logic: DailyTasks scroll -> DayTicker selection
    LaunchedEffect(listState, allDays, isProgrammaticScroll) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { _ ->
                if (!isProgrammaticScroll && allDays.isNotEmpty()) {
                    val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val key = visibleItem?.key as? String
                    if (key != null) {
                        val dateStr = when {
                            key.startsWith("header_") -> key.removePrefix("header_")
                            key.startsWith("allday_") -> key.removePrefix("allday_")
                            key.startsWith("empty_") -> key.removePrefix("empty_")
                            key.startsWith("divider_") -> key.removePrefix("divider_")
                            key.startsWith("event_") -> {
                                val parts = key.split("_")
                                if (parts.size >= 2) parts[1] else null
                            }
                            else -> null
                        }
                        
                        if (dateStr != null) {
                            try {
                                val date = LocalDate.parse(dateStr)
                                onVisibleDayChanged(date)
                            } catch (e: Exception) {
                                // Ignore malformed keys
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun DayHeader(date: LocalDate) {
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val dateStr = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
    val isToday = date == LocalDate.now()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
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
fun AllDayEventsRow(events: List<DraftEvent>) {
    val context = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
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
                    fontWeight = FontWeight.Bold,
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
    onExpandToggled: () -> Unit
) {
    val eventColor = event.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandToggled() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(52.dp)) {
                if (event.startTime != null) {
                    Text(
                        text = event.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (event.endTime != null) {
                        Text(
                            text = event.endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(eventColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (event.calendarName != null) {
                    Text(
                        text = event.calendarName,
                        style = MaterialTheme.typography.labelSmall,
                        color = eventColor.copy(alpha = 0.8f)
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
                    DetailRow(Icons.Default.Notes, event.description)
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
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
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
fun EmptyDayPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            stringResource(R.string.no_events_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
