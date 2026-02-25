package com.flipcash.app.tokens

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.LifecycleEffectOnce
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.analytics.AnalyticsEvent
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.onramp.LocalExternalWalletState
import com.flipcash.app.onramp.OnRampFlowTracker
import com.flipcash.app.tokens.internal.TokenInfoScreen
import com.flipcash.app.tokens.ui.TokenInfoViewModel
import com.flipcash.features.tokens.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ModalScreen
import com.getcode.navigation.screens.ReturnResultScreen
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
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TokenInfoScreen(
    private val mint: Mint,
    private val forNeededFunds: Boolean,
    private val fromDeeplink: Boolean,
) : ReturnResultScreen(), ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "token_info_screen"

    @OptIn(ExperimentalVoyagerApi::class)
    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val externalWalletOnRamp = LocalExternalWalletState.current

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val analytics = rememberAnalytics()
            val viewModel = getViewModel<TokenInfoViewModel>()
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            AppBarWithTitle(
                isInModal = true,
                title = {
                    state.token.dataOrNull?.let { token ->
                        if (state.isCashReserve && state.cashReservesEnabled) {
                            AppBarDefaults.Title(text = stringResource(R.string.title_cashReserves))
                        } else {
                            TokenIconWithName(
                                token = token,
                                imageSize = CodeTheme.dimens.staticGrid.x5,
                                spacing = CodeTheme.dimens.grid.x1,
                            )
                        }
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

            LifecycleEffectOnce {
                val event = when {
                    forNeededFunds -> AnalyticsEvent.OpenTokenInfoEvent.Give
                    fromDeeplink -> AnalyticsEvent.OpenTokenInfoEvent.Deeplink
                    else -> AnalyticsEvent.OpenTokenInfoEvent.Wallet
                }

                analytics.openTokenInfo(
                    from = event,
                    mint = mint
                )
            }

            TokenInfoScreen(viewModel, forNeededFunds)

            LaunchedEffect(Unit) {
                viewModel.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mint, forNeededFunds))
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
                        navigator.push(ScreenRegistry.get(it))
                    }.launchIn(this)
            }

            val animationScale by rememberAnimationScale()
            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<TokenInfoViewModel.Event.ConnectPhantomWallet>()
                    .onEach { delay(300.scaled(animationScale)) }
                    .onEach {
                        externalWalletOnRamp.start(OnRampFlowTracker.source, OnRampProvider.Phantom)
                    }.launchIn(this)
            }
        }
    }

}