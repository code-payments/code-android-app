package com.flipcash.app.lab

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.lab.internal.LabsScreenContent
import com.flipcash.app.lab.internal.LabsScreenViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize


@Parcelize
class LabsScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_betaFlags)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                isInModal = true,
                onBackIconClicked = navigator::pop
            )

            val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()

            LabsScreenContent(viewModel)
        }
    }
}

@Parcelize
class StandaloneLabsScreen: ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_betaFlags)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                titleAlignment = Alignment.CenterHorizontally,
                isInModal = true,
                endContent = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )

            val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()

            LabsScreenContent(viewModel)
        }
    }
}

@Composable
fun PreloadLabs() {
    val viewModel = getActivityScopedViewModel<LabsScreenViewModel>()
}