package com.flipcash.app.contact.verification.email

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.contact.verification.VerificationFlowStep
import com.flipcash.app.contact.verification.internal.email.EmailMagicLinkScreen
import com.flipcash.app.contact.verification.internal.email.EmailVerificationViewModel
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.navigation.FlowNavigator
import com.flipcash.app.navigation.LocalFlowNavigator
import com.flipcash.features.contact.verification.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun EmailMagicLinkContent(
    email: String? = null,
    code: String? = null,
) {
    val flowNavigator = LocalFlowNavigator.current as FlowNavigator<VerificationFlowStep>
    val viewModel = hiltViewModel<EmailVerificationViewModel>()

    BackHandler {
        flowNavigator.exit(false)
    }

    val analytics = rememberAnalytics()
    LaunchedEffect(Unit) {
        analytics.onrampVerification(Analytics.OnrampVerificationStep.ConfirmEmail)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_verifyEmailAddress),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { flowNavigator.exit(false) },
        )
        EmailMagicLinkScreen(viewModel)
    }

    LaunchedEffect(email, code) {
        viewModel.dispatchEvent(EmailVerificationViewModel.Event.OnDataProvided(email, code))
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EmailVerificationViewModel.Event.OpenMailApp>()
            .onEach {
                context.startActivity(IntentUtils.emailApp())
            }.launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EmailVerificationViewModel.Event.OnMaxAttemptsReached>()
            .onEach { flowNavigator.exit(false) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EmailVerificationViewModel.Event.Exit>()
            .onEach { flowNavigator.exit(false) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<EmailVerificationViewModel.Event.OnCodeVerified>()
            .onEach { flowNavigator.continueFlowFrom(VerificationFlowStep.Email) }
            .launchIn(this)
    }
}
