package com.flipcash.app.give

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.give.internal.GiveScreenContent
import com.flipcash.app.give.internal.GiveScreenViewModel
import com.flipcash.app.session.LocalSessionController
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class GiveScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_give)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val session = LocalSessionController.currentOrThrow

        val viewModel = getViewModel<GiveScreenViewModel>()

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<GiveScreenViewModel.Event.PresentBill>()
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
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                endContent = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )
            GiveScreenContent(viewModel)
        }
    }
}