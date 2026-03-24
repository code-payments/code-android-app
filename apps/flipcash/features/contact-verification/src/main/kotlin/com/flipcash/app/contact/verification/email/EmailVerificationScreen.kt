package com.flipcash.app.contact.verification.email

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
import com.flipcash.app.contact.verification.internal.email.EmailEntryScreen
import com.flipcash.app.contact.verification.internal.email.EmailVerificationViewModel
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.core.LocalCodeNavigator
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun EmailVerificationContent(
    onPushMagicLink: () -> Unit = {},
) {
    val codeNavigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<EmailVerificationViewModel>()
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
    LaunchedEffect(Unit) {
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
                    onPushMagicLink()
                }
            }.launchIn(this)
    }
}
