package com.flipcash.app.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.BetaIndicator
import com.getcode.ui.components.ListItem
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.utils.sheetResignmentBehavior

@Composable
fun <T> MenuList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    items: List<MenuItem<T>>,
    showChevrons: Boolean = false,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    userScrollEnabled: Boolean = true,
    itemModifier: Modifier = Modifier,
    onItemClick: (MenuItem<T>) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .verticalScrollStateGradient(
                scrollState = state,
                isLongGradient = true,
            ).sheetResignmentBehavior(state),
        state = state,
        contentPadding = contentPadding,
        userScrollEnabled = userScrollEnabled,
    ) {
        if (header != null) {
            item { header() }
        }
        items(items, key = { it.id }, contentType = { it }) { item ->
            // [itemModifier] wraps the whole row rather than riding on ListItem's own modifier:
            // ListItem emits its divider as a sibling of the row, so a modifier handed to the row
            // alone would leave the divider behind (visibly, for callers fading the list out).
            Column(modifier = Modifier.animateItem().then(itemModifier)) {
                ListItem(item = item, showChevron = showChevrons) {
                    onItemClick(item)
                }
            }
        }

        if (footer != null) {
            item { footer() }
        }
    }
}

/**
 * Slot-driven variant: the caller renders each row's trailing content via [endSlot] (chevron,
 * loading spinner, etc.). Use this when a row needs a stateful trailing indicator.
 *
 * [isItemEnabled] and [supportingTextFor] let a row report that it can't act right now and say
 * why — the biometrics row on a device with nothing enrolled, for instance.
 */
@Composable
fun <T> MenuList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    items: List<MenuItem<T>>,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    isItemEnabled: (MenuItem<T>) -> Boolean = { true },
    supportingTextFor: @Composable (MenuItem<T>) -> String? = { null },
    onItemClick: (MenuItem<T>) -> Unit,
    endSlot: @Composable RowScope.(MenuItem<T>) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .verticalScrollStateGradient(
                scrollState = state,
                isLongGradient = true,
            ).sheetResignmentBehavior(state),
        state = state,
        contentPadding = contentPadding,
    ) {
        if (header != null) {
            item { header() }
        }
        items(items, key = { it.id }, contentType = { it }) { item ->
            ListItem(
                headline = item.name,
                icon = item.icon,
                modifier = Modifier.animateItem(),
                enabled = isItemEnabled(item),
                supportingText = supportingTextFor(item),
                onClick = { onItemClick(item) },
                endSlot = {
                    // The badge stays this overload's job so a staff row is marked the same way
                    // whether or not the caller supplies its own trailing content.
                    if (item.showBetaIndicator) {
                        BetaIndicator()
                        Spacer(Modifier.width(CodeTheme.dimens.grid.x2))
                    }
                    endSlot(item)
                },
            )
        }
        if (footer != null) {
            item { footer() }
        }
    }
}

@Composable
private fun <T> ListItem(
    modifier: Modifier = Modifier,
    item: MenuItem<T>,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headline = item.name,
        icon = item.icon,
        modifier = modifier,
        onClick = onClick,
        showChevron = showChevron,
        showBetaIndicator = item.showBetaIndicator,
    )
}