package com.flipcash.app.contact.verification.email

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
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.contact.verification.EmailVerificationFlow
import com.flipcash.app.contact.verification.internal.email.EmailEntryScreen
import com.flipcash.app.contact.verification.internal.email.EmailVerificationViewModel
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.screens.AppScreen
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class EmailVerificationScreen : AppScreen, Parcelable  {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "email_verification_screen"

    @OptIn(ExperimentalVoyagerApi::class)
    @Composable
    override fun ScreenContent() {
        val codeNavigator = LocalCodeNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getStackScopedViewModel<EmailVerificationViewModel>(EmailVerificationFlow.key)
        val keyboard = rememberKeyboardController()
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = stringResource(R.string.title_verifyEmailAddress),
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = {
                    keyboard.hideIfVisible {
                        codeNavigator.pop()
                    }
                },
            )
            EmailEntryScreen(viewModel)
        }

        val analytics = rememberAnalytics()
        LifecycleEffectOnce {
            analytics.onrampVerification(Analytics.OnrampVerificationStep.EnterEmail)
        }

        BackHandler {
            keyboard.hideIfVisible {
                codeNavigator.pop()
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<EmailVerificationViewModel.Event.OnCodeSent>()
                .onEach {
                    keyboard.hideIfVisible {
                        navigator.push(EmailMagicLinkScreen())
                    }
                }.launchIn(this)
        }
    }
}