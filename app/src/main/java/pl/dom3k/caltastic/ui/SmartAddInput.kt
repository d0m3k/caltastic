package pl.dom3k.caltastic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.dom3k.caltastic.parser.DraftEvent
import pl.dom3k.caltastic.parser.SmartAdditionParser
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAddInput(
    onAddEvent: (DraftEvent) -> Unit,
    defaultCalendarName: String,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val parser = remember { SmartAdditionParser() }

    val draftEvent by remember(text) {
        derivedStateOf { parser.parse(text) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, start = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Szybkie dodawanie",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Kalendarz: $defaultCalendarName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Np. Obiad z mamą jutro 14:00-15:30") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (draftEvent.isComplete) {
                            onAddEvent(draftEvent)
                            text = ""
                        }
                    }
                ),
                trailingIcon = {
                    FilledIconButton(
                        onClick = {
                            if (draftEvent.isComplete) {
                                onAddEvent(draftEvent)
                                text = ""
                            }
                        },
                        enabled = draftEvent.isComplete,
                        modifier = Modifier.padding(end = 4.dp).size(40.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Event")
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            AnimatedVisibility(
                visible = text.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ParseDetails(draftEvent = draftEvent, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
fun ParseDetails(draftEvent: DraftEvent, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Podgląd:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = draftEvent.title.ifBlank { "..." },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draftEvent.date != null) {
                InfoChip(
                    icon = Icons.Default.CalendarMonth,
                    text = draftEvent.date.format(DateTimeFormatter.ofPattern("d MMMM"))
                )
            }
            if (draftEvent.startTime != null) {
                val timeText = if (draftEvent.endTime != null) {
                    "${draftEvent.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${draftEvent.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                } else {
                    draftEvent.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                }
                InfoChip(
                    icon = Icons.Default.Schedule,
                    text = timeText
                )
            } else if (draftEvent.isAllDay) {
                InfoChip(
                    icon = Icons.Default.Schedule,
                    text = "Cały dzień"
                )
            }
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}
