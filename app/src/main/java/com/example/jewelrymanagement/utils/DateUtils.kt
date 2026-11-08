package com.example.jewelrymanagement.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dates are stored in SQLite as "yyyy-MM-dd HH:mm:ss" (local time) so that plain string
 * comparison in SQL matches chronological order.
 */
object DateUtils {
    const val DB_PATTERN = "yyyy-MM-dd HH:mm:ss"

    private fun dbFormat() = SimpleDateFormat(DB_PATTERN, Locale.US)

    fun now(): String = dbFormat().format(Date())

    fun format(date: Date): String = dbFormat().format(date)

    fun parse(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return try {
            dbFormat().parse(value)
        } catch (e: Exception) {
            null
        }
    }

    /** "2026-12-01 08:30:00" -> "01/12/2026 08:30"; returns the input unchanged if unparsable. */
    fun toDisplay(value: String?): String {
        val date = parse(value) ?: return value ?: ""
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(date)
    }

    /** "2026-12-01" -> "01/12/2026". */
    fun dayToDisplay(day: String): String {
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day) ?: return day
            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d)
        } catch (e: Exception) {
            day
        }
    }
}
