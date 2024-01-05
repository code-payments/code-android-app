package com.getcode.util

import androidx.compose.foundation.Indication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

inline fun Modifier.addIf(
    predicate: Boolean,
    crossinline whenTrue: () -> Modifier,
): Modifier = if (predicate) {
    this.then(whenTrue())
} else {
    this
}

fun Modifier.unboundedClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource? = null,
    rippleRadius: Dp = 24.dp,
    onClick: () -> Unit,
) = this.composed {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }

    rememberedClickable(
        onClick = onClick,
        enabled = enabled,
        role = role,
        interactionSource = interaction,
        indication = rememberRipple(bounded = false, radius = rippleRadius),
    )
}


fun Modifier.debugBounds(color: Color = Color.Magenta, shape: Shape = RectangleShape) = this.border(1.dp, color, shape)

fun Modifier.rememberedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
) = composed {
    val clicker = remember(enabled, onClickLabel, role, onClick) {
        Modifier.clickable(enabled, onClickLabel, role, onClick)
    }

    this.then(clicker)
}

fun Modifier.rememberedClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
) = composed {

    val clicker = remember(interactionSource, indication, enabled, onClickLabel, role, onClick) {
        Modifier.clickable(interactionSource, indication, enabled, onClickLabel, role, onClick)
    }
    this.then(clicker)
}


