package com.flipcash.app.withdrawal

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.withdrawal.internal.destination.WithdrawalDestinationScreen
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class WithdrawalDestinationScreen: ModalScreen, NamedScreen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_withdraw)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
            )
            WithdrawalDestinationScreen(getStackScopedViewModel<WithdrawalViewModel>(key = WithdrawalFlow.key))
        }
    }

}