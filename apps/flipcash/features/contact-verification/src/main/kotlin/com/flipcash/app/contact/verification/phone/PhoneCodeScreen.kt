package com.flipcash.app.contact.verification.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.contact.verification.internal.phone.PhoneCodeScreen
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.core.verification.VerificationStep
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun PhoneCodeContent(
    includeEmail: Boolean,
    isInModal: Boolean = true,
) {
    val flowNavigator = rememberFlowNavigator<VerificationStep, VerificationResult>()
    val viewModel = flowSharedViewModel<PhoneVerificationViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_enterTheCode),
            isInModal = isInModal,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { flowNavigator.back() },
        )
        PhoneCodeScreen(viewModel)
    }

    val analytics = rememberAnalytics()
    LaunchedEffect(Unit) {
        analytics.onrampVerification(Analytics.OnrampVerificationStep.ConfirmPhone)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PhoneVerificationViewModel.Event.OnMaxAttemptsReached>()
            .onEach { flowNavigator.exitCanceled() }
            .launchIn(this)
    }

    LaunchedEffect(viewModel, includeEmail) {
        viewModel.eventFlow
            .filterIsInstance<PhoneVerificationViewModel.Event.OnCodeVerified>()
            .onEach {
                if (includeEmail) {
                    flowNavigator.navigateTo(VerificationStep.EmailEntry)
                } else {
                    flowNavigator.exitWithResult(VerificationResult.Success)
                }
            }
            .launchIn(this)
    }
}
