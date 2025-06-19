package com.flipcash.app.pools.internal.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.pools.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PoolListScreen(
    viewModel: PoolListViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    PoolListScreenContent(state, viewModel::dispatchEvent)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PoolListViewModel.Event.OnPoolClicked>()
            .onEach {

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
    dispatch: (PoolListViewModel.Event) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
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
                onClick = { dispatch(PoolListViewModel.Event.OnCreatePool)  },
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
                dispatch = {}
            )
        }
    }
}