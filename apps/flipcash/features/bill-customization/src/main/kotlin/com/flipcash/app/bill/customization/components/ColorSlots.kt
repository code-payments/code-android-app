package com.flipcash.app.bill.customization.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.flipcash.app.bill.customization.ColorStore
import com.flipcash.app.bill.customization.Event
import com.flipcash.features.bill.playground.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.rememberedClickable

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ColorSlots(
    selectedSlot: Int,
    maxSlots: Int,
    selectedColors: List<ColorStore>,
    modifier: Modifier = Modifier,
    dispatchEvent: (Event) -> Unit,
) {
    Row(
        modifier = modifier,
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
                selectedColors.fastForEachIndexed { slot, store ->
                    val borderColor by animateColorAsState(
                        if (selectedSlot == slot) Color.White else Color.White.copy(0.30f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .animateBounds(this@LookaheadScope)
                            .presenceBorder(3.dp, borderColor)
                            .background(color = store.color, shape = CodeTheme.shapes.small)
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
}