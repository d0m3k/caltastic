package pl.dom3k.caltastic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.dom3k.caltastic.parser.DraftEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DayTicker(
    days: List<LocalDate>,
    events: Map<LocalDate, List<DraftEvent>>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDateFocused: (LocalDate) -> Unit,
    isProgrammaticScroll: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    // Scroll to the selected date if it changes externally (e.g. from DailyTasks scroll)
    LaunchedEffect(selectedDate) {
        if (!listState.isScrollInProgress) {
            val index = days.indexOf(selectedDate)
            if (index >= 0) {
                // Offset to center the selected date roughly at the 2nd position
                listState.animateScrollToItem(index, scrollOffset = -150)
            }
        }
    }

    // Report "focused" date (item at a certain offset) when user scrolls the ticker
    // We restart this effect if isProgrammaticScroll changes to correctly suppress sync-back
    LaunchedEffect(listState.isScrollInProgress, isProgrammaticScroll) {
        if (listState.isScrollInProgress && !isProgrammaticScroll) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { firstVisible ->
                    // Focus on the 2nd visible item if available to match the offset used in animateScrollToItem
                    val focusIndex = (firstVisible + 1).coerceAtMost(days.size - 1)
                    if (focusIndex >= 0 && focusIndex < days.size) {
                        onDateFocused(days[focusIndex])
                    }
                }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth(), // Removed vertical padding
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp) // Explicitly set 0dp vertical
        ) {
            itemsIndexed(days) { index, date ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isNewMonth = index > 0 && date.month != days[index - 1].month
                    val isNewWeek = index > 0 && date.dayOfWeek == DayOfWeek.MONDAY

                    if (isNewMonth) {
                        // Bigger divide between months
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .width(2.5.dp)
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        )
                    } else if (isNewWeek) {
                        // Divider between Sunday and Monday
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(1.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }

                    if (index > 0) {
                        val spacing = if (isNewMonth) 2.dp else if (isNewWeek) 4.dp else 4.dp
                        Spacer(modifier = Modifier.width(spacing))
                    }

                    DayTickerItem(
                        date = date,
                        isSelected = date == selectedDate,
                        events = events[date] ?: emptyList(),
                        onClick = { onDateSelected(date) }
                    )
                }
            }
        }
    }
}

@Composable
fun DayTickerItem(
    date: LocalDate,
    isSelected: Boolean,
    events: List<DraftEvent>,
    onClick: () -> Unit
) {
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    val dayOfMonth = date.dayOfMonth.toString()
    val isToday = date == LocalDate.now()
    
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                      else if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                      else Color.Transparent,
        label = "containerColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary 
                      else if (isToday) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "elevation"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = elevation,
        modifier = Modifier
            .width(52.dp)
            .height(100.dp) 
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.fillMaxSize().padding(top = 8.dp)
        ) {
            Text(
                text = dayOfWeek.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = contentColor.copy(alpha = if (isSelected) 1f else 0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dayOfMonth,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Bold,
                color = contentColor,
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Line views for events
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val (allDay, timed) = events.partition { it.isAllDay }
                
                // All day indicators: stacked full width bars
                allDay.take(3).forEach { event ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                event.color?.let { Color(it) } 
                                ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                    )
                }
                
                // Timed events: each on its own row, proportionally placed
                // Show up to 5 bars total (all-day + timed)
                val remainingSlots = (5 - allDay.size.coerceAtMost(3)).coerceAtLeast(0)
                timed.take(remainingSlots).forEach { event ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                    ) {
                        val startPercent = (event.startTime?.toSecondOfDay()?.toFloat() ?: 0f) / (24 * 3600f)
                        val endPercent = (event.endTime?.toSecondOfDay()?.toFloat() ?: (event.startTime?.toSecondOfDay()?.plus(3600f) ?: 0f)) / (24 * 3600f)
                        val durationPercent = (endPercent - startPercent).coerceIn(0.15f, 1f)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(durationPercent)
                                .fillMaxHeight()
                                .align(Alignment.CenterStart)
                                .offset(x = (40.dp) * startPercent)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(
                                    event.color?.let { Color(it) } 
                                    ?: MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        }
    }
}
