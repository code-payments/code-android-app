package com.getcode.navigation.scenes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.BottomSheetState
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledBottomSheet
import com.composeunstyled.rememberBottomSheetState
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.noRippleClickable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * State of an [InWindowBottomSheet]. Create with [rememberInWindowSheetState].
 *
 * [isLocked] is read live: while it is true the sheet can't be closed by a drag, the scrim or back.
 * [hide] ignores it, so a flow that locks the sheet for the length of a send can still take it
 * down once the send is done.
 */
@Stable
class InWindowSheetState internal constructor(
    internal val isLocked: () -> Boolean,
) {
    internal lateinit var sheet: BottomSheetState

    /** True once [hide] has been asked for: the caller closed it, so no dismiss request follows. */
    internal var hidingByCaller by mutableStateOf(false)
        private set

    /** Slides the sheet down and returns when it has settled. Does not call `onDismissRequest`. */
    suspend fun hide() {
        hidingByCaller = true
        sheet.animateTo(SheetDetent.Hidden)
    }
}

@Composable
fun rememberInWindowSheetState(isLocked: () -> Boolean = { false }): InWindowSheetState {
    val locked = rememberUpdatedState(isLocked)
    val state = remember { InWindowSheetState { locked.value() } }
    state.sheet = rememberBottomSheetState(
        initialDetent = SheetDetent.Hidden,
        detents = listOf(SheetDetent.Hidden, WrapContent),
        confirmDetentChange = { detent ->
            detent != SheetDetent.Hidden || state.hidingByCaller || !state.isLocked()
        },
    )
    return state
}

/**
 * A bottom sheet drawn inside the composition that hosts it, not in a window of its own.
 *
 * Material's `ModalBottomSheet` is a separate window, so anything the Activity window draws, such
 * as a bottom bar, is covered by it. This one is an ordinary layer of the host's content: fill
 * the route with it (it takes the whole parent), and what the route's entry decorator draws after
 * the content, the bar included, lands above it.
 *
 * Opens to its content height, capped at the same fraction the navigation sheets use. A drag
 * down past the threshold, a tap on the scrim ([dismissOnScrimTap]) or back calls
 * [onDismissRequest]; none of them does anything while the state is locked.
 */
@Composable
fun InWindowBottomSheet(
    state: InWindowSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = CodeTheme.colors.background,
    dismissOnScrimTap: Boolean = true,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val sheet = state.sheet
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)

    LaunchedEffect(sheet) { sheet.animateTo(WrapContent) }

    // [UnstyledBottomSheet] has no dismissal callback: watch for it settling Hidden after it was
    // shown, the way the navigation sheets do, and ignore a close the caller asked for itself.
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.targetDetent }.first { it != SheetDetent.Hidden }
        snapshotFlow { sheet.isIdle && sheet.currentDetent == SheetDetent.Hidden }.first { it }
        if (!state.hidingByCaller) currentOnDismiss()
    }

    val requestClose: () -> Unit = {
        if (!state.isLocked() && sheet.currentDetent != SheetDetent.Hidden) {
            scope.launch { sheet.animateTo(SheetDetent.Hidden) }
        }
    }
    // Always taken, locked or not: back mid-send must not fall through and pop the route.
    BackHandler(enabled = true, onBack = requestClose)

    val scrimColor = CodeTheme.colors.scrim
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val progress = sheet.progress(SheetDetent.Hidden, WrapContent).coerceIn(0f, 1f)
                    drawRect(scrimColor.copy(alpha = scrimColor.alpha * progress))
                }
                .then(if (dismissOnScrimTap) Modifier.noRippleClickable(onClick = requestClose) else {
                    // Still swallows touches, so nothing underneath is reachable while it is up.
                    Modifier.noRippleClickable { }
                }),
        )
        UnstyledBottomSheet(
            state = sheet,
            modifier = Modifier.fillMaxSize(),
            enabled = gesturesEnabled && !state.isLocked(),
        ) {
            Sheet(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(containerColor),
            ) {
                content()
            }
        }
    }
}
