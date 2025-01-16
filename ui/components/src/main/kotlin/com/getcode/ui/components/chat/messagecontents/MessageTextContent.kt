package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.R
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.components.text.markup.MarkupTextHelper
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedLongClickable
import com.getcode.util.formatDateRelatively
import kotlinx.datetime.Instant
import kotlin.math.max

sealed interface MessageControlAction {
    val onSelect: () -> Unit

    @get:Composable
    val painter: Painter
    val isDestructive: Boolean
    val delayUponSelection: Boolean

    data class Copy(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = false
        override val delayUponSelection: Boolean = false

        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.ContentCopy)
    }

    data class Reply(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = false
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.AutoMirrored.Default.Reply)
        override val delayUponSelection: Boolean = false
    }

    data class Tip(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = false
        override val painter: Painter
            @Composable get() = painterResource(R.drawable.ic_kin_white_small)
        override val delayUponSelection: Boolean = true
    }

    data class Delete(override val onSelect: () -> Unit) : MessageControlAction {
        override val isDestructive: Boolean = true
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Delete)
        override val delayUponSelection: Boolean = false
    }

    data class RemoveUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.PersonRemove)
        override val delayUponSelection: Boolean = false
    }

    data class MuteUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.VoiceOverOff)
        override val delayUponSelection: Boolean = false
    }

    data class ReportUserForMessage(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Flag)
        override val delayUponSelection: Boolean = false
    }

    data class BlockUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = true
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Block)
        override val delayUponSelection: Boolean = false
    }

    data class UnblockUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction {
        override val isDestructive: Boolean = false
        override val painter: Painter
            @Composable get() = rememberVectorPainter(Icons.Default.Person)
        override val delayUponSelection: Boolean = false
    }
}

data class MessageControls(
    val actions: List<MessageControlAction> = emptyList()
) {
    val hasAny: Boolean
        get() = actions.isNotEmpty()
}

@Composable
internal fun MessageNodeScope.MessageText(
    modifier: Modifier = Modifier,
    content: String,
    shape: Shape = MessageNodeDefaults.DefaultShape,
    options: MessageNodeOptions,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    showControls: () -> Unit,
    showTipModal: () -> Unit
) {
    val alignment = if (isFromSelf) Alignment.CenterEnd else Alignment.CenterStart

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        BoxWithConstraints(
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
        ) contents@{
            val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            Column {
                MessageContent(
                    maxWidth = maxWidthPx,
                    message = content,
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    isFromBlockedMember = isFromBlockedMember,
                    options = options,
                    onLongPress = showControls,
                    onDoubleClick = showTipModal
                )
            }
        }
    }
}

@Composable
private fun rememberAlignmentRule(
    contentTextStyle: TextStyle,
    minWidth: Int = 0,
    maxWidth: Int,
    message: AnnotatedString,
    date: Instant
): State<AlignmentRule?> {
    val density = LocalDensity.current
    val dateTextStyle = DateWithStatusDefaults.DateTextStyle
    val iconSizePx = with(density) { DateWithStatusDefaults.IconWidth.roundToPx() }
    val spacingPx = with(density) { DateWithStatusDefaults.Spacing.roundToPx() }
    val contentPaddingPx = with(density) { CodeTheme.dimens.grid.x2.roundToPx() }

    return remember(minWidth, maxWidth, message, date) {
        mutableStateOf<AlignmentRule?>(null)
    }.apply {
        val textMeasurer = rememberTextMeasurer()
        val dateStatusWidth = remember(message, date) {
            val result = textMeasurer.measure(
                text = date.formatDateRelatively(),
                style = dateTextStyle,
                maxLines = 1
            )

            max(minWidth, result.size.width + spacingPx + iconSizePx)
        }

        val bufferSize by remember(dateStatusWidth) {
            derivedStateOf {
                dateStatusWidth + spacingPx + contentPaddingPx * 2
            }
        }

        if (value == null) {
            Text(
                modifier = Modifier.drawWithContent { },
                text = message,
                style = contentTextStyle,
                onTextLayout = { textLayoutResult ->
                    val lastLineNum = textLayoutResult.lineCount - 1
                    val lineStart = textLayoutResult.getLineStart(lastLineNum)
                    val lineEnd =
                        textLayoutResult.getLineEnd(lastLineNum, visibleEnd = true)
                    val lineContent = message.substring(lineStart, lineEnd)

                    val lineContentWidth =
                        textMeasurer.measure(lineContent, contentTextStyle).size.width

                    value = when {
                        lineContentWidth + bufferSize > maxWidth -> AlignmentRule.Column
                        textLayoutResult.lineCount == 1 -> AlignmentRule.SingleLineEnd
                        else -> AlignmentRule.ParagraphLastLine
                    }
                }
            )
        }
    }
}

