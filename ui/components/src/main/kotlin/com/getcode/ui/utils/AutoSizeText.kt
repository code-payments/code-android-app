package com.getcode.ui.utils

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.Ref
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
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

fun Modifier.constrain(
    mode: ConstraintMode,
    text: String,
    style: TextStyle,
    frameConstraints: Constraints,
    onTextSizeDetermined: (TextUnit) -> Unit
): Modifier = this.composed {
    val textMeasurer = rememberTextMeasurer()
    val autosizeTextMeasurer = remember(textMeasurer) { AutoSizeTextMeasurer(textMeasurer) }
    val textLayoutResult = remember { Ref<TextLayoutResult?>() }
    var flag by remember { mutableStateOf(Unit, neverEqualPolicy()) }

    Modifier.addIf(mode is ConstraintMode.AutoSize) {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val result = autosizeTextMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                constraints = Constraints(
                    maxWidth = (frameConstraints.maxWidth * 0.85f).roundToInt(),
                    minHeight = 0
                ),
                minFontSize = (mode as ConstraintMode.AutoSize).minimum.fontSize,
                maxFontSize = style.fontSize,
                autosizeGranularity = 100
            )

            textLayoutResult.value = result
            flag = Unit

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
    frameConstraints: Constraints,
    onTextSizeDetermined: (TextUnit) -> Unit
): Modifier = this.composed {
    val textMeasurer = rememberTextMeasurer()
    val autosizeTextMeasurer = remember(textMeasurer) { AutoSizeTextMeasurer(textMeasurer) }
    val textLayoutResult = remember { Ref<TextLayoutResult?>() }
    var flag by remember { mutableStateOf(Unit, neverEqualPolicy()) }

    Modifier.addIf(mode is ConstraintMode.AutoSize) {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val result = autosizeTextMeasurer.measure(
                text = AnnotatedString(state.text.toString()),
                style = style,
                constraints = Constraints(
                    maxWidth = (frameConstraints.maxWidth * 0.85f).roundToInt(),
                    minHeight = 0
                ),
                minFontSize = (mode as ConstraintMode.AutoSize).minimum.fontSize,
                maxFontSize = style.fontSize,
                autosizeGranularity = 100
            )

            textLayoutResult.value = result
            flag = Unit

            onTextSizeDetermined(result.layoutInput.style.fontSize)

            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}