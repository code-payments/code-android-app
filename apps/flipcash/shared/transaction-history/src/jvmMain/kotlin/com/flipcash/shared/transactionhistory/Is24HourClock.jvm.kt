package com.flipcash.shared.transactionhistory

import androidx.compose.runtime.Composable
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Desktop has no clock setting to read, so this follows the locale's short time pattern. */
@Composable
internal actual fun is24HourClock(): Boolean {
    val format = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    return (format as? SimpleDateFormat)?.toPattern()?.contains('H') ?: false
}
