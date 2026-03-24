package com.flipcash.app.discovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.discovery.internal.TokenDiscoveryScreen
import com.flipcash.app.discovery.internal.TokenDiscoveryViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun TokenDiscoveryScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<TokenDiscoveryViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_discoverCurrencies),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { navigator.pop() },
        )
        TokenDiscoveryScreen(viewModel)
    }

    LaunchedEffect(Unit) {
        if (viewModel.stateFlow.value.category == null) {
            viewModel.dispatchEvent(TokenDiscoveryViewModel.Event.OnCategorySelected(DiscoverCategory.Popular))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<TokenDiscoveryViewModel.Event.OpenTokenInfo>()
            .map { it.mint }
            .onEach { navigator.navigate(AppRoute.Token.Info(it)) }
            .launchIn(this)
    }
}