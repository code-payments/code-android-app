package com.getcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme

/**
 * Node 11440:189 — a small filled dot that marks an icon as needing attention, ringed in the
 * background colour so it reads as cut out of the icon. No count.
 */
@Composable
fun NotificationDot(
    modifier: Modifier = Modifier,
    color: Color = CodeTheme.colors.success,
    ring: Color = CodeTheme.colors.background,
) {
    // The ring sits outside the fill: the design's 11dp dot plus a 2dp stroke.
    Box(
        modifier = modifier
            .background(ring, CircleShape)
            .padding(2.dp)
            .size(11.dp)
            .background(color, CircleShape),
    )
}

/**
 * Draws [content] with a [NotificationDot] on its top-end corner while [isShown]. The default
 * [offset] hangs the dot off a bare glyph's corner, as iOS does; a circular button, whose corner is
 * already empty, wants [DpOffset.Zero].
 */
@Composable
fun WithNotificationDot(
    isShown: Boolean,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(5.dp, (-5).dp),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (isShown) {
            NotificationDot(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = offset.x, y = offset.y),
            )
        }
    }
}
