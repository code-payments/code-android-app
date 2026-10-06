package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.getcode.theme.CodeTheme

private val ActionHeight = 38.dp
private val ActionIconSize = 22.dp
private const val PressedAlpha = 0.7f

/** A 38dp capsule with [text], for the actions that sit beside the avatar on a profile. */
@Composable
fun ProfileActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionCapsule(
        onClick = onClick,
        modifier = modifier.height(ActionHeight),
        contentPadding = CodeTheme.dimens.staticGrid.x4,
    ) {
        Text(
            text = text,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textMain,
        )
    }
}

/** The 38×38 icon form of [ProfileActionButton]. [contentDescription] is what a screen reader says. */
@Composable
fun ProfileActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionCapsule(
        onClick = onClick,
        modifier = modifier.size(ActionHeight),
        contentPadding = 0.dp,
    ) {
        Icon(
            modifier = Modifier.size(ActionIconSize),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CodeTheme.colors.textMain,
        )
    }
}

@Composable
private fun ActionCapsule(
    onClick: () -> Unit,
    modifier: Modifier,
    contentPadding: Dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .alpha(if (pressed) PressedAlpha else 1f)
            .clip(CircleShape)
            .background(CodeTheme.colors.action.copy(alpha = 0.1f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileActionButtons() {
    Row(horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x3)) {
        ProfileActionButton(text = "Edit Profile", onClick = {})
        ProfileActionButton(icon = Icons.Outlined.IosShare, contentDescription = "Share", onClick = {})
    }
}
