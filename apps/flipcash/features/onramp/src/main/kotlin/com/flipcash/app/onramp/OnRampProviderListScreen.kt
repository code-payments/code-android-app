package com.flipcash.app.onramp

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
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
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.onramp.internal.OnRampViewModel
import com.flipcash.app.onramp.internal.PhantomDeeplinkState
import com.flipcash.app.onramp.internal.data.OnRampProviderDestination
import com.flipcash.app.onramp.internal.screens.OnRampProviderListScreen
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
class OnRampProviderListScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_addCash)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current

        val viewModel = getStackScopedViewModel<OnRampViewModel>(key = OnRampFlowTracker.key)

        val phantomDepositState = LocalPhantomDepositState.current

        Box {
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
                OnRampProviderListScreen(viewModel)
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<OnRampViewModel.Event.OnProviderSelected>()
                .map { it.item.destination }
                .filterIsInstance<OnRampProviderDestination.Screen>()
                .map { it.screen }
                .onEach { destination ->
                    navigator.push(ScreenRegistry.get(destination))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<OnRampViewModel.Event.OnProviderSelected>()
                .map { it.item.destination }
                .filterIsInstance<OnRampProviderDestination.PhantomConnection>()
                .onEach {
                    if (phantomDepositState.deeplinkState == PhantomDeeplinkState.CONNECTED) {
                        // we are connected, so we can move forward
                        navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.Amount))
                    } else {
                        phantomDepositState.setOrigin(OnRampFlowTracker.source)
                        phantomDepositState.start()
                    }
                }
                .launchIn(this)
        }
    }
}