package com.flipcash.app.bill.customization.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.internal.InternalBillPlaygroundController
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.bill.playground.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.model.financial.BillBackground
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Pill
import com.getcode.ui.core.addIf
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.utils.hexToColor

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun BillPlayground(
    selectedSlot: Int,
    maxSlots: Int,
    colorOptions: List<BillBackground>,
    selectedColors: List<Color>,
    dispatchEvent: (Event) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CodeTheme.dimens.grid.x3)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Pill(
            backgroundColor = Color.White,
            contentColor = Color.Black,
            text = "Background",
            textStyle = CodeTheme.typography.textMedium,
            shape = CodeTheme.shapes.medium,
        )

        // color selections
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.grid.x3),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // remove slot
            IconButton(
                enabled = selectedColors.count() > 1,
                onClick = {
                    dispatchEvent(Event.RemoveSlot)
                }
            ) {
                val alpha by animateFloatAsState(
                    if (selectedColors.count() > 1) 1f else ContentAlpha.disabled
                )
                Icon(
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                    painter = painterResource(R.drawable.ic_minus),
                    contentDescription = "Remove Color Slot",
                    tint = Color.White.copy(alpha),
                )
            }

            // slots
            LookaheadScope {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(CodeTheme.dimens.grid.x10),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    selectedColors.fastForEachIndexed { slot, color ->
                        val borderColor by animateColorAsState(
                            if (selectedSlot == slot) Color.White else Color.White.copy(0.30f)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .animateBounds(this@LookaheadScope)
                                .presenceBorder(3.dp, borderColor)
                                .background(color = color, shape = CodeTheme.shapes.small)
                                .rememberedClickable {
                                    dispatchEvent(Event.SelectSlot(slot))
                                }
                        )
                    }
                }
            }
            // add slot
            IconButton(
                enabled = selectedColors.count() < maxSlots,
                onClick = {
                    dispatchEvent(Event.AddSlot)
                }
            ) {
                val alpha by animateFloatAsState(
                    if (selectedColors.count() < maxSlots) 1f else ContentAlpha.disabled
                )
                Icon(
                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = "Add Color Slot",
                    tint = Color.White.copy(alpha),
                )
            }
        }

        // color bar
        LazyHorizontalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            rows = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            contentPadding = PaddingValues(horizontal = CodeTheme.dimens.grid.x5)
        ) {
            item(
                span = { GridItemSpan(maxLineSpan) }
            ) {
                // hue control trigger
                Box(
                    modifier = Modifier
                        .width(50.dp) // Set a fixed width matching other items
                        .fillMaxHeight() // Fill the available height of the grid
                        .rainbowBackground()
                        .padding(CodeTheme.dimens.thickBorder)
                        .background(
                            color = Color.Black.copy(0.50f),
                            shape = CodeTheme.shapes.small
                        )
                        .padding(CodeTheme.dimens.grid.x3),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_color_tune_hsl),
                        contentDescription = "Color manipulation",
                        tint = Color.White
                    )
                }
            }

            items(colorOptions.size) { index ->
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
                            .size(50.dp)
                            .presenceBorder()
                            .addIf(option is BillBackground.Solid) {
                                Modifier.background(
                                    color = hexToColor((option as BillBackground.Solid).colorHex),
                                    shape = CodeTheme.shapes.small
                                )
                            }
                            .addIf(option is BillBackground.Gradient) {
                                val colors =
                                    (option as BillBackground.Gradient).colors.map { hexToColor(it) }
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
                                        Event.ChangeColor(
                                            hexToColor(option.colorHex)
                                        )
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}

private fun Modifier.presenceBorder(
    width: Dp = 2.dp,
    color: Color = Color.White.copy(0.30f)
): Modifier = this.composed {
    Modifier.border(
        width = width,
        color = color,
        shape = CodeTheme.shapes.small
    )
}

private fun Modifier.rainbowBackground(): Modifier = composed {
    val small = CodeTheme.shapes.small
    val thickBorder = CodeTheme.dimens.thickBorder
    val density = LocalDensity.current
    this.drawWithContent {
        drawRoundRect(
            brush = Brush.sweepGradient(
                colorStops = arrayOf( // Starts at 3 o'clock
                    0f to Color(0xFFBB3DFF),     // Purple starts at 3 o'clock (right)
                    0.16f to Color(0xFFFF3D3D),  // Dark red
                    0.22f to Color(0xFFFF7070),  // Light red
                    0.38f to Color(0xFFFFC23D),  // Yellow
                    0.58f to Color(0xFF54FF3D),  // Green
                    0.81f to Color(0xFF3DEFFF),  // Cyan
                    0.92f to Color(0xFF3DA8FF),  // Blue
                    1f to Color(0xFFBB3DFF)      // Loops back to purple
                ),
                center = center
            ),
            cornerRadius = CornerRadius(
                small.topStart.toPx(size, density),
                small.topEnd.toPx(size, density)
            ),
        )
        drawRoundRect(
            brush = SolidColor(Color.Black.copy(0.50f)),
            cornerRadius = CornerRadius(
                small.topStart.toPx(size, density),
                small.topEnd.toPx(size, density)
            ),
            topLeft = Offset(
                x = thickBorder.toPx(),
                y = thickBorder.toPx()
            ),
            size = size.copy(
                width = size.width - thickBorder.toPx() * 2,
                height = size.height - thickBorder.toPx() * 2
            )
        )
        drawContent()
    }
}

private val usdToCadRate = Rate(fx = 1.37161, currency = CurrencyCode.CAD)
private val cadToUsdRate = Rate(fx = 0.72894, currency = CurrencyCode.USD)
private val rates = mapOf(
    CurrencyCode.CAD to usdToCadRate,
    CurrencyCode.USD to cadToUsdRate,
)

@Composable
@Preview
private fun PreviewCustomizationControls() {
    FlipcashDesignSystem {
        val context = LocalContext.current
        val controller = remember {
            InternalBillPlaygroundController(
                ExchangeStub(
                    providedRates = rates,
                    context = context,
                )
            )
        }
        val state by controller.state.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(CodeTheme.dimens.grid.x10)
                    .aspectRatio(0.555f)
                    .weight(1f)
                    .background(
                        brush = state.brush,
                        shape = CodeTheme.shapes.large,
                    )
                    .presenceBorder(),
            )
            BillPlayground(
                selectedSlot = state.selectedSlot,
                maxSlots = state.maxSlots,
                selectedColors = state.selectedColors,
                colorOptions = state.colorOptions,
            ) { controller.dispatchEvent(it) }
        }
    }
}