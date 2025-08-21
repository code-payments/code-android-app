package com.flipcash.app.contact.verification.phone

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.contact.verification.PhoneVerificationFlow
import com.flipcash.app.contact.verification.internal.phone.PhoneCountryCodeScreen
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class PhoneCountryCodeScreen: Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_verifyPhoneNumber)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getStackScopedViewModel<PhoneVerificationViewModel>(PhoneVerificationFlow.key)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
            )
            PhoneCountryCodeScreen(viewModel = viewModel)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<PhoneVerificationViewModel.Event.OnCountrySelected>()
                .onEach { navigator.pop() }
                .launchIn(this)
        }
    }
}