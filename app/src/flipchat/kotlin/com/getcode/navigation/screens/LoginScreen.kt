package com.getcode.navigation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.LocalAnalytics
import com.getcode.LocalSession
import com.getcode.R
import com.getcode.analytics.Action
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.TitleBar
import com.getcode.ui.utils.measured
import com.getcode.ui.utils.unboundedClickable
import com.getcode.view.login.LoginHome
import com.getcode.view.login.SeedDeepLink
import com.getcode.view.main.tip.ConnectAccountScreen
import com.getcode.view.main.tip.ConnectAccountViewModel
import com.getcode.view.main.tip.IdentityConnectionReason
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class LoginScreen(val seed: String? = null) : LoginGraph {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.action_logIn)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val analytics = LocalAnalytics.current
        val session = LocalSession.current

        if (seed != null) {
            SeedDeepLink(getViewModel(), seed)
        } else {
            // establish a new entropy identity for create account flow
            // login will replace it in [SessionManager].
            // session?.setupAsNew()

            LoginHome(
                createAccount = {
                    analytics.action(Action.CreateAccount)
//                    navigator.push(CreateAccountWithX)
                    navigator.push(LoginPhoneVerificationScreen(isNewAccount = true))
                },
                login = {
                    navigator.push(AccessKeyLoginScreen())
                }
            )
        }
    }
}


@Parcelize
data object CreateAccountWithX: LoginGraph {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getViewModel<ConnectAccountViewModel>()
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            TitleBar(
                modifier = Modifier.fillMaxWidth(),
                backButton = true,
                onBackIconClicked = { navigator.pop() }
            )
            ConnectAccountScreen(viewModel, titleAlignment = TextAlign.Center)
        }

        LaunchedEffect(viewModel) {
            viewModel.dispatchEvent(ConnectAccountViewModel.Event.OnReasonChanged(IdentityConnectionReason.Login))
        }

        AnalyticsScreenWatcher(action = Action.OpenConnectAccount)
    }
}
