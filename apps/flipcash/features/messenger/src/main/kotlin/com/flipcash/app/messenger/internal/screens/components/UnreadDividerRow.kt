package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.messenger.internal.unreadCountLabel
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * "N Unread Messages", a full-width band above the first message that arrived since the viewer
 * last read the chat. When a day also changes at that gap, [date] is drawn above the band, so the
 * reader sees the day, then the divider, then the messages.
 */
@Composable
internal fun UnreadDividerRow(
    count: Int,
    date: Instant?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (date != null) DateSeparatorRow(date)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodeTheme.colors.divider)
                .padding(vertical = CodeTheme.dimens.grid.x2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = pluralStringResource(R.plurals.title_chatUnreadDivider, count, unreadCountLabel(count)),
                style = CodeTheme.typography.caption.copy(fontWeight = FontWeight.W700),
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

// region Previews

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UnreadDivider() {
    Column {
        UnreadDividerRow(count = 1, date = null)
        UnreadDividerRow(count = 12, date = Clock.System.now())
        UnreadDividerRow(count = 250, date = null)
    }
}

// endregion
