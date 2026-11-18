package com.flipcash.app.contact.verification.email

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.analytics.OnrampStep
import com.flipcash.app.contact.verification.onrampStepEvent
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.contact.verification.internal.email.EmailEntryScreen
import com.flipcash.app.contact.verification.internal.email.EmailVerificationViewModel
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.core.verification.VerificationStep
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun EmailVerificationContent(
    origin: EmailDeeplinkOrigin? = null,
    // New-account onboarding: the address is linked without a code, and there is no back to the
    // code screen once the phone is verified.
    onboarding: Boolean = false,
) {
    val codeNavigator = LocalCodeNavigator.current
    val flowNavigator = rememberFlowNavigator<VerificationStep, VerificationResult>()
    val viewModel = flowSharedViewModel<EmailVerificationViewModel>()
    val keyboard = rememberKeyboardController()

    LaunchedEffect(origin) {
        viewModel.dispatchEvent(EmailVerificationViewModel.Event.OnOriginSet(origin))
    }
    LaunchedEffect(onboarding) {
        viewModel.dispatchEvent(EmailVerificationViewModel.Event.OnOnboardingModeSet(onboarding))
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            // Onboarding puts its title in the step's headline instead.
            title = if (onboarding) "" else stringResource(R.string.title_connectEmailAddress),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = if (onboarding) null else {
                {
                    keyboard.hideIfVisible {
                        codeNavigator.pop()
                    }
                }
            },
        )
        EmailEntryScreen(viewModel, onboarding)
    }

    val analytics = rememberAnalytics()
    LaunchedEffect(Unit) {
        onrampStepEvent(OnrampStep.ENTER_EMAIL, onboarding)?.let(analytics::track)
    }

    BackHandler {
        if (onboarding) return@BackHandler
        keyboard.hideIfVisible {
            codeNavigator.pop()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EmailVerificationViewModel.Event.OnCodeSent>()
            .onEach {
                keyboard.hideIfVisible {
                    flowNavigator.navigateTo(VerificationStep.EmailMagicLink())
                }
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EmailVerificationViewModel.Event.OnEntrySaved>()
            .onEach {
                if (onboarding) {
                    // The keyboard stays up and the name step takes focus as it arrives.
                    flowNavigator.exitWithResult(VerificationResult.Success)
                } else {
                    keyboard.hideIfVisible {
                        flowNavigator.exitWithResult(VerificationResult.Success)
                    }
                }
            }.launchIn(this)
    }
}
