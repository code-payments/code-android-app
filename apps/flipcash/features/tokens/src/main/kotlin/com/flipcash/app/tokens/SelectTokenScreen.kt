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
import com.flipcash.app.core.AppRoute
import com.flipcash.app.tokens.internal.SelectTokenScreen
import com.flipcash.features.tokens.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class SelectTokenScreen : ModalScreen, NamedScreen, Parcelable {

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
                titleAlignment = Alignment.CenterHorizontally,
                endContent = { AppBarDefaults.Close { navigator.hide() } },
            )
            val viewModel = getViewModel<SelectTokenViewModel>()
            SelectTokenScreen(viewModel)

            LaunchedEffect(viewModel) {
                viewModel.dispatchEvent(SelectTokenViewModel.Event.OnPurposeChanged(TokenPurpose.Send))
            }


            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<SelectTokenViewModel.Event.OnTokenSelected>()
                    .map { it.token }
                    .onEach { token ->
                        navigator.push(
                            ScreenRegistry.get(AppRoute.Main.Give(token.address))
                        )
                    }.launchIn(this)
            }
        }
    }
}