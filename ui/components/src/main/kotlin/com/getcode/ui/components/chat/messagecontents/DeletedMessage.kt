package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import kotlinx.datetime.Instant

@Composable
internal fun MessageNodeScope.DeletedMessage(
    modifier: Modifier = Modifier,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    date: Instant,
) {
    val alignment = if (isFromSelf) Alignment.CenterEnd else Alignment.CenterStart

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        BoxWithConstraints(
            modifier = Modifier
                .sizeableWidth()
                .border(color = CodeTheme.colors.tertiary, width = 1.dp,  shape = MessageNodeDefaults.DefaultShape)
                .padding(CodeTheme.dimens.grid.x2)
        ) contents@{
            val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            Column(
                modifier = Modifier
                    // add top padding to accommodate ascents
                    .padding(top = CodeTheme.dimens.grid.x1),
            ) {
                MessageContent(
                    maxWidth = maxWidthPx,
                    message = "Deleted message",
                    date = date,
                    status = MessageStatus.Unknown,
                    isFromSelf = isFromSelf,
                    isFromBlockedMember = isFromBlockedMember,
                    options = MessageNodeOptions(contentStyle = MessageNodeDefaults.ContentStyle),
                )
            }
        }
    }
}