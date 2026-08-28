package com.absenku.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * DateFormatter — Indonesian-localized date/time helpers.
 * All DB-stored timestamps are epoch-millis; we format here for display.
 */
object DateFormatter {

    private val indo = Locale("id", "ID")

    /** Date only: "Senin, 28 Agustus 2026" */
    fun formatDate(millis: Long): String = try {
        SimpleDateFormat("EEEE, dd MMMM yyyy", indo).format(Date(millis))
    } catch (e: Exception) { SimpleDateFormat.getDateInstance().format(Date(millis)) }

    /** Date + time: "28 Agustus 2026 14:30" */
    fun formatDateTime(millis: Long): String = try {
        SimpleDateFormat("dd MMMM yyyy HH:mm", indo).format(Date(millis))
    } catch (e: Exception) { "${formatDate(millis)} ${SimpleDateFormat("HH:mm").format(Date(millis))}" }

    /** Time only: "14:30" */
    fun formatTime(millis: Long): String = SimpleDateFormat("HH:mm", indo).format(Date(millis))

    /** DB date key: "yyyy-MM-dd" */
    fun toDbDate(millis: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    /** Now string for UI: "Senin, 28 Agt 2026 · 14:30" */
    fun nowDisplayString(): String = formatDateTime(System.currentTimeMillis())

    /** Current HH:mm for clock display. */
    fun nowClock(): String = formatTime(System.currentTimeMillis())
}
