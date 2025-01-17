package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.chat.utils.ReplyMessageAnchor
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.getcode.ui.utils.rememberedLongClickable
import kotlinx.datetime.Instant

@Composable
internal fun MessageNodeScope.MessageReplyContent(
    modifier: Modifier = Modifier,
    content: String,
    originalMessage: ReplyMessageAnchor,
    onOriginalMessageClicked: () -> Unit,
    shape: Shape = MessageNodeDefaults.DefaultShape,
    options: MessageNodeOptions,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    tips: List<MessageTip>,
    showTips: () -> Unit,
    showControls: () -> Unit,
    showTipSelection: () -> Unit
) {
    val alignment = if (isFromSelf) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .sizeableWidth()
                .background(
                    color = color,
                    shape = shape,
                )
                .addIf(options.isInteractive) {
                    Modifier.rememberedLongClickable {
                        showControls()
                    }
                }
                .padding(CodeTheme.dimens.grid.x2)
        ) {
            SubcomposeLayout { constraints ->
                val spacing = 2.5.dp.roundToPx()

                val messageContentPlaceable = subcompose("MessageContent") {
                    MessageContent(
                        maxWidth = constraints.maxWidth,
                        message = content,
                        date = date,
                        status = status,
                        isFromSelf = isFromSelf,
                        isFromBlockedMember = isFromBlockedMember,
                        options = options,
                        tips = tips,
                        openTipModal = showTips,
                        onLongPress = showControls,
                        onDoubleClick = showTipSelection,
                    )
                }.first().measure(constraints)

                val replyPreviewPlaceable = subcompose("MessageReplyPreview") {
                    MessageReplyPreview(
                        modifier = Modifier
                            .widthIn(min = messageContentPlaceable.width.toDp())
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = if (!options.isInteractive) null else {
                                        { showControls() }
                                    },
                                    onDoubleTap = { showTipSelection() },
                                    onTap = { onOriginalMessageClicked() }
                                )
                            },
                        originalMessage = originalMessage,
                        backgroundColor = Color.Black.copy(0.1f),
                    )
                }.first().measure(
                    constraints.copy(minWidth = messageContentPlaceable.width, maxWidth = constraints.maxWidth)
                )

                // Determine the final width based on the longer of the two components
                val finalWidth = maxOf(messageContentPlaceable.width, replyPreviewPlaceable.width)

                // Remeasure MessageContent with the updated width
                val remeasuredMessageContentPlaceable = subcompose("MessageContentRemeasured") {
                    MessageContent(
                        maxWidth = finalWidth,
                        minWidth = finalWidth,
                        message = content,
                        date = date,
                        status = status,
                        isFromSelf = isFromSelf,
                        isFromBlockedMember = isFromBlockedMember,
                        options = options,
                        tips = tips,
                        openTipModal = showTips,
                        onLongPress = showControls,
                        onDoubleClick = showTipSelection,
                    )
                }.first().measure(
                    constraints.copy(minWidth = finalWidth, maxWidth = finalWidth)
                )

                // Calculate the total height
                val totalHeight = replyPreviewPlaceable.height + spacing + remeasuredMessageContentPlaceable.height

                // Layout the components
                layout(finalWidth, totalHeight) {
                    replyPreviewPlaceable.place(0, 0)
                    remeasuredMessageContentPlaceable.place(0, replyPreviewPlaceable.height + spacing)
                }
            }
        }
    }
}

@Composable
fun MessageReplyPreview(
    originalMessage: ReplyMessageAnchor,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,

) {
    val colors = generateComplementaryColorPalette(originalMessage.sender.id!!)
    Row(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .height(IntrinsicSize.Min)
            .background(backgroundColor, CodeTheme.shapes.extraSmall)
            .clip(CodeTheme.shapes.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(colors?.first ?: CodeTheme.colors.tertiary)
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = CodeTheme.dimens.grid.x1)
                .padding(vertical = CodeTheme.dimens.grid.x1)
                .weight(1f)
        ) {
            Text(
                text = originalMessage.sender.displayName.orEmpty()
                    .ifEmpty { "Member" },
                color = colors?.second ?: CodeTheme.colors.tertiary,
                style = CodeTheme.typography.textSmall
            )

            val messageBody = when {
                originalMessage.isDeleted -> {
                    val deletionMessage = when {
                        originalMessage.deletedBy?.isSelf == true -> stringResource(R.string.title_messageDeletedByYou)
                        originalMessage.deletedBy?.isHost == true -> stringResource(R.string.title_messageDeletedByHost)
                        else -> stringResource(R.string.title_messageWasDeleted)
                    }
                    AnnotatedString.Builder().apply {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(deletionMessage)
                        pop()
                    }.toAnnotatedString()
                }

                originalMessage.sender.isBlocked -> {
                    AnnotatedString.Builder().apply {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(stringResource(R.string.title_blockedMessage))
                        pop()
                    }.toAnnotatedString()
                }

                else -> AnnotatedString(originalMessage.message.localizedText)
            }

            Text(
                text = messageBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = CodeTheme.colors.textMain,
                style = CodeTheme.typography.caption
            )
        }
    }
}