package com.flipcash.app.onramp

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.screens.OnRampAmountScreen
import com.flipcash.features.onramp.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class OnRampAmountScreen : ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_amountToAdd)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getStackScopedViewModel<OnRampViewModel>(key = OnRampFlowTracker.key)
        var paymentLink by rememberSaveable { mutableStateOf<String?>(null) }
        Box {
            paymentLink?.let {
                CoinbaseOnRampWebview(
                    paymentLinkUrl = it,
                    onPaymentSuccess = {
                        paymentLink = null
                        viewModel.dispatchEvent(OnRampViewModel.Event.OnPaymentSuccess)
                    },
                    onPaymentFailure = {
                        paymentLink = null
                        viewModel.dispatchEvent(OnRampViewModel.Event.OnPaymentError(it))
                    }
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                AppBarWithTitle(
                    title = name,
                    isInModal = true,
                    backButton = true,
                    onBackIconClicked = { navigator.pop() },
                    titleAlignment = Alignment.CenterHorizontally,
                )
                OnRampAmountScreen(viewModel)
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<OnRampViewModel.Event.OnPaymentLinkGenerated>()
                .map { it.url }
                .onEach {

                }.launchIn(this)
        }
    }
}