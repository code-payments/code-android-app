package com.flipcash.app.advanced

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.advanced.internal.AdvancedFeaturesScreen
import com.flipcash.app.advanced.internal.AdvancedFeaturesScreenViewModel
import com.flipcash.app.bill.customization.LocalBillPlaygroundController
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun AdvancedFeaturesScreen() {
    val navigator = LocalCodeNavigator.current
    val billPlayground = LocalBillPlaygroundController.current
    val viewModel = hiltViewModel<AdvancedFeaturesScreenViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_advancedFeatures),
            titleAlignment = Alignment.CenterHorizontally,
            isInModal = true,
            backButton = true,
            onBackIconClicked = { navigator.pop() }
        )

        AdvancedFeaturesScreen(viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<AdvancedFeaturesScreenViewModel.Event.OpenScreen>()
            .onEach { navigator.push(it.screen) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<AdvancedFeaturesScreenViewModel.Event.OpenBillPlayground>()
            .onEach {
                navigator.hide()
                billPlayground.customizeFor(Token.usdf)
            }
            .launchIn(this)
    }
}
