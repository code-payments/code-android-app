package com.flipcash.app.cash

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.cash.internal.CashScreenViewModel
import com.flipcash.app.cash.internal.GiveScreenContent
import com.flipcash.app.session.LocalSessionController
import com.flipcash.features.cash.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class CashScreen(
    private val selectedMint: Mint
): ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val session = LocalSessionController.currentOrThrow

        val viewModel = getViewModel<CashScreenViewModel>()

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
                title = stringResource(R.string.title_enterAmount),
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
            )
            GiveScreenContent(viewModel)
        }

        LaunchedEffect(viewModel, selectedMint) {
            viewModel.dispatchEvent(CashScreenViewModel.Event.OnTokenSelected(selectedMint))
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