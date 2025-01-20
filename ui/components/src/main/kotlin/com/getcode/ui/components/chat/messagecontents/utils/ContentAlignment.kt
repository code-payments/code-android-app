package com.getcode.ui.components.chat.messagecontents.utils

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.messagecontents.DateWithStatusDefaults
import com.getcode.util.formatDateRelatively
import kotlinx.datetime.Instant
import kotlin.math.max

internal sealed interface AlignmentRule {
    data object ParagraphLastLine : AlignmentRule
    data object Column : AlignmentRule
    data object SingleLineEnd : AlignmentRule
}

@Composable
internal fun rememberAlignmentRule(
    contentTextStyle: TextStyle,
    minWidth: Int = 0,
    maxWidth: Int,
    message: AnnotatedString,
    date: Instant,
    hasTips: Boolean = false,
    hasLink: Boolean = false,
): State<AlignmentRule?> {
    val density = LocalDensity.current
    val dateTextStyle = DateWithStatusDefaults.DateTextStyle
    val iconSizePx = with(density) { DateWithStatusDefaults.IconWidth.roundToPx() }
    val spacingPx = with(density) { DateWithStatusDefaults.Spacing.roundToPx() }
    val contentPaddingPx = with(density) { CodeTheme.dimens.grid.x2.roundToPx() }

    return remember(minWidth, maxWidth, message, date, hasLink) {
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
                        lineContentWidth + bufferSize > maxWidth || hasTips || hasLink -> AlignmentRule.Column
                        textLayoutResult.lineCount == 1 -> AlignmentRule.SingleLineEnd
                        else -> AlignmentRule.ParagraphLastLine
                    }
                }
            )
        }
    }
}