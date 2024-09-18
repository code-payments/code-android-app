package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.getcode.R
import com.getcode.model.MessageStatus
import com.getcode.theme.BrandLight
import com.getcode.theme.ChatOutgoing
import com.getcode.theme.CodeTheme
import com.getcode.util.formatTimeRelatively
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

object DateWithStatusDefaults {
    val DateTextStyle: TextStyle
        @Composable get() = CodeTheme.typography.caption
    val IconWidth: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x3
    val Spacing: Dp
        @Composable get() = CodeTheme.dimens.grid.x1
}

@Composable
internal fun DateWithStatus(
    modifier: Modifier = Modifier,
    date: Instant,
    isFromSelf: Boolean,
    status: MessageStatus
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DateWithStatusDefaults.Spacing),
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = date.formatTimeRelatively(),
            style = DateWithStatusDefaults.DateTextStyle,
            color = CodeTheme.colors.textSecondary,
            maxLines = 1
        )
        if (status.isValid() && isFromSelf) {
            Icon(
                modifier = Modifier
                    .requiredWidth(width = DateWithStatusDefaults.IconWidth)
                    .padding(top = CodeTheme.dimens.grid.x1 / 2),
                painter = painterResource(
                    id = when (status) {
                        MessageStatus.Sent -> R.drawable.ic_message_status_sent
                        MessageStatus.Delivered -> R.drawable.ic_message_status_delivered
                        MessageStatus.Read -> R.drawable.ic_message_status_read
                        else -> -1
                    }
                ),
                tint = Color.Unspecified,
                contentDescription = "status"
            )
        }
    }
}




@Preview
@Composable
private fun Preview_DateWithStatus() {
    CodeTheme {
        @Composable
        fun Bubble(
            status: MessageStatus
        ) {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .background(
                        color = CodeTheme.colors.secondary,
                        shape = MessageNodeDefaults.DefaultShape
                    )
                    .padding(CodeTheme.dimens.grid.x2)
            ) {
                DateWithStatus(date = Clock.System.now(), status = status, isFromSelf = true)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            Bubble(status = MessageStatus.Sent)
            Bubble(status = MessageStatus.Delivered)
            Bubble(status = MessageStatus.Read)
        }
    }
}