package com.flipcash.app.onramp

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
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.onramp.internal.OnRampTestScreenContent
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.features.onramp.R
import com.getcode.navigation.core.LocalCodeNavigator
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
class OnRampTestScreen : ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_onramp)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current

        val viewModel = getViewModel<OnRampViewModel>()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                titleAlignment = Alignment.CenterHorizontally,
                isInModal = true,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
            )
            OnRampTestScreenContent(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<OnRampViewModel.Event.OnOrderPlaced>()
                .map { it.paymentLink }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.PaymentWebview(it)))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<OnRampViewModel.Event.OnPhoneVerificationRequired>()
                .map { it.url }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.PhoneVerification(it)))
                }.launchIn(this)
        }
    }
}