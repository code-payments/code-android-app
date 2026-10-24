package com.flipcash.app.purchase

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.purchase.internal.PurchaseAccountScreenContent
import com.flipcash.app.purchase.internal.PurchaseAccountViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.purchase.internal.PurchaseAccountScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun PurchaseAccountScreen(
    fromLogin: Boolean = false
) {
    val navigator = LocalCodeNavigator.current
    Column {
        AppBarWithTitle(
            onBackIconClicked = {
                if (fromLogin) {
                    navigator.popAll()
                } else {
                    navigator.pop()
                }
            }
        )
        PurchaseAccountScreen(hiltViewModel())
    }
}

/** The purchase step inside onboarding; the app passes it to OnboardingFlowScreen. */
@Composable
fun OnboardingPurchaseStep(onAccountCreated: () -> Unit) {
    val viewModel = hiltViewModel<PurchaseAccountViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val currentOnAccountCreated by rememberUpdatedState(onAccountCreated)

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PurchaseAccountViewModel.Event.OnAccountCreated>()
            .onEach { currentOnAccountCreated() }
            .launchIn(this)
    }

    PurchaseAccountScreenContent(state, viewModel::dispatchEvent)
}
