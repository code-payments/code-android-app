package com.flipcash.app.bill.customization.features

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.ScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.bill.customization.components.AlphaSlider
import com.flipcash.app.bill.customization.components.presenceBorder
import com.flipcash.app.bill.customization.internal.features.BlendMode
import com.flipcash.app.bill.customization.internal.features.GraphicState
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Pill
import com.getcode.ui.core.rememberedClickable
import com.flipcash.app.bill.customization.Event.Graphics as GraphicsEvent

@Composable
internal fun TextureControls(
    state: GraphicState,
    dispatchEvent: (GraphicsEvent) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyHorizontalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .height(CodeTheme.dimens.grid.x13)
                .padding(top = CodeTheme.dimens.grid.x3),
            rows = GridCells.Fixed(1),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            contentPadding = PaddingValues(horizontal = CodeTheme.dimens.grid.x5)
        ) {
            itemsIndexed(state.options) { index, resource ->
                val isSelected = state.selectedOption == index
                val alpha by animateFloatAsState(
                    if (isSelected) 1f else 0.5f
                )

                val borderColor by animateColorAsState(
                    if (isSelected) Color.White else Color.White.copy(0.6f)
                )

                Box(
                    modifier = Modifier
                        .requiredSize(CodeTheme.dimens.grid.x13)
                        .alpha(alpha)
                        .presenceBorder(
                            width = CodeTheme.dimens.border,
                            color = borderColor,
                            shape = CodeTheme.shapes.large
                        )
                        .clip(CodeTheme.shapes.large)
                        .rememberedClickable {
                            dispatchEvent(GraphicsEvent.ApplyGraphic(index))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(resource),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }

        val blendModes = remember(state.blendModes) {
            state.blendModes.map { it.blendMode }
        }

        val selectedTabIndex by remember(state.selectedBlendMode) {
            derivedStateOf {
                blendModes.indexOf(state.selectedBlendMode.blendMode).takeIf { it >= 0 } ?: 0
            }
        }

        ScrollableTabRow(
            modifier = Modifier.fillMaxWidth().padding(top = CodeTheme.dimens.grid.x4),
            selectedTabIndex = selectedTabIndex,
            edgePadding = CodeTheme.dimens.grid.x3,
            backgroundColor = Color.Transparent,
            indicator = {}, // no indicator
            divider = {} // no divider
        ) {
            blendModes.forEachIndexed { index, mode ->
                BlendModeTab(
                    mode = mode,
                    isSelected = index == selectedTabIndex,
                    onClick = { dispatchEvent(GraphicsEvent.CommitBlendMode(mode)) },
                )
            }
        }

        AlphaSlider(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.grid.x8)
                .padding(top = CodeTheme.dimens.grid.x3),
            opacity = state.selectedBlendMode.strength,
            onOpacityChange = { opacity, dragging ->
                if (dragging) {
                    dispatchEvent(GraphicsEvent.PreviewBlendMode(opacity))
                } else {
                    dispatchEvent(
                        GraphicsEvent.CommitBlendMode(
                            state.selectedBlendMode.blendMode,
                            opacity
                        )
                    )
                }
            }
        )
    }
}

@Composable
internal fun BlendModeTab(
    mode: BlendMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) Color.White.copy(0.30f) else Color.Transparent
    )

    val alpha by animateFloatAsState(
        if (isSelected) 1f else 0.6f
    )

    Pill(
        modifier = modifier
            .alpha(alpha)
            .rememberedClickable(onClick = onClick),
        backgroundColor = backgroundColor,
        contentColor = Color.White,
        text = stringResource(mode.labelRes),
        textStyle = CodeTheme.typography.textMedium,
        shape = CodeTheme.shapes.medium,
    )
}