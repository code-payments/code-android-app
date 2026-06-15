package com.flipcash.app.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    ) {
        if (header != null) {
            item { header() }
        }
        items(items, key = { it.id }, contentType = { it }) { item ->
            ListItem(modifier = Modifier.animateItem(), item = item, showChevron = showChevrons) {
                onItemClick(item)
            }
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