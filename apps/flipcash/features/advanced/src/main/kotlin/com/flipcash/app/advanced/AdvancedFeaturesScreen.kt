package com.flipcash.app.advanced

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
import com.flipcash.app.advanced.internal.AdvancedFeaturesScreen
import com.flipcash.app.advanced.internal.AdvancedFeaturesScreenViewModel
import com.flipcash.app.bill.customization.LocalBillPlaygroundController
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class AdvancedFeaturesScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_advancedFeatures)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val billPlayground = LocalBillPlaygroundController.current
        val viewModel = getViewModel<AdvancedFeaturesScreenViewModel>()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                titleAlignment = Alignment.CenterHorizontally,
                isInModal = true,
                backButton = true,
                onBackIconClicked = { navigator.pop() }
            )

            AdvancedFeaturesScreen(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<AdvancedFeaturesScreenViewModel.Event.OpenScreen>()
                .map { ScreenRegistry.get(it.screen) }
                .onEach { navigator.push(it) }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<AdvancedFeaturesScreenViewModel.Event.OpenBillPlayground>()
                .onEach {
                    navigator.hide()
                    billPlayground.customizeFor(Token.usdf)
                }
                .launchIn(this)
        }
    }
}