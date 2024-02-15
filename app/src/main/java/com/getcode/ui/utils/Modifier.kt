package com.getcode.ui.utils

import android.view.MotionEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

inline fun Modifier.addIf(
    predicate: Boolean,
    crossinline whenTrue: @Composable () -> Modifier,
): Modifier = composed {
    if (predicate) {
        this.then(whenTrue())
    } else {
        this
    }
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


fun Modifier.debugBounds(color: Color = Color.Magenta, shape: Shape = RectangleShape) =
    this.border(1.dp, color, shape)

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

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rememberedLongClickable(
    enabled: Boolean = true,
    onLongClickLabel: String? = null,
    indication: Indication? = null,
    role: Role? = null,
    onLongClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val clicker = remember(enabled, onLongClickLabel, role, onLongClick) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            role = role,
            onClick = {}
        )
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

fun Modifier.measured(block: (DpSize) -> Unit): Modifier = composed {
    val density = LocalDensity.current
    onPlaced { block(it.size.toDp(density)) }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.swallowClicks(onClick: () -> Unit = { }): Modifier =
    this.pointerInteropFilter {
        if (it.actionMasked == MotionEvent.ACTION_UP) {
            onClick()
        }
        true
    }

/**
 * Draws circle with a solid [color] behind the content.
 *
 * @param color The color of the circle.
 * @param padding The padding to be applied externally to the circular shape. It determines the spacing between
 * the edge of the circle and the content inside.
 *
 * @return Combined [Modifier] that first draws the background circle and then centers the layout.
 */
fun Modifier.circleBackground(color: Color, padding: Dp): Modifier {
    val backgroundModifier = drawBehind {
        drawCircle(color, size.width / 2f, center = Offset(size.width / 2f, size.height / 2f))
    }

    val layoutModifier = layout { measurable, constraints ->
        // Adjust the constraints by the padding amount
        val adjustedConstraints = constraints.offset(-padding.roundToPx())

        // Measure the composable with the adjusted constraints
        val placeable = measurable.measure(adjustedConstraints)

        // Get the current max dimension to assign width=height
        val currentHeight = placeable.height
        val currentWidth = placeable.width
        val newDiameter = maxOf(currentHeight, currentWidth) + padding.roundToPx() * 2

        // Assign the dimension and the center position
        layout(newDiameter, newDiameter) {
            // Place the composable at the calculated position
            placeable.placeRelative(
                (newDiameter - currentWidth) / 2,
                (newDiameter - currentHeight) / 2
            )
        }
    }

    return this then backgroundModifier then layoutModifier
}

fun Modifier.punchRectangle(color: Color) = this.drawWithContent {
    drawRect(
        color,
        blendMode = BlendMode.Src
    )

    drawContent()
}

fun Modifier.punchCircle(color: Color) = this.drawWithContent {
    drawCircle(
        color,
        blendMode = BlendMode.Src
    )

    drawContent()
}

fun Modifier.drawWithGradient(
    color: Color,
    startY: ContentDrawScope.(Float) -> Float,
    endY: ContentDrawScope.(Float) -> Float = { Float.POSITIVE_INFINITY },
    blendMode: BlendMode = BlendMode.SrcOver
) = this.composed {
    var height by remember {
        mutableStateOf(0.dp)
    }

    val density = LocalDensity.current

    Modifier
        .onPlaced {
            height = with(density) { it.size.height.toDp() }
        }
        .graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
            val colors = listOf(Color.Transparent, color)
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    startY = startY(height.toPx()),
                    endY = endY(height.toPx()).takeIf { it != Float.POSITIVE_INFINITY } ?: height.toPx(),
                    colors = colors,
                ),
                blendMode = blendMode
            )
        }
}