package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.AppRoute.Transfers.Deposit
import com.flipcash.app.core.AppRoute.Transfers.Withdrawal
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.internal.CurrenciesDeck
import com.flipcash.app.tokens.internal.SelectTokenScreen
import com.flipcash.app.tokens.internal.rememberCurrenciesCardExpansion
import com.flipcash.app.tokens.internal.showsCurrencyDeck
import com.flipcash.app.tokens.ui.HiltSelectTokenViewModel
import com.flipcash.app.tokens.ui.SelectTokenViewModel
import com.flipcash.app.tokens.ui.TokenListPresentation
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun TokenSelectScreen(
    purpose: TokenPurpose,
    showTopBar: Boolean = purpose !is TokenPurpose.LaunchFunding,
    presentation: TokenListPresentation = TokenListPresentation.Default,
    loadingMint: Mint? = null,
) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<HiltSelectTokenViewModel>()

    val topBar: @Composable (Modifier) -> Unit = { modifier ->
        AppBarWithTitle(
            modifier = modifier,
            title = when (purpose) {
                is TokenPurpose.Swap -> stringResource(R.string.title_selectPaymentCurrency)
                is TokenPurpose.LaunchFunding -> stringResource(R.string.title_selectPaymentCurrency)
                else -> stringResource(R.string.title_selectCurrency)
            },
            onBackIconClicked = { navigator.pop() },
            titleAlignment = Alignment.CenterHorizontally,
        )
    }

    if (showsCurrencyDeck(purpose, LocalNavBarShell.current)) {
        // The Group Money shell's currencies screen: the wallet's card deck, and a tapped card opens
        // its currency in place over it, as it does on the classic wallet.
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        val expansion = rememberCurrenciesCardExpansion()
        CurrencyCardExpandHost(controller = expansion) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showTopBar) {
                    // Fades with the card's flight, as the wallet's header does.
                    topBar(
                        Modifier.graphicsLayer {
                            alpha = 1f - CardExpansionController.heroProgress(expansion.progress.value)
                        }
                    )
                }
                state.tokens?.takeIf { it.isNotEmpty() }?.let { tokens ->
                    CurrenciesDeck(
                        tokens = tokens,
                        controller = expansion,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("currencies_token_card_stack"),
                    )
                }
            }
        }
    } else {
        Column(
            modifier = if (presentation.wrapHeight) Modifier.fillMaxWidth()
            else Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTopBar) {
                topBar(Modifier)
            }

            SelectTokenScreen(viewModel, presentation = presentation, loadingMint = loadingMint)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(purpose))
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OpenScreen>()
            .map { it.route }
            .onEach { navigator.push(it) }
            .launchIn(this)
    }

    // handle the cases where we are inserted in a flow to select a token
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
            .filter { it.fromUser }
            .map { it.mint }
            .onEach {
                when (purpose) {
                    // Balance is the Money tab's currency count. Under the Group Money shell that
                    // screen is the card deck, which opens a currency in place and never lands here;
                    // this push is the plain list's way into a currency.
                    TokenPurpose.Balance -> navigator.push(AppRoute.Token.Info(it, TokenInfoEntry.Currencies))
                    is TokenPurpose.Select -> Unit
                    TokenPurpose.Withdraw -> {
                        navigator.push(Withdrawal())
                    }

                    TokenPurpose.Deposit -> {
                        navigator.push(Deposit())
                    }

                    is TokenPurpose.LaunchFunding -> Unit
                    is TokenPurpose.Swap -> Unit
                    is TokenPurpose.ConvertDestination -> Unit
                    is TokenPurpose.BuyFunding -> Unit
                }
            }.launchIn(this)
    }

    // handle the case where we are changing the selected token
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SelectTokenViewModel.Event.OnTokenChanged>()
            .onEach { navigator.pop() }
            .launchIn(this)
    }
}
