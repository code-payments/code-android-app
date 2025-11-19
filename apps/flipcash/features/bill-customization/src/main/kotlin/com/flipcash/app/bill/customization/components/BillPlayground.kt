package com.flipcash.app.bill.customization.components

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bill.customization.Event
import com.flipcash.app.bill.customization.PlaygroundMode
import com.flipcash.app.bill.customization.PlaygroundState
import com.flipcash.app.bill.customization.internal.InternalBillPlaygroundController
import com.flipcash.app.theme.FlipcashDesignSystem
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Pill

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun BillPlayground(
    state: PlaygroundState,
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
        ColorSlots(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.grid.x3),
            selectedSlot = state.backgroundState.selectedSlot,
            maxSlots = state.backgroundState.maxSlots,
            selectedColors = state.backgroundState.selectedColors,
            dispatchEvent = dispatchEvent
        )

        val selectedSlotStore by rememberUpdatedState(state.backgroundState.selectedColors[state.backgroundState.selectedSlot])

        // color options
        AnimatedContent(
            targetState = state.backgroundState.mode,
            transitionSpec = {
                if (targetState == PlaygroundMode.ColorPanel) {
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
                PlaygroundMode.ColorPanel -> {
                    ColorPanel(
                        hsv = selectedSlotStore.hsv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CodeTheme.dimens.grid.x14 * 2)
                            .padding(horizontal = CodeTheme.dimens.grid.x5)
                            .padding(vertical = CodeTheme.dimens.grid.x3),
                        onChange = { color, isDragging ->
                            if (isDragging) {
                                dispatchEvent(Event.PreviewColorChange(color))
                            } else {
                                dispatchEvent(Event.CommitColorChange(color))

                            }
                        },
                        onClose = {
                            dispatchEvent(Event.CloseHueControls)
                        }
                    )
                }

                PlaygroundMode.Presets -> {
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
                                dispatchEvent(Event.OpenHueControls)
                            }
                        }

                        items(state.backgroundState.colorOptions.size) { index ->
                            ColorOptionItem(state.backgroundState.colorOptions, index) { event ->
                                dispatchEvent(event)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Modifier.presenceBorder(
    width: Dp = 2.dp,
    color: Color = Color.White.copy(0.30f),
    shape: Shape = CodeTheme.shapes.small
): Modifier = this.border(
    width = width,
    color = color,
    shape = shape
)

@Composable
@Preview
private fun PreviewCustomizationControls() {
    FlipcashDesignSystem {
        val clipboardManager = LocalContext.current.getSystemService(ClipboardManager::class.java)
        val controller = remember {
            InternalBillPlaygroundController(clipboardManager)
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
                        brush = state.backgroundBrush,
                        shape = CodeTheme.shapes.large,
                    )
                    .presenceBorder(),
            )
            BillPlayground(
                state = state,
            ) { controller.dispatchEvent(it) }
        }
    }
}