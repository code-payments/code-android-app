package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.model.ID
import com.getcode.model.chat.Deleter
import com.getcode.model.chat.MessageStatus
import com.getcode.model.chat.Sender
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import kotlinx.datetime.Instant

@Composable
internal fun MessageNodeScope.DeletedMessage(
    modifier: Modifier = Modifier,
    sender: Sender,
    shape: Shape,
    deletedBy: Deleter?,
    date: Instant,
) {
    val alignment = if (sender.isSelf) Alignment.CenterEnd else Alignment.CenterStart

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        BoxWithConstraints(
            modifier = Modifier
                .sizeableWidth()
                .border(
                    color = CodeTheme.colors.tertiary,
                    width = 1.dp,
                    shape = shape,
                )
                .padding(CodeTheme.dimens.grid.x2)
        ) contents@{
            val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            Column(
                modifier = Modifier
                    // add top padding to accommodate ascents
                    .padding(top = CodeTheme.dimens.grid.x1),
            ) {

                val message = buildAnnotatedString {
                        when {
                            deletedBy?.isSelf == true -> {
                                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                                append(stringResource(R.string.title_messageDeletedByYou))
                                pop()
                            }
                            deletedBy?.isHost == true -> {
                                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                                append(stringResource(R.string.title_messageDeletedByHost))
                                pop()
                            }
                            else -> {
                                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                                append(stringResource(R.string.title_messageWasDeleted))
                                pop()
                            }
                        }
                }

                MessageContent(
                    maxWidth = maxWidthPx,
                    annotatedMessage = message,
                    date = date,
                    status = MessageStatus.Unknown,
                    isFromSelf = sender.isSelf,
                    isFromBlockedMember = sender.isBlocked,
                    options = MessageNodeOptions(
                        contentStyle = MessageNodeDefaults.ContentStyle.copy(
                            color = CodeTheme.colors.textSecondary,
                            fontWeight = FontWeight.W400,
                        )
                    ),
                )
            }
        }
    }
}