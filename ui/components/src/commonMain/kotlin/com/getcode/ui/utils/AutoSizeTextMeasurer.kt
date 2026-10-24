package com.getcode.ui.utils

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

/** Credit to halilozercan. Modified to work with BTF2
 *
 * @see https://gist.github.com/halilozercan/d1468efa45c4e9a71907623e41c2ccb7
 */
class AutoSizeTextMeasurer(private val textMeasurer: TextMeasurer) {

    var textUnitArray: Array<TextUnit> = emptyArray()

    @Stable
    fun measure(
        text: AnnotatedString,
        style: TextStyle = TextStyle.Default, // fontSize will be ignored
        constraints: Constraints = Constraints(),
        minFontSize: TextUnit = 4.sp,
        maxFontSize: TextUnit = 144.sp,
        autosizeGranularity: Int = 100
    ): TextLayoutResult {
        val fontSize = findFontSize(
            text = text,
            style = style,
            constraints = constraints,
            minFontSize = minFontSize,
            maxFontSize = maxFontSize,
            autosizeGranularity = autosizeGranularity
        )

        return textMeasurer.measure(
            text = text,
            style = style.copy(fontSize = fontSize),
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            constraints = constraints
        )
    }

    fun findFontSize(
        text: AnnotatedString,
        style: TextStyle = TextStyle.Default, // fontSize will be ignored
        constraints: Constraints = Constraints(),
        minFontSize: TextUnit = 4.sp,
        maxFontSize: TextUnit = 144.sp,
        autosizeGranularity: Int = 100
    ): TextUnit {
        require(minFontSize.isSpecified && maxFontSize.isSpecified) {
            "min and max font sizes must be specified"
        }
        require((minFontSize.isSp && maxFontSize.isSp) || (minFontSize.isEm && maxFontSize.isEm)) {
            "min and max font sizes must be in the same format"
        }
        require(maxFontSize.value >= minFontSize.value) {
            "max value must be equal to or larger than min value"
        }
        require(autosizeGranularity >= 0) {
            "granularity must be a positive integer"
        }

        if (textUnitArray.size != 2 + autosizeGranularity ||
            textUnitArray.first() != minFontSize ||
            textUnitArray.last() != maxFontSize
        ) {
            val size = 2 + autosizeGranularity
            textUnitArray = Array(size) {
                when (it) {
                    0 -> minFontSize
                    size - 1 -> maxFontSize
                    else -> lerp(minFontSize, maxFontSize, (1f / (autosizeGranularity + 1)) * it)
                }
            }
        }

        val fontSize = binarySearchTextSize(array = textUnitArray) {
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = style.copy(fontSize = it),
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = Int.MAX_VALUE,
                constraints = constraints
            )
            !textLayoutResult.hasVisualOverflow
        }
        return textUnitArray[fontSize]
    }
}

private fun binarySearchTextSize(
    array: Array<TextUnit>,
    start: Int = 0,
    end: Int = array.size - 1,
    block: (TextUnit) -> Boolean
): Int {
    // Array contains a list of sorted text units (font sizes).
    // calling block tells whether given font size fits into layout.
    // If array is mapped using block, the result would look like
    // T T T T ... T T F F .. F
    // this functions returns the last T index

    // we are down to single index. if this is true, return this, else return -1
    if (start == end) {
        return if (block(array[start])) {
            start
        } else {
            (start - 1).coerceAtLeast(0)
        }
    }

    val mid = (start + end) / 2
    // if true, we need to go higher, else we go lower
    return if (block(array[mid])) {
        binarySearchTextSize(array, mid + 1, end, block)
    } else {
        binarySearchTextSize(array, start, mid, block)
    }
}