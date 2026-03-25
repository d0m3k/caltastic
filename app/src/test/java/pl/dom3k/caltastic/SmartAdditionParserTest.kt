package pl.dom3k.caltastic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dom3k.caltastic.parser.SmartAdditionParser
import java.time.LocalDate
import java.time.LocalTime

class SmartAdditionParserTest {

    private val parser = SmartAdditionParser()

    @Test
    fun testParseTomorrowWithTime() {
        val result = parser.parse("Kawa jutro 13:00")
        
        assertEquals("Kawa", result.title)
        assertEquals(LocalDate.now().plusDays(1), result.date)
        assertEquals(LocalTime.of(13, 0), result.startTime)
        assertTrue(result.isComplete)
    }

    @Test
    fun testParseTodayNoTime() {
        val result = parser.parse("dzisiaj spotkanie")
        
        assertEquals("spotkanie", result.title)
        assertEquals(LocalDate.now(), result.date)
        assertEquals(null, result.startTime)
        assertTrue(result.isComplete)
    }

    @Test
    fun testParseExactDateAndTime() {
        val result = parser.parse("Wizyta u lekarza 12.04 15:30")
        
        assertEquals("Wizyta u lekarza", result.title)
        assertEquals(LocalDate.of(LocalDate.now().year, 4, 12), result.date)
        assertEquals(LocalTime.of(15, 30), result.startTime)
        assertTrue(result.isComplete)
    }

    @Test
    fun testParseOnlyTime() {
        val result = parser.parse("Lunch 12:30")
        
        assertEquals("Lunch", result.title)
        assertEquals(LocalDate.now(), result.date)
        assertEquals(LocalTime.of(12, 30), result.startTime)
        assertTrue(result.isComplete)
    }
}
