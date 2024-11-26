package com.getcode.ui.components.chat.messagecontents

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.components.text.markup.Markup
import com.getcode.ui.components.text.markup.MarkupTextHelper
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedLongClickable
import com.getcode.util.formatDateRelatively
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

sealed interface MessageControlAction {
    val onSelect: () -> Unit

    data class Copy(override val onSelect: () -> Unit) : MessageControlAction
    data class Delete(override val onSelect: () -> Unit) : MessageControlAction
    data class RemoveUser(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction

    data class MuteUser(val name: String, override val onSelect: () -> Unit) : MessageControlAction
    data class ReportUserForMessage(val name: String, override val onSelect: () -> Unit) :
        MessageControlAction
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
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    showControls: () -> Unit,
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
            Column(
                modifier = Modifier.background(color),
            ) {
                MessageContent(
                    maxWidth = maxWidthPx,
                    message = content,
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    options = options,
                    onLongPress = { showControls() }
                )
            }
        }
    }
}

@Composable
private fun rememberAlignmentRule(
    contentTextStyle: TextStyle,
    maxWidth: Int,
    message: String,
    date: Instant
): State<AlignmentRule?> {
    val density = LocalDensity.current
    val dateTextStyle = DateWithStatusDefaults.DateTextStyle
    val iconSizePx = with(density) { DateWithStatusDefaults.IconWidth.roundToPx() }
    val spacingPx = with(density) { DateWithStatusDefaults.Spacing.roundToPx() }
    val contentPaddingPx = with(density) { CodeTheme.dimens.grid.x2.roundToPx() }

    return remember(maxWidth, message, date) {
        mutableStateOf<AlignmentRule?>(null)
    }.apply {
        val textMeasurer = rememberTextMeasurer()
        val dateStatusWidth = remember(message, date) {
            val result = textMeasurer.measure(
                text = date.formatDateRelatively(),
                style = dateTextStyle,
                maxLines = 1
            )
            result.size.width + spacingPx + iconSizePx
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
    maxWidth: Int,
    message: String,
    date: Instant,
    status: MessageStatus,
    isFromSelf: Boolean,
    options: MessageNodeOptions,
    onLongPress: () -> Unit = { },
) {
    val alignmentRule by rememberAlignmentRule(
        contentTextStyle = options.contentStyle,
        maxWidth = maxWidth,
        message = message,
        date = date,
    )

    when (alignmentRule) {
        AlignmentRule.Column -> {
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                MarkupTextHandler(
                    text = message,
                    options = options,
                    onLongPress = onLongPress,
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End)
                        .pointerInput(Unit) { detectTapGestures {  }},
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
                modifier = Modifier.padding(CodeTheme.dimens.grid.x1),
                verticalArrangement = Arrangement.spacedBy(-(CodeTheme.dimens.grid.x2))) {
                MarkupTextHandler(
                    text = message,
                    options = options,
                    onLongPress = onLongPress,
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End)
                        .pointerInput(Unit) { detectTapGestures {  }},
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
                    text = message,
                    options = options,
                    onLongPress = onLongPress,
                )
                DateWithStatus(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x1 + 2.dp)
                        .pointerInput(Unit) { detectTapGestures {  }},
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
    text: String,
    options: MessageNodeOptions,
    modifier: Modifier = Modifier,
    onLongPress: () -> Unit = { },
) {
    if (options.onMarkupClicked != null) {
        val handler = options.onMarkupClicked
        val markupTextHelper = remember { MarkupTextHelper() }
        val markups = options.markupsToResolve.map { Markup.create(it) }

        val annotatedString = markupTextHelper.annotate(text, markups)

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
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        layoutResult?.let { layoutResult ->
                            val position = layoutResult.getOffsetForPosition(offset)
                            handleTouchedContent(position)
                        }
                    },
                    onLongPress = if (!options.isInteractive) null else { { onLongPress() } },
                )
            },
            onTextLayout = { layoutResult = it }
        )
    } else {
        Text(modifier = modifier, text = text, style = options.contentStyle)
    }
}

sealed interface AlignmentRule {
    data object ParagraphLastLine : AlignmentRule
    data object Column : AlignmentRule
    data object SingleLineEnd : AlignmentRule
}