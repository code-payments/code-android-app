package com.getcode.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class SheetResignmentModifierNode(
    private val listState: LazyListState,
    private val setGesturesEnabled: (Boolean) -> Unit,
    private val autoResetDelayMs: Long = 700L  // time after which first-drag block expires
) : Modifier.Node(), DelegatableNode {

    private val isAtTop by derivedStateOf {
        listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
    }

    private var waitingForSecondDrag = false
    private var resetJob: Job? = null
    private var observeJob: Job? = null

    private val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (isAtTop && waitingForSecondDrag && available.y < 0f) {  // downward attempt
                // Consume the first downward motion → sheet doesn't move at all
                waitingForSecondDrag = false
                resetJob?.cancel()
                return available.copy(x = 0f)  // fully consume → no sheet motion
            }
            return Offset.Zero
        }
    }

    override fun onAttach() {
        super.onAttach()
        observeJob = coroutineScope.launch {
            snapshotFlow { isAtTop to listState.isScrollInProgress }
                .collect { (atTop, scrolling) -> updateGestures(atTop, scrolling) }
        }
    }

    private fun updateGestures(atTop: Boolean, scrolling: Boolean) {
        resetJob?.cancel()

        if (atTop && !scrolling) {
            waitingForSecondDrag = true
            setGesturesEnabled(false)

            if (autoResetDelayMs > 0) {
                resetJob = coroutineScope.launch {
                    delay(autoResetDelayMs)
                    waitingForSecondDrag = false
                    setGesturesEnabled(true)
                }
            }
        } else {
            waitingForSecondDrag = false
            setGesturesEnabled(false)
        }
    }

    override fun onDetach() {
        observeJob?.cancel()
        resetJob?.cancel()
        setGesturesEnabled(true)  // restore default
        super.onDetach()
    }
}

private data class TwoStepDismissElement(
    val listState: LazyListState,
    val setGesturesEnabled: (Boolean) -> Unit,
    val autoResetDelayMs: Long
) : ModifierNodeElement<SheetResignmentModifierNode>() {

    override fun create(): SheetResignmentModifierNode =
        SheetResignmentModifierNode(listState, setGesturesEnabled, autoResetDelayMs)

    override fun update(node: SheetResignmentModifierNode) {
        // No dynamic update needed (listState & lambda are stable)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "twoStepSheetDismiss"
        properties["listState"] = listState
        properties["autoResetDelayMs"] = autoResetDelayMs
    }
}

/**
 * Reusable modifier: Prevents sheet from starting dismiss on first downward drag after list reaches top.
 * - Consumes initial downward delta → no visible movement/snap-back.
 * - Enables sheet gestures when at top (until scroll away or auto-reset).
 */
@Composable
fun Modifier.sheetResignmentBehavior(
    listState: LazyListState,
    setGesturesEnabled: (Boolean) -> Unit = LocalSheetGesturesState.current,
    autoResetDelayMs: Long = 700L // 0L to disable auto-reset → strict "scroll away first"
): Modifier = this then TwoStepDismissElement(
    listState = listState,
    setGesturesEnabled = setGesturesEnabled,
    autoResetDelayMs = autoResetDelayMs
)