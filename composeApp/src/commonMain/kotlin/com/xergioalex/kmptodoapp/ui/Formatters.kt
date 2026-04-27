package com.xergioalex.kmptodoapp.ui

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun formatDueDate(instant: Instant): String {
    val ldt: LocalDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ldt.monthNumber.toString().padStart(2, '0')
    val day = ldt.dayOfMonth.toString().padStart(2, '0')
    val hour = ldt.hour.toString().padStart(2, '0')
    val minute = ldt.minute.toString().padStart(2, '0')
    return "${ldt.year}-$month-$day  $hour:$minute"
}
