package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.getcode.theme.CodeTheme

/**
 * A warning-tinted pill saying one thing about the viewer's relationship to the subject — Muted,
 * Blocked. Amber at a tenth strength rather than a theme token, because what it has to sit on is
 * whatever the host screen's background is; tinted rather than solid so it stays under the title.
 */
@Composable
fun ProfileStatusChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = CodeTheme.colors.warning.copy(alpha = 0.1f),
                shape = CircleShape,
            )
            .padding(
                horizontal = CodeTheme.dimens.staticGrid.x2,
                vertical = CodeTheme.dimens.staticGrid.x1,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
    ) {
        Icon(
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
            imageVector = icon,
            contentDescription = null,
            tint = CodeTheme.colors.warning,
        )
        Text(
            text = text,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.warning,
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileStatusChip() {
    Row(horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2)) {
        ProfileStatusChip(icon = Icons.Outlined.NotificationsOff, text = "Muted")
        ProfileStatusChip(icon = Icons.Outlined.Block, text = "Blocked")
    }
}
