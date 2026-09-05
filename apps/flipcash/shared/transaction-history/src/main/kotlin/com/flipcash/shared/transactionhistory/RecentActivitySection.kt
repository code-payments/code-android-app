package com.flipcash.shared.transactionhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.getcode.theme.CodeTheme
import com.getcode.util.resources.R

/**
 * The "Recent" activity section — a tappable header (label + trailing chevron) followed by the list of
 * [ActivityFeedRow]s — shared by the wallet screen and the token-info screen so both stay consistent
 * (chevron sits right after the label, same row spacing and item rendering).
 *
 * It's a [LazyListScope] extension so callers drop it straight into their own `LazyColumn`. Screen-specific
 * chrome (title text/style, click target, and horizontal insets) is supplied by the caller; the internal
 * layout of the header and rows is owned here.
 *
 * @param modifier caller-owned modifier for the header row (padding + `clickable`); full-width is applied
 *   internally.
 * @param itemPadding padding applied to each activity row (e.g. the screen's horizontal inset).
 * @param onItemClick opens one entry's details; null leaves the rows inert.
 */
fun LazyListScope.recentActivitySection(
    transactions: List<TransactionListItem>,
    modifier: Modifier = Modifier,
    itemPadding: PaddingValues = PaddingValues(),
    onItemClick: ((TransactionListItem) -> Unit)? = null,
) {
    item(key = "recent_activity_header", contentType = "recent_activity_header") {
        RecentActivityHeader(modifier = modifier)
    }
    items(transactions, key = { it.id }, contentType = { "recent_activity_row" }) { item ->
        ActivityFeedRow(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .padding(itemPadding),
            onClick = onItemClick?.let { click -> { click(item) } },
        )
    }
}

@Composable
private fun LazyItemScope.RecentActivityHeader(modifier: Modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(com.flipcash.core.R.string.title_recentActivity),
            style = CodeTheme.typography.screenTitle,
            color = CodeTheme.colors.textMain,
        )
        Icon(
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )
    }
}
