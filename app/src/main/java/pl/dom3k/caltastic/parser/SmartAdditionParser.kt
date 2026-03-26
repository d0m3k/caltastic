package pl.dom3k.caltastic.parser

import java.time.LocalDate
import java.time.LocalTime

data class DraftEvent(
    val id: Long? = null,
    val instanceId: Long? = null,
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
    fun parse(input: String): DraftEvent {
        if (input.isBlank()) return DraftEvent(title = "", date = null, startTime = null)

        var title = input
        var date: LocalDate? = null
        var startTime: LocalTime? = null

        // Date keywords
        val todayKeywords = listOf("dzisiaj", "today")
        val tomorrowKeywords = listOf("jutro", "tomorrow")

        for (keyword in todayKeywords) {
            if (title.contains(keyword, ignoreCase = true)) {
                date = LocalDate.now()
                title = title.replace(keyword, "", ignoreCase = true)
            }
        }

        if (date == null) {
            for (keyword in tomorrowKeywords) {
                if (title.contains(keyword, ignoreCase = true)) {
                    date = LocalDate.now().plusDays(1)
                    title = title.replace(keyword, "", ignoreCase = true)
                }
            }
        }

        // Time parsing HH:mm
        val timeRegex = """(\d{1,2}):(\d{2})""".toRegex()
        timeRegex.find(title)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour in 0..23 && minute in 0..59) {
                startTime = LocalTime.of(hour, minute)
                title = title.replace(match.value, "")
                if (date == null) date = LocalDate.now()
            }
        }

        // Time parsing HHmm (e.g., 1030) - only if startTime not found yet
        if (startTime == null) {
            val timeRegexNoSeparator = """\b(\d{1,2})(\d{2})\b""".toRegex()
            timeRegexNoSeparator.find(title)?.let { match ->
                val hour = match.groupValues[1].toInt()
                val minute = match.groupValues[2].toInt()
                if (hour in 0..23 && minute in 0..59) {
                    startTime = LocalTime.of(hour, minute)
                    title = title.replace(match.value, "")
                    if (date == null) date = LocalDate.now()
                }
            }
        }

        // Date parsing dd.MM
        val dateRegex = """(\d{1,2})\.(\d{2})""".toRegex()
        dateRegex.find(title)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            if (month in 1..12 && day in 1..31) {
                date = LocalDate.of(LocalDate.now().year, month, day)
                title = title.replace(match.value, "")
            }
        }

        return DraftEvent(
            title = title.trim().replace(Regex("\\s+"), " "),
            date = date,
            startTime = startTime,
            originalText = input
        )
    }
}
