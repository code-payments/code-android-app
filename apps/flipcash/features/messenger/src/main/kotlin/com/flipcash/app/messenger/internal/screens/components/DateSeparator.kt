package com.flipcash.app.messenger.internal.screens.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.util.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Composable
internal fun DateSeparatorRow(
    timestamp: Instant,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatDateSeparator(timestamp),
            style = CodeTheme.typography.caption,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun formatDateSeparator(instant: Instant): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Clock.System.now().toLocalDateTime(tz).date
    val messageDate = instant.toLocalDateTime(tz).date
    val dayDiff = todayDate.toEpochDays() - messageDate.toEpochDays()

    val time = instant.formatLocalized("h:mm a", is24Hour = is24Hour, if24Hour = "H:mm")

    return when {
        dayDiff == 0L -> "${stringResource(R.string.label_chatSeparator_today)} $time"
        dayDiff == 1L -> "${stringResource(R.string.label_chatReceipt_yesterday)} $time"
        dayDiff in 2L..6L -> "${instant.formatLocalized("EEEE")} $time"
        messageDate.year == todayDate.year -> "${instant.formatLocalized("MMM d")} $time"
        else -> "${instant.formatLocalized("MMM d, yyyy")} $time"
    }
}

// region Previews

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_AllRanges() {
    val now = Clock.System.now()
    Column {
        DateSeparatorRow(timestamp = now.minus(2.hours))
        DateSeparatorRow(timestamp = now.minus(1.days))
        DateSeparatorRow(timestamp = now.minus(3.days))
        DateSeparatorRow(timestamp = now.minus(30.days))
        DateSeparatorRow(timestamp = now.minus(400.days))
    }
}

// endregion