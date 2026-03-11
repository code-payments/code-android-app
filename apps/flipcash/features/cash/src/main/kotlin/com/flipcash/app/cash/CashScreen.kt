package com.flipcash.app.cash

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.cash.internal.CashScreenViewModel
import com.flipcash.app.cash.internal.GiveScreenContent
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.session.LocalSessionController
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ModalScreen
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class CashScreen(
    private val selectedMint: Mint?,
    private val fromTokenInfo: Boolean,
) : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    companion object {
        const val TEST_TAG = "cash_screen"
    }

    @IgnoredOnParcel
    override val testTag = TEST_TAG

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val session = LocalSessionController.currentOrThrow

        val viewModel = getViewModel<CashScreenViewModel>()
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<CashScreenViewModel.Event.PresentBill>()
                .onEach {
                    session.showBill(it.bill)
                    navigator.hide()
                }
                .launchIn(this)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                title = {
                    TokenSelectionPill(state.token?.token) {
                        navigator.push(
                            ScreenRegistry.get(
                                AppRoute.Sheets.TokenSelection(TokenPurpose.Select)
                            )
                        )
                    }
                },
                leftIcon = {
                    if (fromTokenInfo) {
                        AppBarDefaults.UpNavigation { navigator.pop() }
                    }
                },
                rightContents = {
                    if (!fromTokenInfo) {
                        AppBarDefaults.Close { navigator.hide() }
                    }
                }
            )
            GiveScreenContent(viewModel)
        }

        LaunchedEffect(viewModel, selectedMint) {
            selectedMint?.let {
                viewModel.dispatchEvent(CashScreenViewModel.Event.OnTokenSelected(it))
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<CashScreenViewModel.Event.OpenScreen>()
                .map { ScreenRegistry.get(it.screen) }
                .onEach { navigator.push(it) }
                .launchIn(this)
        }
    }
}