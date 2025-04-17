package com.flipcash.app.balance.internal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.flipcash.app.balance.internal.components.BalanceHeader
import com.flipcash.app.balance.internal.components.FeedItem
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient

@Composable
internal fun BalanceScreenContent(viewModel: BalanceViewModel) {
    val state by viewModel.stateFlow.collectAsState()

    Column {
        BalanceHeader(
            modifier = Modifier
                .fillMaxWidth(),
            balance = state.balance
        )

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .verticalScrollStateGradient(
                    scrollState = listState,
                    color = CodeTheme.colors.background,
                    showAtEnd = true
                ),
            state = listState
        ) {
            itemsIndexed(state.feed, key = { _, item -> item.id }) { index, message ->
                FeedItem(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .animateItem(),
                    message = message
                )

                if (index < state.feed.lastIndex) {
                    Divider(color = CodeTheme.colors.dividerVariant)
                }
            }

            item {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}