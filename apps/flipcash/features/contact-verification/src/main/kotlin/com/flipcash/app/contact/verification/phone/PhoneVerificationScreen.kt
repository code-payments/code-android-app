package com.flipcash.app.contact.verification.phone

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.lifecycle.LifecycleEffectOnce
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.contact.verification.PhoneVerificationFlow
import com.flipcash.app.contact.verification.internal.phone.PhoneEntryScreen
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.features.contact.verification.R
import com.flipcash.services.analytics.AnalyticsEvent
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.rememberKeyboardController
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

    @OptIn(ExperimentalVoyagerApi::class)
    @Composable
    override fun Content() {
        val codeNavigator = LocalCodeNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getStackScopedViewModel<PhoneVerificationViewModel>(key = PhoneVerificationFlow.key)
        val keyboard = rememberKeyboardController()

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
                    keyboard.hideIfVisible {
                        codeNavigator.pop()
                    }
                },
            )
            PhoneEntryScreen(viewModel)
        }

        val analytics = LocalAnalytics.current as FlipcashAnalyticsService
        LifecycleEffectOnce {
            analytics.onrampVerification(AnalyticsEvent.OnRampVerificationEvent.EnterPhone)
        }

        BackHandler {
            keyboard.hideIfVisible {
                codeNavigator.pop()
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<PhoneVerificationViewModel.Event.OpenCountrySelector>()
                .onEach {
                    keyboard.hideIfVisible {
                        navigator.push(PhoneCountryCodeScreen())
                    }
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<PhoneVerificationViewModel.Event.OnCodeSent>()
                .onEach {
                    keyboard.hideIfVisible {
                        navigator.push(PhoneCodeScreen())
                    }
                }.launchIn(this)
        }
    }
}