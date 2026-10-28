package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import com.flipcash.shared.chat.models.SystemUserLink
import com.getcode.opencode.model.core.ID
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.getcode.theme.CodeTheme

/**
 * A server notice, centred in the transcript (node 11721:879, option D, file Jxf2wD9QyfKaONbhHSdU0K):
 * an info glyph, then the text. No fill, no bubble and no sender label. The glyph is what tells it
 * apart from the date separator, which is also centred caption text.
 *
 * The text is drawn as it arrived: it wraps at [SYSTEM_TEXT_MAX_WIDTH], centred, with no `maxLines`
 * and no trimming. The glyph stays beside the first line.
 */
@Composable
internal fun SystemMessageRow(
    text: String,
    modifier: Modifier = Modifier,
    userLinks: List<SystemUserLink> = emptyList(),
    onUserClick: (ID) -> Unit = {},
) {
    // The text is remembered across recompositions, so the click goes through the latest handler.
    val currentOnUserClick by rememberUpdatedState(onUserClick)
    val lineHeight = with(LocalDensity.current) { CodeTheme.typography.caption.lineHeight.toDp() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SYSTEM_ROW_VERTICAL_PADDING)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SYSTEM_GLYPH_GAP, Alignment.CenterHorizontally),
    ) {
        // One line tall, so the glyph centres on the first line however many the text wraps to.
        Box(modifier = Modifier.height(lineHeight), contentAlignment = Alignment.Center) {
            Icon(
                modifier = Modifier.size(SYSTEM_GLYPH_SIZE),
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        }
        Text(
            modifier = Modifier.widthIn(max = SYSTEM_TEXT_MAX_WIDTH),
            text = remember(text, userLinks) { linkedText(text, userLinks) { currentOnUserClick(it) } },
            // Centred in each 18sp line box, as iOS does with its baseline offset. The default
            // proportional split sits the glyphs high, off the glyph's centre.
            style = CodeTheme.typography.caption.copy(
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            ),
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * [text] with each of [userLinks] tappable, drawn heavier rather than recoloured: the row's grey is
 * the notice's voice, and a coloured name would read as an action.
 */
private fun linkedText(
    text: String,
    userLinks: List<SystemUserLink>,
    onUserClick: (ID) -> Unit,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    userLinks.sortedBy { it.start }.forEach { link ->
        if (link.start < cursor || link.end > text.length) return@forEach
        append(text, cursor, link.start)
        withLink(
            LinkAnnotation.Clickable(
                tag = "user",
                styles = TextLinkStyles(style = SpanStyle(fontWeight = FontWeight.SemiBold)),
            ) { onUserClick(link.userId) }
        ) { append(text, link.start, link.end) }
        cursor = link.end
    }
    append(text, cursor, text.length)
}

private val SYSTEM_GLYPH_SIZE = 14.dp
private val SYSTEM_GLYPH_GAP = 6.dp
private val SYSTEM_TEXT_MAX_WIDTH = 250.dp
private val SYSTEM_ROW_VERTICAL_PADDING = 6.dp

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_SystemMessageRow() {
    Column(modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset)) {
        SystemMessageRow("Alex joined the group")
        SystemMessageRow(
            text = "Alex joined with Jeff Yanta’s invite link",
            userLinks = listOf(SystemUserLink(start = 17, end = 27, userId = listOf(1))),
        )
        SystemMessageRow(
            text = "You joined with Jeff Yanta’s invite link",
            userLinks = listOf(SystemUserLink(start = 16, end = 26, userId = listOf(1))),
        )
        SystemMessageRow(
            text = "Alex joined with your invite link",
        )
        SystemMessageRow("Alex added Sam, Jordan and Riley to the group, and changed the group name to Weekend plans")
    }
}
