package com.getcode.ui.utils

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.getcode.ui.core.addIf
import kotlin.math.roundToInt

sealed interface ConstraintMode {
    data object Free : ConstraintMode
    data class AutoSize(val minimum: TextStyle) : ConstraintMode
}

/**
 * Padding factor applied to the measurement width. Individual characters rendered
 * in a Row (e.g. AnimatedNumberText) sum wider than a single-string measurement
 * due to lack of kerning and per-character composable overhead. This buffer
 * prevents the auto-sizer from selecting a font size that causes the Row to
 * overflow, which would trigger font-size oscillation.
 */
internal const val MeasureWidthFraction = 0.95f

fun Modifier.constrain(
    mode: ConstraintMode,
    text: String,
    style: TextStyle,
    onTextSizeDetermined: (TextUnit) -> Unit
): Modifier = this.composed {
    val textMeasurer = rememberTextMeasurer()
    val autosizeTextMeasurer = remember(textMeasurer) { AutoSizeTextMeasurer(textMeasurer) }

    Modifier.addIf(mode is ConstraintMode.AutoSize) {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val result = autosizeTextMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                constraints = Constraints(
                    maxWidth = (constraints.maxWidth * MeasureWidthFraction).roundToInt(),
                    minHeight = 0
                ),
                minFontSize = (mode as ConstraintMode.AutoSize).minimum.fontSize,
                maxFontSize = style.fontSize,
                autosizeGranularity = 100
            )

            onTextSizeDetermined(result.layoutInput.style.fontSize)

            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}


fun Modifier.constrain(
    mode: ConstraintMode,
    state: TextFieldState,
    style: TextStyle,
    onTextSizeDetermined: (TextUnit) -> Unit
): Modifier = this.composed {
    val textMeasurer = rememberTextMeasurer()
    val autosizeTextMeasurer = remember(textMeasurer) { AutoSizeTextMeasurer(textMeasurer) }

    Modifier.addIf(mode is ConstraintMode.AutoSize) {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val result = autosizeTextMeasurer.measure(
                text = AnnotatedString(state.text.toString()),
                style = style,
                constraints = Constraints(
                    maxWidth = (constraints.maxWidth * MeasureWidthFraction).roundToInt(),
                    minHeight = 0
                ),
                minFontSize = (mode as ConstraintMode.AutoSize).minimum.fontSize,
                maxFontSize = style.fontSize,
                autosizeGranularity = 100
            )

            onTextSizeDetermined(result.layoutInput.style.fontSize)

            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}