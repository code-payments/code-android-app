package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.theme.extraSmall
import com.getcode.ui.components.chat.MessageNodeDefaults

/**
 * What sits behind the blur of a group the viewer has not joined — node 10127:117171.
 *
 * Shapes, not messages. No RPC serves a transcript to a non-member: `GetChat` gives a group's
 * title, picture, roster summary and rules, and nothing else, so there is no conversation to fetch
 * and blur. What the design shows through the blur is therefore drawn rather than loaded — bars in
 * the proportions of an incoming run, so the gate reads as a conversation withheld rather than as a
 * screen that failed to load.
 *
 * Deliberately unlocalised and free of any text: the closer this got to real message content, the
 * more a screenshot with the blur stripped would look like one. Every value here is fixed, so it
 * also renders the same on every open.
 */
@Composable
internal fun GatedTranscriptPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = CodeTheme.dimens.grid.x3),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        // Stands in for the date separator that heads a day's messages.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = SeparatorWidth, height = LabelHeight)
                .clip(CodeTheme.shapes.extraSmall)
                .background(White05),
        )

        PlaceholderRow(widthFraction = 0.72f, bubbleHeight = ShortBubbleHeight)
        PlaceholderRow(widthFraction = 0.86f, bubbleHeight = TallBubbleHeight)
    }
}

/** One incoming row: the sender gutter, their name above the bubble, and the bubble. */
@Composable
private fun PlaceholderRow(widthFraction: Float, bubbleHeight: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        Box(
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape)
                .background(White10),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Box(
                modifier = Modifier
                    .padding(start = CodeTheme.dimens.grid.x1)
                    .size(width = NameWidth, height = LabelHeight)
                    .clip(CodeTheme.shapes.extraSmall)
                    .background(White05),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(bubbleHeight)
                    .clip(
                        MessageNodeDefaults.messageShape(
                            isIncoming = true,
                            isPreviousInGroup = false,
                            isNextInGroup = false,
                        )
                    )
                    .background(White10),
            )
        }
    }
}

private val SeparatorWidth = 96.dp
private val NameWidth = 72.dp
private val LabelHeight = 10.dp
private val ShortBubbleHeight = 40.dp
private val TallBubbleHeight = 64.dp
