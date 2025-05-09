package com.flipcash.app.currency.internal.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipcash.app.currency.internal.CurrencyListItem
import com.getcode.opencode.model.financial.Currency
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeCircularProgressIndicator

@Composable
internal fun CurrencyList(
    items: List<CurrencyListItem>,
    isLoading: Boolean,
    selected: Currency?,
    modifier: Modifier = Modifier,
    onRemoved: (Currency) -> Unit,
    onSelected: (Currency) -> Unit,
) {
    val groups by remember(items) {
        derivedStateOf {
            if (items.isEmpty()) return@derivedStateOf emptyList<CurrencyListItem>() to emptyList<CurrencyListItem>()
            val index = items.indexOfLast { it is CurrencyListItem.TitleItem }
            if (index == 0) {
                // no recents
                emptyList<CurrencyListItem>() to items
            } else {
                // recents
                items.subList(0, index) to items.subList(
                    index,
                    items.lastIndex
                )
            }
        }
    }

    val (recents, other) = groups

    var recentItems by remember(recents) {
        mutableStateOf(recents)
    }

    var otherItems by remember(other) {
        mutableStateOf(other)
    }

    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(CodeTheme.colors.background)
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
            ),
        state = listState,
    ) {
        if (isLoading) {
            item {
                Box(Modifier.fillParentMaxSize()) {
                    CodeCircularProgressIndicator(
                        Modifier.align(
                            Alignment.TopCenter
                        )
                    )
                }
            }
        }

        items(recentItems) { listItem ->
            val currencyCode = when (listItem) {
                is CurrencyListItem.RegionCurrencyItem -> listItem.currency.code
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (listItem !is CurrencyListItem.TitleItem) 70.dp else 60.dp)
            ) {

                when (listItem) {
                    is CurrencyListItem.TitleItem -> {
                        GroupHeader(
                            modifier = Modifier.align(Alignment.BottomStart),
                            text = listItem.text
                        )
                    }

                    is CurrencyListItem.RegionCurrencyItem -> {
                        ListRowItem(
                            item = listItem,
                            isSelected = selected?.code.orEmpty() == currencyCode,
                            onRemoved = {
                                recentItems = if (recentItems.count() == 2) {
                                    emptyList()
                                } else {
                                    recentItems.minus(listItem)
                                }
                                val title = otherItems[0]
                                val filtered =
                                    otherItems.filterIsInstance<CurrencyListItem.RegionCurrencyItem>() + listItem

                                otherItems = listOf(title) + filtered.sortedBy { it.currency.name }

                                onRemoved(listItem.currency)

                            },
                        ) {
                            onSelected(listItem.currency)
                        }
                    }
                }
            }
        }

        items(otherItems) { listItem ->
            val currencyCode = when (listItem) {
                is CurrencyListItem.RegionCurrencyItem -> listItem.currency.code
                else -> ""
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (listItem !is CurrencyListItem.TitleItem) 70.dp else 60.dp)
            ) {

                when (listItem) {
                    is CurrencyListItem.TitleItem -> {
                        GroupHeader(
                            modifier = Modifier.align(Alignment.BottomStart),
                            text = listItem.text
                        )
                    }

                    is CurrencyListItem.RegionCurrencyItem -> {
                        var isSwipedAway by remember(listItem) {
                            mutableStateOf(false)
                        }

                        val animatedHeight by animateDpAsState(
                            targetValue = if (!isSwipedAway) 70.dp else 0.dp,
                            label = "height animation",
                            animationSpec = tween(300),
                            finishedListener = {
                                if (it == 0.dp) {
                                    onRemoved(listItem.currency)
                                }
                            }
                        )
                        ListRowItem(
                            modifier = Modifier.height(animatedHeight),
                            item = listItem,
                            isSelected = selected?.code.orEmpty() == currencyCode,
                            onRemoved = {
                                isSwipedAway = true
                                onRemoved(listItem.currency)
                            },
                        ) {
                            onSelected(listItem.currency)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}