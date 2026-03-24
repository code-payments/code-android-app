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
import com.flipcash.app.contact.verification.VerificationFlowStep
import com.flipcash.app.contact.verification.internal.phone.PhoneCodeScreen
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.app.navigation.FlowNavigator
import com.flipcash.app.navigation.LocalFlowNavigator
import com.flipcash.features.contact.verification.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun PhoneCodeContent() {
    val flowNavigator = LocalFlowNavigator.current as FlowNavigator<VerificationFlowStep>
    val viewModel = hiltViewModel<PhoneVerificationViewModel>()

    BackHandler { flowNavigator.exit(false) }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_enterTheCode),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { flowNavigator.exit(false) },
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
            .onEach { flowNavigator.exit(false) }
            .launchIn(this)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PhoneVerificationViewModel.Event.OnCodeVerified>()
            .onEach { flowNavigator.continueFlowFrom(VerificationFlowStep.Phone) }
            .launchIn(this)
    }
}