@Composable
internal fun MessageContent(
    modifier: Modifier = Modifier,
    minWidth: Int = 0,
    maxWidth: Int,
    message: String,
    date: Instant,
    status: MessageStatus,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    options: MessageNodeOptions,
    onLongPress: () -> Unit = { },
    onDoubleClick: () -> Unit = { },
) {
    MessageContent(
        modifier = modifier,
        minWidth = minWidth,
        maxWidth = maxWidth,
        annotatedMessage = AnnotatedString(message),
        date = date,
        status = status,
        isFromSelf = isFromSelf,
        isFromBlockedMember = isFromBlockedMember,
        options = options,
        onLongPress = onLongPress,
        onDoubleClick = onDoubleClick
    )
}

@Composable
internal fun MessageContent(
    modifier: Modifier = Modifier,
    minWidth: Int = 0,
    maxWidth: Int,
    annotatedMessage: AnnotatedString,
    date: Instant,
    status: MessageStatus,
    isFromSelf: Boolean,
    isFromBlockedMember: Boolean,
    options: MessageNodeOptions,
    onLongPress: () -> Unit = { },
    onDoubleClick: () -> Unit = { },
) {
    val alignmentRule by rememberAlignmentRule(
        contentTextStyle = options.contentStyle,
        minWidth = minWidth,
        maxWidth = maxWidth,
        message = annotatedMessage,
        date = date,
    )

    when (alignmentRule) {
        AlignmentRule.Column -> {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                MarkupTextHandler(
                    text = annotatedMessage,
                    options = options,
                    onLongPress = onLongPress,
                    isFromBlockedMember = isFromBlockedMember,
                    onDoubleClick = onDoubleClick,
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = if (!options.isInteractive) null else {
                                    { onLongPress() }
                                },
                            )
                        },
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = options.showStatus,
                    showTimestamp = options.showTimestamp,
                )
            }
        }

        AlignmentRule.ParagraphLastLine -> {
            Column(
                modifier = modifier.padding(CodeTheme.dimens.grid.x1)
            ) {
                MarkupTextHandler(
                    text = annotatedMessage,
                    options = options,
                    onLongPress = onLongPress,
                    isFromBlockedMember = isFromBlockedMember,
                    onDoubleClick = onDoubleClick,
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = if (!options.isInteractive) null else {
                                    { onLongPress() }
                                },
                            )
                        },
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = options.showStatus,
                    showTimestamp = options.showTimestamp,
                )
            }
        }

        AlignmentRule.SingleLineEnd -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                MarkupTextHandler(
                    text = annotatedMessage,
                    options = options,
                    onLongPress = onLongPress,
                    isFromBlockedMember = isFromBlockedMember,
                    onDoubleClick = onDoubleClick,
                )
                DateWithStatus(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x1 + 2.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = if (!options.isInteractive) null else {
                                    { onLongPress() }
                                },
                            )
                        },
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = options.showStatus,
                    showTimestamp = options.showTimestamp,
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun MarkupTextHandler(
    text: AnnotatedString,
    options: MessageNodeOptions,
    isFromBlockedMember: Boolean,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = { },
    onDoubleClick: () -> Unit,
) {
    when {
        isFromBlockedMember -> {
            Text(
                modifier = modifier,
                text = AnnotatedString.Builder().apply {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(stringResource(R.string.title_blockedMessage))
                    pop()
                }.toAnnotatedString(),
                style = options.contentStyle
            )

        }

        options.onMarkupClicked != null -> {
            val handler = options.onMarkupClicked
            val markupTextHelper = remember { MarkupTextHelper() }
            val markups = options.markupsToResolve.map { Markup.create(it) }

            val annotatedString = markupTextHelper.annotate(text.text, markups)

            val handleTouchedContent = { offset: Int ->
                annotatedString.getStringAnnotations(
                    tag = Markup.RoomNumber.TAG,
                    start = offset,
                    end = offset
                )
                    .firstOrNull()?.let { annotation ->
                        handler.invoke(Markup.RoomNumber(annotation.item.toLong()))
                    }

                annotatedString.getStringAnnotations(
                    tag = Markup.Url.TAG,
                    start = offset,
                    end = offset
                )
                    .firstOrNull()?.let { annotation ->
                        handler.invoke(Markup.Url(annotation.item))
                    }

                annotatedString.getStringAnnotations(
                    tag = Markup.Phone.TAG,
                    start = offset,
                    end = offset
                ).firstOrNull()?.let { annotation ->
                    handler.invoke(Markup.Phone(annotation.item))
                }
            }

            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            Text(
                text = annotatedString,
                style = options.contentStyle.copy(color = CodeTheme.colors.textMain),
                modifier = modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                layoutResult?.let { layoutResult ->
                                    val position = layoutResult.getOffsetForPosition(offset)
                                    handleTouchedContent(position)
                                }
                            },
                            onDoubleTap = { _ -> onDoubleClick() },
                            onLongPress = if (!options.isInteractive) null else {
                                { onLongPress() }
                            },
                        )
                    },
                onTextLayout = { layoutResult = it }
            )
        }

        else -> {
            Text(modifier = modifier, text = text, style = options.contentStyle)
        }
    }
}

sealed interface AlignmentRule {
    data object ParagraphLastLine : AlignmentRule
    data object Column : AlignmentRule
    data object SingleLineEnd : AlignmentRule
}