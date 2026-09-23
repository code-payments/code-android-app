package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.messenger.internal.unreadCountLabel
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * "N Unread Messages", a caption between two hairlines above the first message that arrived since
 * the viewer last read the chat. When a day also changes at that gap, [date] is drawn above it, so the
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CodeTheme.dimens.inset,
                    vertical = CodeTheme.dimens.grid.x2,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = CodeTheme.colors.divider)
            Text(
                text = pluralStringResource(R.plurals.title_chatUnreadDivider, count, unreadCountLabel(count)),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = CodeTheme.colors.divider)
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
