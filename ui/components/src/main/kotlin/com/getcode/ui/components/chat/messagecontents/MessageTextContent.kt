package com.getcode.ui.components.chat.messagecontents

import android.telephony.PhoneNumberUtils
import android.util.Patterns
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.Markup
import com.getcode.ui.components.chat.MessageNodeDefaults
import com.getcode.ui.components.chat.MessageNodeOptions
import com.getcode.ui.components.chat.MessageNodeScope
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedLongClickable
import com.getcode.util.formatDateRelatively
import kotlinx.datetime.Instant

sealed interface MessageControlAction {
    val onSelect: () -> Unit
    data class Copy(override val onSelect: () -> Unit): MessageControlAction
    data class Delete(override val onSelect: () -> Unit): MessageControlAction
    data class RemoveUser(val name: String, override val onSelect: () -> Unit): MessageControlAction
    data class MuteUser(val name: String, override val onSelect: () -> Unit): MessageControlAction
    data class ReportUserForMessage(val name: String, override val onSelect: () -> Unit): MessageControlAction
}

data class MessageControls(
    val actions: List<MessageControlAction> = emptyList()
) {
    val hasAny: Boolean
        get() = actions.isNotEmpty()

    val canCopy: Boolean
        get() = actions.any { it is MessageControlAction.Copy }

    val canDelete: Boolean
        get() = actions.any { it is MessageControlAction.Delete }

    val canRemoveUser: Boolean
        get() = actions.any { it is MessageControlAction.RemoveUser }
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
                    options = options
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
    maxWidth: Int,
    message: String,
    date: Instant,
    status: MessageStatus,
    isFromSelf: Boolean,
    options: MessageNodeOptions,
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
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End),
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
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End),
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = options.showStatus,
                    showTimestamp = options.showTimestamp,
                )
            }
        }

        AlignmentRule.SingleLineEnd -> {
            Row(horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)) {
                MarkupTextHandler(
                    text = message,
                    options = options,
                )
                DateWithStatus(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x1 + 2.dp),
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
    modifier: Modifier = Modifier
) {
    if (options.onMarkupClicked != null) {
        val handler = options.onMarkupClicked
        val annotatedString = buildAnnotatedString {
            val hashtagRegex = Regex("#\\d+")
            val urlMatcher = Patterns.WEB_URL.matcher(text)
            val phoneRegex = Regex("(\\+[0-9]+[ \\-.]*)?(\\([0-9]+\\)[ \\-.]*)?([0-9]+([ \\-.][0-9]+)*)")


            var lastIndex = 0

            while (urlMatcher.find()) {
                val urlStart = urlMatcher.start()
                val urlEnd = urlMatcher.end()

                append(text.substring(lastIndex, urlStart))

                val url = urlMatcher.group()
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                pop()

                lastIndex = urlEnd
            }

            var remainingText = text.substring(lastIndex)
            phoneRegex.findAll(remainingText).forEach { matchResult ->
                val start = matchResult.range.first
                val end = matchResult.range.last + 1

                append(remainingText.substring(lastIndex, start))

                val number = matchResult.value

                pushStringAnnotation(tag = "PHONE", annotation = number)
                withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(matchResult.value)
                }
                pop()

                lastIndex = end
            }

            remainingText = text.substring(lastIndex)
            hashtagRegex.findAll(remainingText).forEach { matchResult ->
                val start = matchResult.range.first
                val end = matchResult.range.last + 1

                append(remainingText.substring(lastIndex, start))

                val number = matchResult.value.removePrefix("#")

                pushStringAnnotation(tag = "HASHTAG", annotation = number)
                withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(matchResult.value)
                }
                pop()

                lastIndex = end
            }

            append(remainingText.substring(lastIndex.coerceAtMost(remainingText.length)))
        }

        ClickableText(
            text = annotatedString,
            style = options.contentStyle.copy(color = CodeTheme.colors.textMain),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        handler.invoke(Markup.RoomNumber(annotation.item.toLong()))
                    }

                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        handler.invoke(Markup.Url(annotation.item))
                    }

                annotatedString.getStringAnnotations(tag = "PHONE", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        handler.invoke(Markup.Phone(annotation.item))
                    }
            }
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