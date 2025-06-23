package com.flipcash.app.pools.internal.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.pools.internal.list.components.PoolListItem
import com.flipcash.app.pools.internal.list.components.PoolStatusSeparator
import com.flipcash.app.pools.internal.list.components.PoolSummaryRow
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.pools.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.Pill
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.format.Padding

@Composable
internal fun PoolListScreen(
    viewModel: PoolListViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val pools = viewModel.pools.collectAsLazyPagingItems()

    PoolListScreenContent(state, pools, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PoolListViewModel.Event.OnPoolClicked>()
            .map { it.pool }
            .onEach {
                navigator.push(
                    ScreenRegistry.get(
                        NavScreenProvider.HomeScreen.Pools.ChoiceSelection(it.id)
                    )
                )
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PoolListViewModel.Event.OnCreatePool>()
            .onEach {
                navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Pools.Create.Name))
            }.launchIn(this)
    }
}

@Composable
private fun PoolListScreenContent(
    state: PoolListViewModel.State,
    items: LazyPagingItems<PoolListItem>,
    dispatch: (PoolListViewModel.Event) -> Unit
) {
    if (items.itemCount == 0 && items.loadState.append is LoadState.NotLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(dispatch)
        }
    } else {
        CodeScaffold(
            bottomBar = {
                CodeButton(
                    onClick = { dispatch(PoolListViewModel.Event.OnCreatePool) },
                    text = stringResource(R.string.action_createNewPool),
                    buttonState = ButtonState.Filled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(
                            top = CodeTheme.dimens.grid.x2,
                            bottom = CodeTheme.dimens.grid.x2
                        ),
                )
            }
        ) { innerPadding ->
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(items.itemCount) { index ->
                    items[index]?.let { item ->
                        when (item) {
                            is PoolListItem.Header -> {
                                Box(modifier = Modifier.fillParentMaxWidth()) {
                                    PoolStatusSeparator(
                                        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x7),
                                        item = item
                                    )
                                }
                            }

                            is PoolListItem.PoolItem -> {
                                PoolSummaryRow(
                                    data = item.data,
                                    onClick = {
                                        dispatch(
                                            PoolListViewModel.Event.OnPoolClicked(
                                                item.data.pool
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Divider(color = CodeTheme.colors.divider)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.EmptyState(
    dispatch: (PoolListViewModel.Event) -> Unit
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.25f))
            Image(
                painter = painterResource(R.drawable.ic_pools_preview),
                contentDescription = "",
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.grid.x8)
                    .padding(top = CodeTheme.dimens.grid.x10)
                    .fillMaxWidth()
            )
            Text(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset),
                text = stringResource(R.string.subtitle_createPools),
                style = CodeTheme.typography.textMedium
                    .copy(textAlign = TextAlign.Center),
                color = CodeTheme.colors.textMain,
            )
            Spacer(Modifier.weight(1f))
            CodeButton(
                onClick = { dispatch(PoolListViewModel.Event.OnCreatePool) },
                text = stringResource(R.string.action_createNewPool),
                buttonState = ButtonState.Filled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2),
            )
        }
    }
}

@Preview
@Composable
private fun Preview_EmptyState() {
    FlipcashDesignSystem {
        Box(modifier = Modifier.background(CodeTheme.colors.background)) {
            PoolListScreenContent(
                state = PoolListViewModel.State(),
                items = flowOf(PagingData.empty<PoolListItem>()).collectAsLazyPagingItems(),
                dispatch = {}
            )
        }
    }
}