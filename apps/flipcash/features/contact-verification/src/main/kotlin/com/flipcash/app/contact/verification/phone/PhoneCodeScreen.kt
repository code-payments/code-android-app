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
import com.flipcash.app.analytics.AnalyticsEvent
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.contact.verification.PhoneVerificationFlow
import com.flipcash.app.contact.verification.VerificationFlowStep
import com.flipcash.app.contact.verification.internal.phone.PhoneCodeScreen
import com.flipcash.app.contact.verification.internal.phone.PhoneVerificationViewModel
import com.flipcash.app.navigation.FlowNavigator
import com.flipcash.app.navigation.LocalFlowNavigator
import com.flipcash.features.contact.verification.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class PhoneCodeScreen: Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_enterTheCode)

    @OptIn(ExperimentalVoyagerApi::class)
    @Composable
    override fun Content() {
        val flowNavigator = LocalFlowNavigator.current as FlowNavigator<VerificationFlowStep>
        val viewModel = getStackScopedViewModel<PhoneVerificationViewModel>(key = PhoneVerificationFlow.key)

        BackHandler { flowNavigator.exit(false) }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { flowNavigator.exit(false) },
            )
            PhoneCodeScreen(viewModel)
        }

        val analytics = rememberAnalytics()
        LifecycleEffectOnce {
            analytics.onrampVerification(AnalyticsEvent.OnRampVerificationEvent.ConfirmPhone)
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
}