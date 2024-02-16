package com.getcode.view.main.currency

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissState
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FixedThreshold
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.Brand
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.theme.inputColors
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.utils.keyboardAsState
import com.getcode.ui.utils.rememberedClickable
import com.getcode.view.main.giveKin.CurrencyListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
fun CurrencySelectionSheet(
    viewModel: CurrencyViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val composeScope = rememberCoroutineScope()
    var searchQuery by remember {
        mutableStateOf(TextFieldValue())
    }

    LaunchedEffect(searchQuery, state.currencySearchText) {
        if (searchQuery.text != state.currencySearchText) {
            viewModel.dispatchEvent(CurrencyViewModel.Event.OnSearchQueryChanged(searchQuery.text))
        }
    }

    val keyboard by keyboardAsState()

    Column(
        modifier = Modifier.imePadding()
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = CodeTheme.dimens.grid.x2)
                .padding(horizontal = CodeTheme.dimens.grid.x3),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = White50,
                )
            },
            placeholder = {
                Text(
                    stringResource(id = R.string.subtitle_searchCurrencies),
                    style = CodeTheme.typography.subtitle1.copy(
                        fontSize = 16.sp,
                    )
                )
            },
            trailingIcon = {
                if (searchQuery.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchQuery = TextFieldValue()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = White50,
                        )
                    }
                }
            },
            value = searchQuery,
            onValueChange = { searchQuery = it },
            textStyle = CodeTheme.typography.subtitle1.copy(
                fontSize = 16.sp,
            ),
            singleLine = true,
            colors = inputColors(),
            shape = RoundedCornerShape(size = 5.dp)
        )

        val groups by remember(state.listItems) {
            derivedStateOf {
                if (state.listItems.isEmpty()) return@derivedStateOf emptyList<CurrencyListItem>() to emptyList<CurrencyListItem>()
                val index = state.listItems.indexOfLast { it is CurrencyListItem.TitleItem }
                if (index == 0) {
                    // no recents
                    emptyList<CurrencyListItem>() to state.listItems
                } else {
                    // recents
                    state.listItems.subList(0, index) to state.listItems.subList(
                        index,
                        state.listItems.lastIndex
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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand)
                .weight(1f)
        ) {
            if (state.loading) {
                item {
                    Box(Modifier.fillParentMaxSize()) {
                        CodeCircularProgressIndicator(Modifier.align(Alignment.TopCenter))
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
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    modifier = Modifier.padding(bottom = 10.dp),
                                    style = CodeTheme.typography.body2,
                                    color = BrandLight,
                                    text = listItem.text
                                )
                            }
                        }

                        is CurrencyListItem.RegionCurrencyItem -> {
                            ListRowItem(
                                item = listItem,
                                isSelected = state.selectedCurrencyCode.orEmpty() == currencyCode,
                                onRemoved = {
                                    if (recentItems.count() == 2) {
                                        recentItems = emptyList()
                                    } else {
                                        recentItems = recentItems.minus(listItem)
                                    }
                                    val title = otherItems[0]
                                    val items =
                                        otherItems.filterIsInstance<CurrencyListItem.RegionCurrencyItem>() + listItem

                                    otherItems = listOf(title) + items.sortedBy { it.currency.name }
                                    viewModel.dispatchEvent(
                                        CurrencyViewModel.Event.OnRecentCurrencyRemoved(
                                            listItem.currency
                                        )
                                    )
                                },
                            ) {
                                composeScope.launch {
                                    if (keyboard) {
                                        keyboardController?.hide()
                                        delay(500)
                                    }
                                    navigator.popWithResult(listItem.currency)
                                }
                                viewModel.dispatchEvent(
                                    CurrencyViewModel.Event.OnSelectedCurrencyChanged(
                                        listItem.currency
                                    )
                                )
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
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    modifier = Modifier.padding(bottom = 10.dp),
                                    style = CodeTheme.typography.body2,
                                    color = BrandLight,
                                    text = listItem.text
                                )
                            }
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
                                        viewModel.dispatchEvent(
                                            CurrencyViewModel.Event.OnRecentCurrencyRemoved(
                                                listItem.currency
                                            )
                                        )
                                    }
                                }
                            )
                            ListRowItem(
                                modifier = Modifier.height(animatedHeight),
                                item = listItem,
                                isSelected = state.selectedCurrencyCode.orEmpty() == currencyCode,
                                onRemoved = {
                                    isSwipedAway = true
                                    viewModel.dispatchEvent(
                                        CurrencyViewModel.Event.OnRecentCurrencyRemoved(
                                            listItem.currency
                                        )
                                    )
                                },
                            ) {
                                composeScope.launch {
                                    if (keyboard) {
                                        keyboardController?.hide()
                                        delay(500)
                                    }
                                    navigator.popWithResult(listItem.currency)
                                }
                                viewModel.dispatchEvent(
                                    CurrencyViewModel.Event.OnSelectedCurrencyChanged(
                                        listItem.currency
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ListRowItem(
    modifier: Modifier = Modifier,
    item: CurrencyListItem.RegionCurrencyItem,
    isSelected: Boolean,
    onRemoved: () -> Unit,
    onClick: () -> Unit
) {

    var removed by remember(item) {
        mutableStateOf(false)
    }

    val dismissState = remember(item) {
        DismissState(
            initialValue = DismissValue.Default,
            confirmStateChange = {
                if (it == DismissValue.DismissedToStart) {
                    removed = true
                    true
                } else false
            }
        )
    }

    SwipeToDismiss(
        state = dismissState,
        dismissThresholds = { FixedThreshold(150.dp) },
        directions = if (item.isRecent) setOf(DismissDirection.EndToStart) else emptySet(),
        background = {
            if (item.isRecent) {
                DismissBackground(dismissState)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brand)
                .let {
                    if (item.currency.rate > 0) {
                        it.rememberedClickable { onClick() }
                    } else it
                }
                .padding(horizontal = CodeTheme.dimens.inset)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.CenterStart)
                    .alpha(if (item.currency.rate <= 0) 0.25f else 1.0f)
            ) {
                item.currency.resId?.let { resId ->
                    Image(
                        modifier = Modifier
                            .padding(end = CodeTheme.dimens.grid.x3)
                            .requiredSize(CodeTheme.dimens.staticGrid.x6)
                            .clip(CodeTheme.shapes.large)
                            .align(Alignment.CenterVertically),
                        painter = painterResource(resId),
                        contentDescription = ""
                    )
                }
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.CenterVertically),
                ) {
                    Text(
                        text = item.currency.name,
                        style = CodeTheme.typography.body1
                    )
                }
            }

            Image(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterEnd)
                    .alpha(if (item.currency.rate <= 0) 0.25f else 1.0f),
                painter = painterResource(
                    if (isSelected)
                        R.drawable.ic_checked_blue else R.drawable.ic_unchecked
                ),
                contentDescription = ""
            )
        }
    }

    LaunchedEffect(removed) {
        if (removed) {
            delay(200)
            onRemoved()
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DismissBackground(dismissState: DismissState) {
    val color = when (dismissState.dismissDirection) {
        DismissDirection.EndToStart -> CodeTheme.colors.error
        else -> Brand
    }
    val direction = dismissState.dismissDirection

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(end = CodeTheme.dimens.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (direction == DismissDirection.EndToStart) {
            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = "delete"
            )
        }
    }
}