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
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.tokens.internal.TokenInfoScreen
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TokenInfoScreen(private val mint: Mint) : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val viewModel = getViewModel<TokenInfoViewModel>()
            val state by viewModel.stateFlow.collectAsStateWithLifecycle()
            AppBarWithTitle(
                isInModal = true,
                title = {
                    state.token?.let { token ->
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
                    if (state.isCashReserve && state.cashReservesEnabled) {
                        AppBarDefaults.Share {
                            viewModel.dispatchEvent(TokenInfoViewModel.Event.Share)
                        }
                    }
                },
            )

            TokenInfoScreen(viewModel)

            LaunchedEffect(viewModel, mint) {
                viewModel.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mint))
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
        }
    }

}