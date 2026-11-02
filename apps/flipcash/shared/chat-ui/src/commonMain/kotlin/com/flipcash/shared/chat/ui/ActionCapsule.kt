package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.getcode.theme.CodeTheme

/**
 * A capsule holding a screen's icon buttons side by side, as iOS combines them: the photo viewer's
 * pill, on the quick-reaction strip's solid surface so it reads over a photo and over the app's
 * own background alike.
 */
@Composable
fun ActionCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(StripSurface),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One of [ActionCapsule]'s buttons. */
@Composable
fun ActionCapsuleButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String? = null,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(CodeTheme.dimens.staticGrid.x10)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}
