package com.flipcash.app.transactions.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.transactions.internal.components.FeedItem
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient

@Composable
internal fun TransactionHistoryScreen(viewModel: TransactionHistoryViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val transactions = viewModel.transactions.collectAsLazyPagingItems()

    TransactionHistoryScreenContent(state, transactions, viewModel::dispatchEvent)
}

@Composable
private fun TransactionHistoryScreenContent(
    state: TransactionHistoryViewModel.State,
    transactions: LazyPagingItems<ActivityFeedMessage>,
    dispatch: (TransactionHistoryViewModel.Event) -> Unit,
) {
    FeedList(
        modifier = Modifier.fillMaxSize(),
        state = state,
        feed = transactions,
        dispatchEvent = dispatch
    )
}

@Composable
private fun FeedList(
    modifier: Modifier = Modifier,
    state: TransactionHistoryViewModel.State,
    feed: LazyPagingItems<ActivityFeedMessage>,
    dispatchEvent: (TransactionHistoryViewModel.Event) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
                showAtEnd = true
            ),
        state = listState
    ) {
        if (feed.itemCount == 0 && feed.loadState.append is LoadState.NotLoading) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize().padding(bottom = CodeTheme.dimens.inset),
                    contentAlignment = Alignment.Center
                ) {

                }
            }
        } else {
            items(feed.itemCount, key = feed.itemKey { item -> item.id }) { index ->
                val message = feed[index] ?: return@items
                FeedItem(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .animateItem(),
                    message = message,
                    canViewDetails = state.canViewDetails,
                    isExpanded = state.expandedItem == message.id,
                    onCancel = { dispatchEvent(TransactionHistoryViewModel.Event.OnCancelRequested(message)) },
                    onViewDetails = { dispatchEvent(TransactionHistoryViewModel.Event.ViewDetails(message.id)) },
                )

                if (index < feed.itemCount - 1) {
                    Divider(color = CodeTheme.colors.dividerVariant)
                }
            }

            item {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}