package com.flipcash.app.balance

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.balance.internal.BalanceScreenContent
import com.flipcash.app.balance.internal.BalanceViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.RepeatOnLifecycle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class BalanceScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key:
            ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_balance)

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
            BalanceScreenContent(viewModel)

            RepeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.dispatchEvent(BalanceViewModel.Event.UpdateFeed)
            }
        }
    }
}

@Composable
fun PreloadBalance() {
    val viewModel = getActivityScopedViewModel<BalanceViewModel>()
    viewModel.dispatchEvent(BalanceViewModel.Event.UpdateFeed)
}