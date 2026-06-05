package com.flipcash.app.currency.internal.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipcash.app.currency.internal.RegionListItem
import com.getcode.opencode.model.financial.Currency
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.utils.sheetResignmentBehavior

@Composable
internal fun RegionList(
    items: List<RegionListItem>,
    selected: Currency?,
    modifier: Modifier = Modifier,
    onRemoved: (Currency) -> Unit,
    onSelected: (Currency) -> Unit,
) {
    val listState = rememberLazyListState()

    AnimatedContent(
        targetState = items.isEmpty(),
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "region-list",
    ) { isEmpty ->
        if (isEmpty) {
            Box(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeTheme.colors.background)
                    .verticalScrollStateGradient(
                        scrollState = listState,
                        color = CodeTheme.colors.background,
                    )
                    .sheetResignmentBehavior(listState),
                state = listState,
            ) {
                items(
                    items,
                    key = { item ->
                        when (item) {
                            is RegionListItem.RegionCurrencyItem -> item.currency.code
                            is RegionListItem.TitleItem -> item.text
                        }
                    },
                ) { listItem ->
                    val currencyCode = when (listItem) {
                        is RegionListItem.RegionCurrencyItem -> listItem.currency.code
                        else -> ""
                    }

                    Box(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .height(if (listItem !is RegionListItem.TitleItem) 70.dp else 60.dp)
                    ) {
                        when (listItem) {
                            is RegionListItem.TitleItem -> {
                                GroupHeader(
                                    modifier = Modifier.align(Alignment.BottomStart),
                                    text = listItem.text
                                )
                            }

                            is RegionListItem.RegionCurrencyItem -> {
                                ListRowItem(
                                    item = listItem,
                                    isSelected = selected?.code.orEmpty() == currencyCode,
                                    onRemoved = { onRemoved(listItem.currency) },
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
    }
}
