package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.getcode.model.chat.MessageStatus
import com.getcode.theme.CodeTheme
import com.getcode.util.formatDateRelatively
import kotlinx.datetime.Instant

@Composable
fun MessageNodeScope.MessageText(
    modifier: Modifier = Modifier,
    content: String,
    isFromSelf: Boolean,
    date: Instant,
    status: MessageStatus = MessageStatus.Unknown,
    showStatus: Boolean = true,
) {
    val alignment = if (isFromSelf) Alignment.CenterEnd else Alignment.CenterStart

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        BoxWithConstraints(
            modifier = Modifier
                .sizeableWidth()
                .background(
                    color = color,
                    shape = MessageNodeDefaults.DefaultShape
                )
                .padding(CodeTheme.dimens.grid.x2)
        ) contents@{
            val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            Column(
                modifier = Modifier
                    .background(color)
                    // add top padding to accommodate ascents
                    .padding(top = CodeTheme.dimens.grid.x1),
            ) {
                MessageContent(
                    maxWidth = maxWidthPx,
                    message = content,
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = showStatus
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
private fun MessageContent(
    maxWidth: Int,
    message: String,
    date: Instant,
    status: MessageStatus,
    isFromSelf: Boolean,
    showStatus: Boolean = true
) {
    val contentStyle = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.W500)
    val alignmentRule by rememberAlignmentRule(
        contentTextStyle = contentStyle,
        maxWidth = maxWidth,
        message = message,
        date = date,
    )

    when (alignmentRule) {
        AlignmentRule.Column -> {
            Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
                Text(
                    text = message,
                    style = contentStyle,
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End),
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = showStatus,
                )
            }
        }

        AlignmentRule.ParagraphLastLine -> {
            Column(verticalArrangement = Arrangement.spacedBy(-(CodeTheme.dimens.grid.x2))) {
                Text(
                    text = message,
                    style = contentStyle,
                )
                DateWithStatus(
                    modifier = Modifier
                        .align(Alignment.End),
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = showStatus
                )
            }
        }

        AlignmentRule.SingleLineEnd -> {
            Row(horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)) {
                Text(
                    text = message,
                    style = contentStyle,
                )
                DateWithStatus(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x1),
                    date = date,
                    status = status,
                    isFromSelf = isFromSelf,
                    showStatus = showStatus
                )
            }
        }

        else -> Unit
    }
}

sealed interface AlignmentRule {
    data object ParagraphLastLine : AlignmentRule
    data object Column : AlignmentRule
    data object SingleLineEnd : AlignmentRule
}