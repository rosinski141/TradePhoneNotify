package com.mati.tradenotify.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Single logcat tag so `adb logcat -s TradeNotify` shows the whole pipeline. */
const val TAG = "TradeNotify"

private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())

fun formatClock(epochMillis: Long): String = clockFormat.format(Date(epochMillis))

fun formatDateTime(epochMillis: Long): String = dateTimeFormat.format(Date(epochMillis))

/** Minutes since local midnight, for quiet-hours comparisons. */
fun minutesOfDay(epochMillis: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

fun formatRelative(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val delta = nowMillis - epochMillis
    return when {
        delta < 60_000 -> "just now"
        delta < 3_600_000 -> "${delta / 60_000}m ago"
        delta < 86_400_000 -> "${delta / 3_600_000}h ago"
        else -> "${delta / 86_400_000}d ago"
    }
}
