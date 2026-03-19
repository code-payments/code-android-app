package com.flipcash.app.onramp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.data.OnRampProviderDestination
import com.flipcash.app.onramp.internal.screens.OnRampProviderListScreen
import com.flipcash.features.onramp.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun OnRampProviderListScreen(
    neededAmount: Long? = null,
    neededCurrency: CurrencyCode? = null,
) {
    val navigator = LocalCodeNavigator.current

    val viewModel = flowScopedViewModel<OnRampViewModel>(key = OnRampFlowTracker.key)

    Box {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = stringResource(R.string.title_depositFunds),
                titleAlignment = Alignment.CenterHorizontally,
                isInModal = true,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
            )
            OnRampProviderListScreen(viewModel)
        }
    }
}
