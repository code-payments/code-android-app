package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraSmall
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.components.chat.utils.MessageReaction
import com.getcode.ui.components.chat.utils.MessageTip
import com.getcode.ui.components.chat.utils.ReplyMessageAnchor
import com.getcode.ui.components.chat.utils.localizedText
import com.getcode.ui.components.user.social.SenderNameDisplay
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.dashedBorder
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.getcode.ui.utils.measured
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
    wasSentAsFullMember: Boolean,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    tips: List<MessageTip>,
    showTips: () -> Unit,
    reactions: List<MessageReaction>,
    onAddReaction: (String) -> Unit,
    onRemoveReaction: (ID) -> Unit,
    onTap: (contentPadding: PaddingValues, touchOffset: Offset) -> Unit,
    onLongPress: () -> Unit,
    onDoubleClick: () -> Unit,
    showReactions: () -> Unit,
) {
    val alignment = if (isFromSelf) Alignment.CenterEnd else Alignment.CenterStart
    var originalMessagePreviewHeight by remember {
        mutableStateOf(0.dp)
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .sizeableWidth()
                .addIf(wasSentAsFullMember) {
                    Modifier.background(color = color, shape = shape)
                }
                .addIf(!wasSentAsFullMember) {
                    Modifier.dashedBorder(
                        strokeWidth = CodeTheme.dimens.border,
                        dashWidth = 2.dp,
                        gapWidth = 2.dp,
                        dashColor = CodeTheme.colors.tertiary,
                        shape = shape
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            onTap(
                                PaddingValues(
                                    vertical = originalMessagePreviewHeight + 2.5.dp
                                ),
                                offset
                            )
                        },
                        onLongPress = if (!options.isInteractive) null else {
                            { onLongPress() }
                        },
                        onDoubleTap = { if (options.canTip) onDoubleClick() },
                    )
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
                        reactions = reactions,
                        onAddReaction = onAddReaction,
                        onRemoveReaction = onRemoveReaction,
                        onViewFeedback = showReactions,
                    )
                }.first().measure(constraints)

                val replyPreviewPlaceable = subcompose("MessageReplyPreview") {
                    MessageReplyPreview(
                        modifier = Modifier
                            .widthIn(min = messageContentPlaceable.width.toDp())
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onOriginalMessageClicked() }
                                )
                            }
                            .measured { originalMessagePreviewHeight = it.height },
                        originalMessage = originalMessage,
                        backgroundColor = Color.Black.copy(0.1f),
                    )
                }.first().measure(
                    constraints.copy(
                        minWidth = messageContentPlaceable.width,
                        maxWidth = constraints.maxWidth
                    )
                )

                // Determine the final width based on the longer of the two components
                val finalWidth =
                    maxOf(messageContentPlaceable.width, replyPreviewPlaceable.width)

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
                        reactions = reactions,
                        onAddReaction = onAddReaction,
                        onRemoveReaction = onRemoveReaction,
                        onViewFeedback = showReactions,
                    )
                }.first().measure(
                    constraints.copy(minWidth = finalWidth, maxWidth = finalWidth)
                )

                // Calculate the total height
                val totalHeight =
                    replyPreviewPlaceable.height + spacing + remeasuredMessageContentPlaceable.height

                // Layout the components
                layout(finalWidth, totalHeight) {
                    replyPreviewPlaceable.place(0, 0)
                    remeasuredMessageContentPlaceable.place(
                        0,
                        replyPreviewPlaceable.height + spacing
                    )
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
            SenderNameDisplay(
                sender = originalMessage.sender,
                textColor = colors?.second ?: CodeTheme.colors.tertiary,
                textStyle = CodeTheme.typography.textSmall
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