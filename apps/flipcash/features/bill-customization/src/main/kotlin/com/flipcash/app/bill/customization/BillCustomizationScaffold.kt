package com.flipcash.app.bill.customization

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bill.customization.components.BillPlayground
import com.flipcash.app.bills.AnimatedBill
import com.flipcash.app.core.bill.Bill
import com.flipcash.features.bill.playground.R
import com.getcode.opencode.model.financial.BillBackground
import com.getcode.opencode.model.financial.TokenBillCustomizations
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.core.measured
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.unboundedClickable
import com.getcode.ui.utils.AnimationUtils
import com.getcode.ui.utils.toAGColor

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BillPlaygroundScaffold(content: @Composable () -> Unit) {
    val controller = LocalBillPlaygroundController.current

    val state by controller.state.collectAsStateWithLifecycle()

    // bill dismiss state, restarted for every bill
    val billDismissState = remember(state.bill) {
        DismissState(
            initialValue = DismissValue.Default,
            confirmStateChange = { false }
        )
    }

    val customizationsOptions by remember(
        state.selectedColors,
        state.bill?.token?.billCustomizations
    ) {
        derivedStateOf {
            val background = if (state.selectedColors.count() > 1) {
                BillBackground.Gradient.from(
                    state.selectedColors.map { it.color.toAGColor() }
                )
            } else {
                BillBackground.Solid.from(state.selectedColors.first().color.toAGColor())
            }


            return@derivedStateOf TokenBillCustomizations(
                background = background,
                icon = null,
            )
        }
    }

    val augmentedBill by remember(state.bill, customizationsOptions) {
        derivedStateOf {
            val bill = state.bill ?: return@derivedStateOf null
            if (bill !is Bill.Cash) return@derivedStateOf null
            bill.copy(
                token = bill.token.copy(
                    billCustomizations = customizationsOptions
                )
            )
        }
    }

    var topBarHeight by remember {
        mutableStateOf(0.dp)
    }

    var playgroundHeight by remember {
        mutableStateOf(0.dp)
    }

    BackHandler(state.isCustomizing) {
        controller.cancel()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        AnimatedBill(
            modifier = Modifier.fillMaxSize(),
            dismissState = billDismissState,
            dismissed = !state.isCustomizing,
            contentPadding = PaddingValues(
                top = topBarHeight,
                bottom = playgroundHeight,
            ),
            bill = augmentedBill,
            transitionSpec = {
                AnimationUtils.animationBillEnterGrabbed
                    .togetherWith(AnimationUtils.animationBillExitGrabbed)
            },
            contentKey = { it?.data }
        )

        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth()
                .measured { topBarHeight = it.height },
            visible = state.isCustomizing,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TopBar(
                modifier = Modifier.fillMaxWidth(),
                canUndo = controller.canUndo,
                canCopy = controller.canCopy,
                onUndo = { controller.dispatchEvent(Event.Undo) },
                onCopy = { controller.dispatchEvent(Event.Copy) },
                onBack = { controller.cancel() },
                onDone = { controller.cancel() },
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .measured { playgroundHeight = it.height },
            visible = state.isCustomizing,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            label = "bill customization",
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                BillPlayground(
                    mode = state.mode,
                    selectedColors = state.selectedColors,
                    selectedSlot = state.selectedSlot,
                    maxSlots = state.maxSlots,
                    colorOptions = state.colorOptions,
                ) { event ->
                    controller.dispatchEvent(event)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    canUndo: Boolean,
    canCopy: Boolean,
    onUndo: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(top = CodeTheme.dimens.grid.x2)
            .padding(horizontal = CodeTheme.dimens.grid.x2)
            .statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppBarDefaults.UpNavigation { onBack() }

        Row(
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val undoAlpha by animateFloatAsState(
                targetValue = if (canUndo) 1f else ContentAlpha.disabled,
            )
            Image(
                modifier = Modifier
                    .background(Color.Black.copy(0.19f), CircleShape)
                    .clip(CircleShape)
                    .rememberedClickable(enabled = canUndo) { onUndo() }
                    .padding(2.dp),
                painter = painterResource(R.drawable.ic_undo),
                colorFilter = ColorFilter.tint(Color.White.copy(undoAlpha)),
                contentDescription = null
            )

            Text(
                modifier = Modifier
                    .background(Color.Black.copy(0.19f), CircleShape)
                    .clip(CircleShape)
                    .rememberedClickable { onCopy() }
                    .padding(
                        horizontal = CodeTheme.dimens.grid.x2,
                        vertical = CodeTheme.dimens.grid.x1
                    ),
                text = "Copy",
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                modifier = Modifier
                    .background(Color.Black.copy(0.19f), CircleShape)
                    .clip(CircleShape)
                    .rememberedClickable { onDone() }
                    .padding(
                        horizontal = CodeTheme.dimens.grid.x2,
                        vertical = CodeTheme.dimens.grid.x1
                    ),
                text = "Done",
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
        }
    }
}