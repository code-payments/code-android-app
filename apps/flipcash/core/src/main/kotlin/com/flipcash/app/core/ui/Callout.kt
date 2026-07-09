package com.flipcash.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * Content-only callout: an icon, title, description, optional dismiss affordance, and
 * optional action button, with no container of its own. The caller supplies the surface
 * (background, padding, elevation) by wrapping this in whatever layout they need.
 *
 * Pass a [Shape] (see the sibling overload) to have the callout draw its own surface.
 */
@Composable
fun Callout(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
) {
    CalloutContent(
        title = title,
        description = description,
        modifier = modifier,
        icon = icon,
        actionLabel = actionLabel,
        onDismiss = onDismiss,
        onAction = onAction,
    )
}

/**
 * Surface-backed callout. Passing an explicit [shape] selects this overload, drawing the
 * callout inside a [containerColor] surface clipped to [shape] with standard inset padding.
 * Omit [shape] to fall back to the content-only overload and drive the container yourself.
 */
@Composable
fun Callout(
    title: String,
    description: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = CodeTheme.colors.surfaceVariant,
    icon: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
) {
    CalloutContent(
        title = title,
        description = description,
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .padding(CodeTheme.dimens.grid.x3),
        icon = icon,
        actionLabel = actionLabel,
        onDismiss = onDismiss,
        onAction = onAction,
    )
}

@Composable
private fun CalloutContent(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Box(modifier = Modifier.fillMaxHeight()) {
                    icon.invoke()
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            ) {
                Text(
                    text = title,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    text = description,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }
            if (onDismiss != null) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = CodeTheme.colors.textSecondary,
                    modifier = Modifier
                        .size(CodeTheme.dimens.staticGrid.x4)
                        .unboundedClickable(onClick = onDismiss),
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            CodeButton(
                text = actionLabel,
                modifier = Modifier.fillMaxWidth(),
                buttonState = ButtonState.Filled10,
                onClick = onAction,
            )
        }
    }
}
