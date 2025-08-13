package com.flipcash.app.contact.verification.phone

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.contact.verification.PhoneVerificationFlow
import com.flipcash.app.contact.verification.internal.phone.PhoneEntryScreen
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.phone.CountryLocale
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.AppScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.navigation.screens.OnScreenResult
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class PhoneVerificationScreen : Screen, NamedScreen, Parcelable  {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_verifyPhoneNumber)

    @Composable
    override fun Content() {
        val codeNavigator = LocalCodeNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getStackScopedViewModel<PhoneVerificationViewModel>(key = PhoneVerificationFlow.key)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = {
                    if (!navigator.pop()) {
                        codeNavigator.pop()
                    }
                },
            )
            PhoneEntryScreen(viewModel)
        }

        BackHandler {
            if (!navigator.pop()) {
                codeNavigator.pop()
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<PhoneVerificationViewModel.Event.OpenCountrySelector>()
                .onEach {
                    navigator.push(PhoneCountryCodeScreen())
//                    navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Verification.PhoneCountry))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<PhoneVerificationViewModel.Event.OnCodeSent>()
                .onEach {
                    navigator.push(PhoneCodeScreen())
//                    navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.Verification.PhoneCode))
                }.launchIn(this)
        }
    }
}