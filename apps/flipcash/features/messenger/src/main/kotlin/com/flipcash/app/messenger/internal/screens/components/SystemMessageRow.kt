package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.chat.ui.transcriptBubbleMaxWidth
import com.getcode.theme.CodeTheme

/**
 * A server notice in the message column (node 11721:934, option E, file Jxf2wD9QyfKaONbhHSdU0K):
 * an info glyph in the avatar gutter, then the text where a bubble would start. No fill, no bubble
 * and no sender label.
 *
 * The same layout in every chat. In a group the glyph sits in the avatar's column and the text
 * lines up with the bubbles. A DM reserves no gutter for its bubbles, so there a notice starts
 * further in than they do.
 *
 * The text is drawn as it arrived: it wraps like a message, with no `maxLines` and no trimming.
 */
@Composable
internal fun SystemMessageRow(text: String, modifier: Modifier = Modifier) {
    val slot = CodeTheme.dimens.staticGrid.x6
    // Centres the first line on the glyph slot. Row stays top-aligned, so a second line wraps
    // below with the glyph still beside the first.
    val firstLineInset = with(LocalDensity.current) { (slot - CodeTheme.typography.caption.lineHeight.toDp()) / 2 }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SYSTEM_ROW_VERTICAL_PADDING)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
        // The gap the bubble column puts between the avatar and the bubble (MessageRow).
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Box(
            modifier = Modifier
                .requiredSize(slot)
                .border(CodeTheme.dimens.border, CodeTheme.colors.divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(SYSTEM_GLYPH_SIZE),
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        }
        // Measured like a bubble's column, so the cap is the member bubble's.
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            Text(
                modifier = Modifier
                    .widthIn(max = transcriptBubbleMaxWidth(maxWidth, CodeTheme.dimens.inset))
                    .padding(top = firstLineInset),
                text = text,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

private val SYSTEM_GLYPH_SIZE = 14.dp
private val SYSTEM_ROW_VERTICAL_PADDING = 6.dp

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_SystemMessageRow() {
    Column(modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset)) {
        SystemMessageRow("Alex joined the group")
        SystemMessageRow("Alex added Sam, Jordan and Riley to the group, and changed the group name to Weekend plans")
    }
}
