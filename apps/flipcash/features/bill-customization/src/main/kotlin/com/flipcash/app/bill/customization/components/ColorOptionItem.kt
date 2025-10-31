package com.flipcash.app.bill.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.flipcash.app.bill.customization.Event
import com.getcode.opencode.model.financial.BillBackground
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.utils.hexToColor

@Composable
internal fun ColorOptionItem(
    colorOptions: List<BillBackground>,
    index: Int,
    dispatchEvent: (Event) -> Unit
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
                .rememberedClickable {
                    when (option) {
                        is BillBackground.Gradient -> dispatchEvent(
                            Event.LoadBackground(
                                option
                            )
                        )

                        is BillBackground.Solid -> dispatchEvent(
                            Event.CommitColorChange(
                                hexToColor(option.colorHex),
                            )
                        )
                    }
                }
        )
    }
}