package com.flipcash.app.pools.create

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.pools.PoolCreateFlow
import com.flipcash.app.pools.internal.create.PoolConfirmationScreen
import com.flipcash.app.pools.internal.create.PoolCreateViewModel
import com.flipcash.features.pools.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class PoolConfirmationScreen : ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_createPool)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
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
            PoolConfirmationScreen(getStackScopedViewModel<PoolCreateViewModel>(key = PoolCreateFlow.key))
        }
    }
}