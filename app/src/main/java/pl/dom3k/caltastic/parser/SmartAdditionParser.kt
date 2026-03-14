package pl.dom3k.caltastic.parser

import java.time.LocalDate
import java.time.LocalTime

data class DraftEvent(
    val title: String,
    val date: LocalDate?,
    val startTime: LocalTime?,
    val endTime: LocalTime? = null,
    val isAllDay: Boolean = false,
    val color: Int? = null,
    val calendarName: String? = null,
    val location: String? = null,
    val description: String? = null,
    val originalText: String = ""
) {
    val isComplete: Boolean
        get() = title.isNotBlank() && date != null
}

class SmartAdditionParser {

    private val timeRegex = Regex("""(\d{1,2}):(\d{2})""")
    private val timeRangeRegex = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""")
    private val dateRegex = Regex("""(\d{1,2})[\./-](\d{1,2})""")

    fun parse(input: String): DraftEvent {
        var parsedDate: LocalDate? = null
        var parsedStartTime: LocalTime? = null
        var parsedEndTime: LocalTime? = null
        var remainingTitle = input

        // 1. Extract Time Range
        val timeRangeMatch = timeRangeRegex.find(input)
        if (timeRangeMatch != null) {
            try {
                val startHour = timeRangeMatch.groupValues[1].toInt()
                val startMinute = timeRangeMatch.groupValues[2].toInt()
                val endHour = timeRangeMatch.groupValues[3].toInt()
                val endMinute = timeRangeMatch.groupValues[4].toInt()
                
                parsedStartTime = LocalTime.of(startHour, startMinute)
                parsedEndTime = LocalTime.of(endHour, endMinute)
                remainingTitle = remainingTitle.replace(timeRangeMatch.value, "")
            } catch (e: Exception) {
                // Ignore invalid time range
            }
        } else {
            // 1b. Extract Single Time
            val timeMatch = timeRegex.find(input)
            if (timeMatch != null) {
                try {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toInt()
                    parsedStartTime = LocalTime.of(hour, minute)
                    remainingTitle = remainingTitle.replace(timeMatch.value, "")
                } catch (e: Exception) {
                    // Ignore invalid time like 25:61
                }
            }
        }

        // 2. Extract relative dates
        val lowerInput = input.lowercase()
        val today = LocalDate.now()
        
        when {
            lowerInput.contains("dzisiaj") || lowerInput.contains("dziś") -> {
                parsedDate = today
                remainingTitle = remainingTitle.replace(Regex("""(?i)\bdzisiaj\b|\bdziś\b"""), "")
            }
            lowerInput.contains("jutro") -> {
                parsedDate = today.plusDays(1)
                remainingTitle = remainingTitle.replace(Regex("""(?i)\bjutro\b"""), "")
            }
            lowerInput.contains("pojutrze") -> {
                parsedDate = today.plusDays(2)
                remainingTitle = remainingTitle.replace(Regex("""(?i)\bpojutrze\b"""), "")
            }
            else -> {
                // 3. Extract exact date
                val dateMatch = dateRegex.find(input)
                if (dateMatch != null) {
                    try {
                        val day = dateMatch.groupValues[1].toInt()
                        val month = dateMatch.groupValues[2].toInt()
                        val year = today.year // Assume current year for now
                        parsedDate = LocalDate.of(year, month, day)
                        remainingTitle = remainingTitle.replace(dateMatch.value, "")
                    } catch (e: Exception) {
                        // Ignore invalid date like 32.13
                    }
                }
            }
        }

        // 4. Clean up title
        val title = remainingTitle.trim().replace(Regex("""\s+"""), " ")

        return DraftEvent(
            title = title,
            date = parsedDate ?: (if (parsedStartTime != null) today else null), // If only time given, assume today
            startTime = parsedStartTime,
            endTime = parsedEndTime,
            isAllDay = parsedStartTime == null,
            originalText = input
        )
    }
}
