package com.flipcash.app.tokens

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
import com.flipcash.app.core.AppRoute.Menu.Deposit
import com.flipcash.app.core.AppRoute.Transfers.Withdrawal.Amount
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.tokens.internal.SelectTokenScreen
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TokenSelectScreen(private val purpose: TokenPurpose) : ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_selectCurrency)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                title = name,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
                titleAlignment = Alignment.CenterHorizontally,
            )
            val viewModel = getViewModel<SelectTokenViewModel>()
            SelectTokenScreen(viewModel)

            LaunchedEffect(viewModel) {
                viewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(purpose))
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<SelectTokenViewModel.Event.OpenScreen>()
                    .map { ScreenRegistry.get(it.route) }
                    .onEach { navigator.push(it) }
                    .launchIn(this)
            }

            // handle the cases where we are inserted in a flow to select a token
            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
                    .filter { it.fromUser }
                    .map { it.mint }
                    .onEach { token ->
                        when (purpose) {
                            TokenPurpose.Balance -> Unit
                            TokenPurpose.Select -> Unit
                            TokenPurpose.Withdraw -> {
                                navigator.push(
                                    ScreenRegistry.get(Amount(token))
                                )
                            }
                            TokenPurpose.Deposit -> {
                                navigator.push(
                                    ScreenRegistry.get(Deposit(token))
                                )
                            }
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
    }
}