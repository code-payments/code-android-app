package com.flipcash.app.balance

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.balance.internal.BalanceScreen
import com.flipcash.app.balance.internal.BalanceViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.RepeatOnLifecycle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class BalanceScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key:
            ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_wallet)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                endContent = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )

            val viewModel = getActivityScopedViewModel<BalanceViewModel>()
            BalanceScreen(viewModel)

            RepeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.dispatchEvent(BalanceViewModel.Event.ResetSelections)
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<BalanceViewModel.Event.OpenCurrencySelection>()
                    .onEach {
                        navigator.push(
                            ScreenRegistry.get(
                                AppRoute.Main.CurrencySelection(
                                    CurrencySelectionKind.Balance
                                )
                            )
                        )
                    }.launchIn(this)
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<BalanceViewModel.Event.OpenScreen>()
                    .map { ScreenRegistry.get(it.screen) }
                    .onEach { navigator.push(it) }
                    .launchIn(this)
            }
        }
    }
}

@Composable
fun PreloadBalance() {
    val viewModel = getActivityScopedViewModel<BalanceViewModel>()
}