package com.flipcash.app.contact.verification.email

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.contact.verification.EmailVerificationFlow
import com.flipcash.app.contact.verification.LocalVerificationFlowNavigator
import com.flipcash.app.contact.verification.PhoneVerificationFlow
import com.flipcash.app.contact.verification.VerificationFlowStep
import com.flipcash.app.contact.verification.internal.email.EmailMagicLinkScreen
import com.flipcash.app.contact.verification.internal.email.EmailVerificationViewModel
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class EmailMagicLinkScreen(
    private val email: String? = null,
    private val code: String? = null
) : Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_verifyEmailAddress)

    @Composable
    override fun Content() {
        val flowNavigator = LocalVerificationFlowNavigator.current
        val viewModel =
            getStackScopedViewModel<EmailVerificationViewModel>(EmailVerificationFlow.key)

        BackHandler {
            flowNavigator.exit()
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { flowNavigator.exit() },
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
                .onEach { flowNavigator.exit() }
                .launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<EmailVerificationViewModel.Event.OnCodeVerified>()
                .onEach { flowNavigator.continueFlowFrom(VerificationFlowStep.Email) }
                .launchIn(this)
        }
    }
}