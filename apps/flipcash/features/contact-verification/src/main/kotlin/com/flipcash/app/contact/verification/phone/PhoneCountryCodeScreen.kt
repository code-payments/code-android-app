package com.flipcash.app.contact.verification.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.contact.verification.internal.phone.PhoneCountryCodeScreen
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
fun PhoneCountryCodeContent() {
    val flowNavigator = rememberFlowNavigator<VerificationStep, VerificationResult>()
    val viewModel = flowSharedViewModel<PhoneVerificationViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_verifyPhoneNumber),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { flowNavigator.back() },
        )
        PhoneCountryCodeScreen(viewModel = viewModel)
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PhoneVerificationViewModel.Event.OnCountrySelected>()
            .onEach { flowNavigator.back() }
            .launchIn(this)
    }
}
