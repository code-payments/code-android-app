package com.flipcash.app.bill.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.getcode.opencode.model.ui.BillBackground
import com.flipcash.app.bill.customization.Event.Colors as ColorEvent
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import androidx.compose.foundation.clickable
import com.getcode.ui.utils.hexToColor
import com.getcode.ui.utils.hsv

@Composable
internal fun ColorOptionItem(
    colorOptions: List<BillBackground>,
    index: Int,
    dispatchEvent: (ColorEvent) -> Unit
) {
    val numRows = 2
    val itemsPerRow = (colorOptions.size + numRows - 1) / numRows
    val col = index / numRows
    val row = index % numRows
    val newIndex = if (row == 0) {
        col
    } else {
        itemsPerRow + col
    }
    if (newIndex < colorOptions.size) {
        val option = colorOptions[newIndex]
        Box(
            modifier = Modifier
                .width(CodeTheme.dimens.grid.x10)
                .presenceBorder()
                .addIf(option is BillBackground.Solid) {
                    Modifier.background(
                        color = hexToColor((option as BillBackground.Solid).colorHex),
                        shape = CodeTheme.shapes.small
                    )
                }
                .addIf(option is BillBackground.Gradient) {
                    val colors =
                        (option as BillBackground.Gradient).colors.map {
                            hexToColor(
                                it
                            )
                        }
                    Modifier.background(
                        brush = Brush.verticalGradient(
                            colors = colors,
                        ),
                        shape = CodeTheme.shapes.small
                    )
                }
                .clickable {
                    when (option) {
                        is BillBackground.Gradient -> dispatchEvent(
                            ColorEvent.LoadBackground(
                                option
                            )
                        )

                        is BillBackground.Solid -> dispatchEvent(
                            ColorEvent.CommitColorChange(
                                hexToColor(option.colorHex).hsv,
                            )
                        )
                    }
                }
        )
    }
}