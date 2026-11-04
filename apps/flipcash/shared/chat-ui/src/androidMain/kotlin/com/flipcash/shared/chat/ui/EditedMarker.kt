package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_edited
import com.getcode.theme.CodeTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The "Edited" label a bubble pins to its bottom-trailing corner, shared by the text bubble and a
 * photo's caption bubble so both draw the same label in the same place.
 *
 * The label is pinned rather than laid out after the text, so the body has to leave a hole of
 * exactly the label's width. [reserveSpace] does that with an empty placeholder in the text flow:
 * it sits on the last line where there is room and wraps onto its own line where there isn't,
 * without dragging the last word along with it. It draws nothing and holds no text, so selection
 * and the accessibility tree see only the real label drawn by [Label].
 */
@Immutable
internal class EditedMarker(
    val label: String,
    val style: TextStyle,
    private val reservation: TextUnit,
) {
    /** [body] with the label's hole appended. */
    fun reserveSpace(body: AnnotatedString): AnnotatedString = buildAnnotatedString {
        append(body)
        appendInlineContent(SLOT, " ")
    }

    /** The inline content [reserveSpace]'s placeholder needs, keyed by its slot. */
    fun inlineContent(): Pair<String, InlineTextContent> = SLOT to InlineTextContent(
        Placeholder(
            width = reservation,
            height = 1.sp,
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
        ),
    ) { }

    /** The label itself, in the bottom-trailing corner of the [BoxScope] the body sits in. */
    @Composable
    fun BoxScope.Label(endPadding: Dp = 0.dp) {
        Text(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endPadding),
            text = label,
            style = style,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

private const val SLOT = "edited-marker"
private val GAP = 6.dp

@Composable
internal fun rememberEditedMarker(): EditedMarker {
    val label = stringResource(Res.string.label_edited)
    val style = CodeTheme.typography.caption
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(label, style, density) {
        val reservation = with(density) {
            (measurer.measure(label, style).size.width.toDp() + GAP).toSp()
        }
        EditedMarker(label, style, reservation)
    }
}
