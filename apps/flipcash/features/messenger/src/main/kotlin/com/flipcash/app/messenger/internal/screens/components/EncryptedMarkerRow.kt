package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.theme.CodeTheme
import kotlin.time.Clock

/**
 * "🔒 Encrypted ›" (node 10416:1404), above the first message that arrived end-to-end encrypted.
 * [above] is the date separator or unread divider that shares its gap, drawn first so the reader
 * sees the day, then the divider, then the marker and the message.
 */
@Composable
internal fun EncryptedMarkerRow(
    above: ChatListItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (above) {
            is ChatListItem.DateSeparator -> DateSeparatorRow(above.timestamp)
            is ChatListItem.UnreadDivider -> UnreadDividerRow(count = above.count, date = above.date)
            else -> Unit
        }
        Row(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(12.dp),
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.label_e2eeMarker),
                style = CodeTheme.typography.textSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = CodeTheme.colors.textSecondary,
            )
            Icon(
                modifier = Modifier.size(14.dp),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_EncryptedMarker() {
    Column {
        EncryptedMarkerRow(above = null, onClick = {})
        EncryptedMarkerRow(above = ChatListItem.DateSeparator(Clock.System.now()), onClick = {})
        EncryptedMarkerRow(above = ChatListItem.UnreadDivider(count = 3), onClick = {})
    }
}
