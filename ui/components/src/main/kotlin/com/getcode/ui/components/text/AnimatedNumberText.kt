package com.getcode.ui.components.text

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.getcode.ui.utils.AutoSizeTextMeasurer
import com.getcode.ui.utils.ConstraintMode
import com.getcode.ui.utils.MeasureWidthFraction
import kotlin.math.roundToInt

@Composable
fun AnimatedNumberText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    constraintMode: ConstraintMode = ConstraintMode.Free,
) {
    BoxWithConstraints(modifier = modifier) {
        val textSize = if (constraintMode is ConstraintMode.AutoSize) {
            val textMeasurer = rememberTextMeasurer()
            val autosizeTextMeasurer = remember(textMeasurer) {
                AutoSizeTextMeasurer(textMeasurer)
            }
            val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }

            remember(value, style.fontSize, maxWidthPx) {
                autosizeTextMeasurer.findFontSize(
                    text = AnnotatedString(value),
                    style = style,
                    constraints = Constraints(
                        maxWidth = (maxWidthPx * MeasureWidthFraction).roundToInt(),
                        minHeight = 0
                    ),
                    minFontSize = constraintMode.minimum.fontSize,
                    maxFontSize = style.fontSize,
                    autosizeGranularity = 100
                )
            }
        } else {
            style.fontSize
        }

        val resolvedStyle = style.copy(fontSize = textSize)
        Row {
            value.forEach { char ->
                AnimatedDigit(
                    char = char,
                    style = resolvedStyle,
                    color = color,
                )
            }
        }
    }
}

/**
 * One character of [AnimatedNumberText]: a digit that rolls to its replacement, anything else plain.
 *
 * The roller is only mounted once the digit has actually changed. `AnimatedContent` costs several
 * times the `Text` it wraps to compose, and a screen that shows many numbers at once -- the wallet's
 * card deck -- pays that for every character of every number before the first frame, to animate
 * nothing. A number that never moves now costs a plain [Text].
 *
 * The first change still animates: [rolling] flips a frame ahead of [shown], so the roller mounts
 * seeded with the digit already on screen and has something to animate to.
 */
@Composable
private fun AnimatedDigit(
    char: Char,
    style: TextStyle,
    color: Color,
) {
    if (!char.isDigit()) {
        Text(
            text = char.toString(),
            style = style,
            color = color,
        )
        return
    }

    var rolling by remember { mutableStateOf(false) }
    // What the roller is showing. Held one composition behind [char] on the change that mounts the
    // roller, so that mount is seeded with the outgoing digit rather than the incoming one.
    var shown by remember { mutableStateOf(char) }

    LaunchedEffect(char) {
        if (char != shown) rolling = true
    }
    // Restarts on the composition that observes [rolling], which is the one that mounted the roller.
    LaunchedEffect(rolling, char) {
        if (!rolling) return@LaunchedEffect
        // Give the roller a frame of its own before moving it. Effects run inside the frame that
        // composed them, so writing [shown] straight away would settle the mount and the move in one
        // pass and the digit would snap instead of rolling.
        if (shown != char) withFrameNanos { }
        shown = char
    }

    if (!rolling) {
        Text(
            text = shown.toString(),
            style = style,
            color = color,
        )
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val height = remember(style) {
        textMeasurer.measure("0", style).size.height
    }

    Box(
        modifier = Modifier
            .height(with(LocalDensity.current) { height.toDp() })
    ) {
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                if (targetState != initialState) {
                    val rising = (targetState.digitToIntOrNull() ?: 0) >
                        (initialState.digitToIntOrNull() ?: 0)

                    // Enter: slide in from bottom (increasing) or top (decreasing) + fade
                    val enter = slideInVertically { height ->
                        if (rising) height else -height
                    } + fadeIn()

                    // Exit: just fade/blur in place - no slide
                    val exit = fadeOut()

                    enter togetherWith exit using SizeTransform()
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "digit"
        ) { char ->
            // Apply blur during exit transition
            val blurRadius by transition.animateFloat(
                transitionSpec = { tween(150) },
                label = "blur"
            ) { state ->
                when (state) {
                    EnterExitState.PreEnter -> 12f
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> 12f
                }
            }

            Text(
                modifier = Modifier.blur(blurRadius.dp),
                text = char.toString(),
                style = style,
                color = color,
            )
        }
    }
}
