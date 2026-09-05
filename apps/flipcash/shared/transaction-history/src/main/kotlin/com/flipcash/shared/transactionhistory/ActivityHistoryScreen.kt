package com.flipcash.shared.transactionhistory

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.verticalScrollStateGradient

/**
 * The full unified paged activity history — the "dive in" from the wallet's recent-activity preview.
 * Renders the same [ActivityFeedRow]s, unbounded and paged via [ActivityHistoryViewModel].
 */
@Composable
fun ActivityHistoryScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<ActivityHistoryViewModel>()
    val items = viewModel.transactions.collectAsLazyPagingItems()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_activity),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { navigator.pop() },
        )
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // Fade rows out as they scroll up under the app bar; no bottom fade.
                .verticalScrollStateGradient(
                    scrollState = listState,
                    color = CodeTheme.colors.background,
                    showAtEnd = false,
                ),
            state = listState,
            contentPadding = PaddingValues(
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
            ),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.id },
            ) { index ->
                val item = items[index] ?: return@items
                ActivityFeedRow(
                    item = item,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navigator.push(AppRoute.Sheets.TransactionDetails(item.messageId)) },
                )
            }

            item {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}
