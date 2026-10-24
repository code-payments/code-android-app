package com.getcode.ui.components.text.animated

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.getcode.ui.components.text.NumberInputHelper.Companion.DECIMAL_SEPARATOR
import com.getcode.ui.components.text.NumberInputHelper.Companion.GROUPING_SEPARATOR

@Composable
fun DigitContainer(
    textSize: TextUnit,
    density: Density,
    maxDigits: Int,
    firstDigit: String,
    digitVisibility: List<Boolean>,
    zeroVisibility: Boolean,
    placeholder: String,
    getComma: (Int) -> Boolean,
    getValue: (Int, Int) -> String?,
    getLastValue: (Int, Int) -> String?,
    decimalPointVisibility: Boolean,
    totalDecimals: Int,
    digitDecimalVisibility: List<Boolean>,
    digitDecimalZeroVisibility: List<Boolean>,
    decimalEnter: EnterTransition,
    decimalExit: ExitTransition,
    decimalZeroEnter: EnterTransition,
    decimalZeroExit: ExitTransition,
    zeroEnter: EnterTransition,
    zeroExit: ExitTransition,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            // Reference digit to determine height
            Text(
                text = "0",
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.requiredSize(0.dp) // Invisible, for measurement
            )
            // First digit
            AnimatedPlaceholderDigit(
                modifier = Modifier.fillMaxHeight(),
                text = firstDigit,
                digitVisible = digitVisibility[0],
                placeholder = placeholder,
                placeholderVisible = zeroVisibility,
                fontSize = textSize,
                density = density,
                placeholderEnter = zeroEnter,
                placeholderExit = zeroExit,
                placeholderColor = Color.White
            )
            // Other digits and commas
            for (i in 1 until maxDigits) {
                Digit(
                    modifier = Modifier.fillMaxHeight(),
                    isVisible = getComma(i),
                    text = GROUPING_SEPARATOR.toString(),
                    fontSize = textSize,
                    density = density,
                )
                Digit(
                    modifier = Modifier.fillMaxHeight(),
                    isVisible = digitVisibility[i],
                    text = getValue(0, i) ?: getLastValue(0, i) ?: "",
                    fontSize = textSize,
                    density = density
                )
            }
            // Decimal point
            Digit(
                modifier = Modifier.fillMaxHeight(),
                isVisible = decimalPointVisibility,
                text = DECIMAL_SEPARATOR.toString(),
                fontSize = textSize,
                density = density,
                enter = decimalEnter,
                exit = decimalExit,
            )
            // Decimal digits
            for (i in 0 until totalDecimals) {
                AnimatedPlaceholderDigit(
                    text = getValue(1, i) ?: getLastValue(1, i) ?: "0",
                    digitVisible = digitDecimalVisibility[i],
                    placeholderVisible = digitDecimalZeroVisibility[i],
                    placeholder = "0",
                    fontSize = textSize,
                    density = density,
                    placeholderEnter = decimalZeroEnter,
                    placeholderExit = decimalZeroExit,
                )
            }
        }
    ) { measurables, constraints ->
        val referenceDigitPlaceable = measurables[0].measure(constraints) // Invisible "0"
        val digitPlaceables = measurables.drop(1).map { it.measure(constraints) }

        // Use the reference digit's height to normalize positioning
        val digitHeight = referenceDigitPlaceable.height
        val totalWidth = digitPlaceables.sumOf { it.width }
        val totalHeight = digitPlaceables.maxOf { it.height }

        layout(totalWidth, totalHeight) {
            var xOffset = 0
            digitPlaceables.forEach { placeable ->
                val yOffset = (digitHeight - placeable.height) / 2 // Center within digit height
                placeable.placeRelative(xOffset, yOffset)
                xOffset += placeable.width
            }
        }
    }
}