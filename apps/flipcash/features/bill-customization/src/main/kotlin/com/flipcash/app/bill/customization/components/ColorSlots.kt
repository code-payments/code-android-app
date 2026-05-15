package com.flipcash.app.bill.customization.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.ExperimentalAnimatableApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.bill.customization.Event.Colors as ColorEvent
import com.flipcash.app.bill.customization.models.ColorStore
import com.flipcash.features.bill.playground.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.rememberedLongClickable

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimatableApi::class)
@Composable
internal fun ColorSlots(
    selectedSlot: Int,
    maxSlots: Int,
    selectedColors: List<ColorStore>,
    modifier: Modifier = Modifier,
    onShowingPaste: () -> Unit,
    onPasteConfiguration: () -> Unit,
    dispatchEvent: (ColorEvent) -> Unit,
) {
    val toolbar = LocalTextToolbar.current
    var contentRect by remember { mutableStateOf(Rect.Zero) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // remove slot
        IconButton(
            enabled = selectedColors.count() > 1,
            onClick = {
                dispatchEvent(ColorEvent.RemoveSlot)
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
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(CodeTheme.dimens.grid.x10)
                .onPlaced { contentRect = it.boundsInWindow() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CodeTheme.dimens.grid.x10),
                horizontalArrangement = Arrangement.spacedBy(
                    CodeTheme.dimens.grid.x1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val slotSize = (this@BoxWithConstraints.maxWidth - (CodeTheme.dimens.grid.x1 * (selectedColors.count() - 1))) / selectedColors.count()

                repeat(maxSlots) { slot ->
                    val store = selectedColors.getOrNull(slot)

                    AnimatedContent(
                        targetState = store,
                        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None }
                    ) { s ->
                        val animatedWidth by animateDpAsState(
                            if (s != null) slotSize else 0.dp,
                            animationSpec = SlotTransitionAnimationSpec
                        )

                        ColorSlot(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(animatedWidth),
                            store = s,
                            selected = selectedSlot == slot,
                            onLongClick = {
                                toolbar.showMenu(
                                    rect = contentRect,
                                    onPasteRequested = {
                                        onPasteConfiguration()
                                        toolbar.hide()
                                    }
                                )
                                onShowingPaste()
                            }
                        ) {
                            dispatchEvent(ColorEvent.SelectSlot(slot))
                        }
                    }
                }
            }
        }

        // add slot
        IconButton(
            enabled = selectedColors.count() < maxSlots,
            onClick = {
                dispatchEvent(ColorEvent.AddSlot)
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
}

@Composable
private fun ColorSlot(
    store: ColorStore?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {

    val borderColor by animateColorAsState(
        if (selected) Color.White else Color.White.copy(0.22f),
        animationSpec = SelectedSlotAnimationSpec,
        label = "slot border color"
    )

    val borderWidth by animateDpAsState(
        if (selected) 3.dp else CodeTheme.dimens.border,
        animationSpec = SlotTransitionAnimationSpec,
        label = "slot border width"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .presenceBorder(borderWidth, borderColor)
            .background(color = store?.color ?: Color.Transparent, shape = CodeTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    )
}

private val SlotTransitionAnimationSpec = spring<Dp>(
    dampingRatio = 0.9f,
    stiffness = 450f,
)

private val SelectedSlotAnimationSpec = spring<Color>(
    dampingRatio = 0.9f,
    stiffness = 450f,
)