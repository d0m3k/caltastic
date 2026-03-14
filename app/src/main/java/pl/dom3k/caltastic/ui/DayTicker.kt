package pl.dom3k.caltastic.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DayTicker(
    days: List<LocalDate>,
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
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            itemsIndexed(days) { _, date ->
                DayTickerItem(
                    date = date,
                    isSelected = date == selectedDate,
                    onClick = { onDateSelected(date) }
                )
            }
        }
    }
}

@Composable
fun DayTickerItem(
    date: LocalDate,
    isSelected: Boolean,
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = elevation,
        modifier = Modifier
            .width(56.dp)
            .height(72.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = dayOfWeek.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = contentColor.copy(alpha = if (isSelected) 1f else 0.6f),
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dayOfMonth,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Bold,
                color = contentColor,
                fontSize = 20.sp
            )
            
            if (isToday && !isSelected) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
