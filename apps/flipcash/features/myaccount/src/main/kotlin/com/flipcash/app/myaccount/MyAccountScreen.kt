package com.flipcash.app.myaccount

import android.os.Parcelable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.myaccount.internal.MyAccountScreen
import com.flipcash.app.myaccount.internal.MyAccountScreenViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.rememberedClickable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class MyAccountScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_myAccount)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current

        val viewModel = getViewModel<MyAccountScreenViewModel>()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = {
                    AppBarDefaults.Title(
                        modifier = Modifier.rememberedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.dispatchEvent(MyAccountScreenViewModel.Event.OnTitleClicked) },
                        text = name,
                    )
                },
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                leftIcon = { AppBarDefaults.UpNavigation { navigator.pop() } },
            )
            MyAccountScreen(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<MyAccountScreenViewModel.Event.OnAccountDeleted>()
                .onEach {
                    navigator.hide()
                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home())) }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<MyAccountScreenViewModel.Event.OnViewAccessKey>()
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Menu.MyAccount.BackupKey)) }
                .launchIn(this)
        }
    }
}