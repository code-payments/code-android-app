package com.flipcash.app.contact.verification.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.contact.verification.internal.phone.PhoneEntryScreen
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.core.verification.VerificationStep
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
fun PhoneVerificationContent() {
    val codeNavigator = LocalCodeNavigator.current
    val flowNavigator = rememberFlowNavigator<VerificationStep, VerificationResult>()
    val viewModel = flowSharedViewModel<PhoneVerificationViewModel>()
    val keyboard = rememberKeyboardController()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_verifyPhoneNumber),
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

    val analytics = rememberAnalytics()
    LaunchedEffect(Unit) {
        analytics.onrampVerification(Analytics.OnrampVerificationStep.EnterPhone)
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
                    flowNavigator.navigateTo(VerificationStep.PhoneCountryCode)
                }
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PhoneVerificationViewModel.Event.OnCodeSent>()
            .onEach {
                keyboard.hideIfVisible {
                    flowNavigator.navigateTo(VerificationStep.PhoneCode)
                }
            }.launchIn(this)
    }
}
