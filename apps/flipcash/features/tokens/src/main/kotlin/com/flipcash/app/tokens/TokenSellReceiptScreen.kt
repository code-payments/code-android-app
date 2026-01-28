package com.flipcash.app.tokens

import android.Manifest
import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.tokens.internal.TokenSellReceiptScreen
import com.flipcash.features.tokens.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.notificationPermissionCheck
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TokenSellReceiptScreen: ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val context = LocalContext.current
        val navigator = LocalCodeNavigator.current
        val permissions = LocalPermissionChecker.current
        val composeScope = rememberCoroutineScope()

        val close = {
            navigator.popUntil { it is TokenInfoScreen }
        }
        val onNotificationResult: (Boolean) -> Unit = { isGranted ->
            composeScope.launch { close() }
        }

        val viewModel = getStackScopedViewModel<BuySellSwapTokenViewModel>(BuySellFlow.key)
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                title = stringResource(R.string.title_sellToken, state.tokenName),
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = {
                    if (state.sellProgress.loading) {
                        // swallow
                    } else {
                        navigator.pop()
                    }
                }
            )

            TokenSellReceiptScreen(viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<BuySellSwapTokenViewModel.Event.OnSellSubmitted>()
                .map { it.swapId }
                .onEach { swapId ->
                    navigator.push(ScreenRegistry.get(AppRoute.Token.TxProcessing(swapId)))
                }.launchIn(this)
        }
    }
}