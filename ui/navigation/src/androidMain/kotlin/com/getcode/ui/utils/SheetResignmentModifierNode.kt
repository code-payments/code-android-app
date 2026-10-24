package com.getcode.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class SheetResignmentConnection(
    internal val setGesturesEnabled: (Boolean) -> Unit,
    private val autoResetDelayMs: Long,
) : NestedScrollConnection {

    var waitingForSecondDrag = false
    var resetJob: Job? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (waitingForSecondDrag && available.y < 0f) {
            waitingForSecondDrag = false
            resetJob?.cancel()
            setGesturesEnabled(true)
        }
        return Offset.Zero
    }

    fun onAtTop() {
        resetJob?.cancel()
        waitingForSecondDrag = true
        setGesturesEnabled(false)
    }

    fun onScrolledAway() {
        resetJob?.cancel()
        waitingForSecondDrag = false
        setGesturesEnabled(false)
    }

    fun onDetach() {
        resetJob?.cancel()
        waitingForSecondDrag = false
        setGesturesEnabled(true)
    }
}

private class SheetResignmentModifierNode(
    private val isAtTopProvider: () -> Boolean,
    private val connection: SheetResignmentConnection,
    private val autoResetDelayMs: Long,
) : Modifier.Node(), DelegatableNode {

    private val isAtTop by derivedStateOf { isAtTopProvider() }

    private var observeJob: Job? = null

    override fun onAttach() {
        super.onAttach()
        observeJob = coroutineScope.launch {
            snapshotFlow { isAtTop }.collectLatest { atTop ->
                if (atTop) {
                    connection.onAtTop()
                    if (autoResetDelayMs > 0) {
                        connection.resetJob = coroutineScope.launch {
                            delay(autoResetDelayMs)
                            connection.waitingForSecondDrag = false
                            connection.setGesturesEnabled(true)
                        }
                    }
                } else {
                    connection.onScrolledAway()
                }
            }
        }
    }

    override fun onDetach() {
        observeJob?.cancel()
        connection.onDetach()
        super.onDetach()
    }
}

private data class SheetResignmentElement(
    val isAtTopProvider: () -> Boolean,
    val connection: SheetResignmentConnection,
    val autoResetDelayMs: Long,
) : ModifierNodeElement<SheetResignmentModifierNode>() {

    override fun create() = SheetResignmentModifierNode(isAtTopProvider, connection, autoResetDelayMs)

    override fun update(node: SheetResignmentModifierNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "sheetResignmentBehavior"
        properties["autoResetDelayMs"] = autoResetDelayMs
    }
}


/**
 * Prevents a [ModalBottomSheet] from starting dismiss on the first downward drag after the
 * internal [LazyList] reaches the top. The first drag triggers overscroll bounce only;
 * a subsequent downward drag (or after [autoResetDelayMs]) will dismiss normally.
 *
 * Requires [LocalSheetGesturesState] to be provided, or pass [setGesturesEnabled] explicitly.
 *
 * @param listState the [LazyListState] of the list inside the sheet
 * @param setGesturesEnabled callback to toggle sheet gesture handling (e.g. `sheetState.gesturesEnabled`)
 * @param autoResetDelayMs time after which a paused-then-drag will re-enable dismiss; 0 to disable
 */
@Composable
fun Modifier.sheetResignmentBehavior(
    listState: LazyListState,
    setGesturesEnabled: (Boolean) -> Unit = LocalSheetGesturesState.current,
    autoResetDelayMs: Long = 700L,
): Modifier {
    val connection = remember(setGesturesEnabled, autoResetDelayMs) {
        SheetResignmentConnection(setGesturesEnabled, autoResetDelayMs)
    }
    return this
        .nestedScroll(connection)
        .then(SheetResignmentElement(
            isAtTopProvider = {
                listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
            },
            connection = connection,
            autoResetDelayMs = autoResetDelayMs,
        ))
}

/**
 * Prevents a [ModalBottomSheet] from starting dismiss on the first downward drag after the
 * internal [ScrollState] reaches the top. The first drag triggers overscroll bounce only;
 * a subsequent downward drag (or after [autoResetDelayMs]) will dismiss normally.
 *
 * Requires [LocalSheetGesturesState] to be provided, or pass [setGesturesEnabled] explicitly.
 *
 * @param scrollState the [ScrollState] of the scrollable Column inside the sheet
 * @param setGesturesEnabled callback to toggle sheet gesture handling (e.g. `sheetState.gesturesEnabled`)
 * @param autoResetDelayMs time after which a paused-then-drag will re-enable dismiss; 0 to disable
 */
@Composable
fun Modifier.sheetResignmentBehavior(
    scrollState: ScrollState,
    setGesturesEnabled: (Boolean) -> Unit = LocalSheetGesturesState.current,
    autoResetDelayMs: Long = 700L,
): Modifier {
    val connection = remember(setGesturesEnabled, autoResetDelayMs) {
        SheetResignmentConnection(setGesturesEnabled, autoResetDelayMs)
    }
    return this
        .nestedScroll(connection)
        .then(SheetResignmentElement(
            isAtTopProvider = { scrollState.value == 0 },
            connection = connection,
            autoResetDelayMs = autoResetDelayMs,
        ))
}