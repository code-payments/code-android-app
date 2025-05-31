package com.getcode.ui.components.text.animated

import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit

@Composable
internal fun NormalizedPrefixText(
    amountPrefix: String,
    textStyle: TextStyle,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val measurer = rememberTextMeasurer()

    // Measure amountPrefix
    val measureResult = measurer.measure(
        text = amountPrefix,
        style = textStyle.copy(fontSize = textSize, fontWeight = FontWeight.Bold)
    )

    // Measure USD symbol as reference
    val usdResult = measurer.measure(
        text = "$",
        style = textStyle.copy(fontSize = textSize, fontWeight = FontWeight.Bold)
    )

    // Get heights
    val prefixHeight = measureResult.size.height
    val usdHeight = usdResult.size.height

    // Use USD height if amountPrefix height is larger
    val targetHeight = minOf(prefixHeight, usdHeight)

    Layout(
        modifier = modifier,
        content = {
            // Actual amountPrefix with constrained height
            Text(
                text = amountPrefix,
                style = textStyle.copy(fontSize = textSize, fontWeight = FontWeight.Bold, color = Color.White),
                modifier = Modifier.height(with (LocalDensity.current) { targetHeight.toDp() })
            )
            // Rest of the content (digits, etc.)
            content()
        }
    ) { measurables, constraints ->
        // Measure the prefix
        val prefixPlaceable = measurables[0].measure(constraints)
        // Measure all content children (digits, commas, decimal points, etc.)
        val contentPlaceables = measurables.drop(1).map { it.measure(constraints) }

        // Calculate total width by summing all content widths
        val totalContentWidth = contentPlaceables.sumOf { it.width }
        val totalWidth = prefixPlaceable.width + totalContentWidth
        // Use the target height for the prefix, but allow content to dictate its own height
        val totalHeight = maxOf(targetHeight, contentPlaceables.maxOfOrNull { it.height } ?: 0)

        layout(totalWidth, totalHeight) {
            // Center the prefix vertically within the target height
            val prefixYOffset = (targetHeight - prefixPlaceable.height) / 2
            prefixPlaceable.placeRelative(0, prefixYOffset)

            // Place all content children sequentially
            var xOffset = prefixPlaceable.width
            contentPlaceables.forEach { placeable ->
                // Center each content child vertically within its own height
                val yOffset = (totalHeight - placeable.height) / 2
                placeable.placeRelative(xOffset, yOffset)
                xOffset += placeable.width
            }
        }
    }
}