package com.flipcash.app.bill.customization.features

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.flipcash.app.bill.customization.components.ColorOptionItem
import com.flipcash.app.bill.customization.components.ColorPanel
import com.flipcash.app.bill.customization.components.ColorSlots
import com.flipcash.app.bill.customization.components.HueControlButton
import com.flipcash.app.bill.customization.internal.features.ColorState
import com.flipcash.app.bill.customization.models.ColorPlaygroundMode
import com.getcode.theme.CodeTheme
import com.flipcash.app.bill.customization.Event.Colors as ColorEvent

@Composable
internal fun BackgroundControls(
    state: ColorState,
    onShowingPaste: () -> Unit,
    onPasteConfiguration: () -> Unit,
    dispatchEvent: (ColorEvent) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // color selections
        ColorSlots(
            modifier = Modifier
                .fillMaxWidth(),
            selectedSlot = state.selectedSlot,
            maxSlots = state.maxSlots,
            selectedColors = state.selectedColors,
            dispatchEvent = dispatchEvent,
            onShowingPaste = onShowingPaste,
            onPasteConfiguration = onPasteConfiguration,
        )

        val selectedSlotStore by rememberUpdatedState(state.selectedColors[state.selectedSlot])

        // color options
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = {
                if (targetState == ColorPlaygroundMode.ColorPanel) {
                    // ColorPanel is entering, slide in from the left
                    slideInHorizontally(
                        spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold,
                        )
                    ) { width -> -width } togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                } else { // targetState == PlaygroundMode.Presets
                    // ColorPanel is exiting, slide out to the left
                    slideInHorizontally(
                        spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntOffset.VisibilityThreshold,
                        ),
                    ) { width -> width } togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                }
            }
        ) { mode ->
            when (mode) {
                ColorPlaygroundMode.ColorPanel -> {
                    ColorPanel(
                        hsv = selectedSlotStore.hsv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CodeTheme.dimens.grid.x14 * 2)
                            .padding(horizontal = CodeTheme.dimens.grid.x5)
                            .padding(vertical = CodeTheme.dimens.grid.x3),
                        onChange = { color, isDragging ->
                            if (isDragging) {
                                dispatchEvent(ColorEvent.PreviewColorChange(color))
                            } else {
                                dispatchEvent(ColorEvent.CommitColorChange(color))

                            }
                        },
                        onClose = {
                            dispatchEvent(ColorEvent.CloseHueControls)
                        }
                    )
                }

                ColorPlaygroundMode.Presets -> {
                    LazyHorizontalGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CodeTheme.dimens.grid.x14 * 2)
                            .padding(vertical = CodeTheme.dimens.grid.x3),
                        rows = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                        contentPadding = PaddingValues(horizontal = CodeTheme.dimens.grid.x5)
                    ) {
                        item(
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            HueControlButton {
                                dispatchEvent(ColorEvent.OpenHueControls)
                            }
                        }

                        items(state.colorOptions.size) { index ->
                            ColorOptionItem(state.colorOptions, index) { event ->
                                dispatchEvent(event)
                            }
                        }
                    }
                }
            }
        }
    }
}