package com.flipcash.app.tokens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.onramp.LocalExternalWalletOnRampController
import com.flipcash.app.tokens.internal.TokenInfoScreen
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.flipcash.features.tokens.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
fun TokenInfoScreen(
    mint: Mint,
    shortFall: Fiat?,
    fromDeeplink: Boolean,
) {
    val navigator = LocalCodeNavigator.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val analytics = rememberAnalytics()
        val viewModel = hiltViewModel<TokenInfoViewModel>()
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()
        AppBarWithTitle(
            isInModal = true,
            title = {
                state.token.dataOrNull?.let { token ->
                    TokenIconWithName(
                        token = token,
                        imageSize = CodeTheme.dimens.staticGrid.x5,
                        spacing = CodeTheme.dimens.grid.x1,
                    )
                }
            },
            titleAlignment = Alignment.CenterHorizontally,
            leftIcon = {
                AppBarDefaults.UpNavigation { navigator.pop() }
            },
            rightContents = {
                state.token.dataOrNull?.let {
                    if (!state.isCashReserve) {
                        AppBarDefaults.Share {
                            analytics.buttonTapped(Button.TokenShare)
                            viewModel.dispatchEvent(TokenInfoViewModel.Event.Share)
                        }
                    }
                }
            },
        )

        LaunchedEffect(Unit) {
            val source = when {
                shortFall != null -> Analytics.TokenInfoSource.Give
                fromDeeplink -> Analytics.TokenInfoSource.Deeplink
                else -> Analytics.TokenInfoSource.Wallet
            }

            analytics.openTokenInfo(
                source = source,
                mint = mint
            )
        }

        TokenInfoScreen(viewModel, shortFall)

        LaunchedEffect(Unit) {
            viewModel.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mint, shortFall))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<TokenInfoViewModel.Event.Exit>()
                .onEach { navigator.pop() }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<TokenInfoViewModel.Event.OpenScreen>()
                .map { it.screen }
                .onEach {
                    navigator.push(it)
                }.launchIn(this)
        }
    }
}
