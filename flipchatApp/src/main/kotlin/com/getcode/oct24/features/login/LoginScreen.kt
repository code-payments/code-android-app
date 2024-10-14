package com.flipchat.features.login

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class LoginScreen(val seed: String? = null) : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

//    override val name: String
//        @Composable get() = stringResource(id = R.string.action_logIn)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
//        val analytics = LocalAnalytics.current
//        val session = LocalSession.current
//
//        if (seed != null) {
//            SeedDeepLink(getViewModel(), seed)
//        } else {
            // establish a new entropy identity for create account flow
            // login will replace it in [SessionManager].
            // session?.setupAsNew()

            LoginHome(
                createAccount = {
//                    analytics.action(Action.CreateAccount)
//                    navigator.push(CreateAccountWithX)
//                    navigator.push(LoginPhoneVerificationScreen(isNewAccount = true))
                },
                login = {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Login.SeedInput))
                }
            )
//        }
    }
}

//
//@Parcelize
//data object CreateAccountWithX: Screen, Parcelable {
//
//    @IgnoredOnParcel
//    override val key: ScreenKey = uniqueScreenKey
//
//    @Composable
//    override fun Content() {
//        val navigator = LocalCodeNavigator.current
//        val viewModel = getViewModel<ConnectAccountViewModel>()
//        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
//            TitleBar(
//                modifier = Modifier.fillMaxWidth(),
//                backButton = true,
//                onBackIconClicked = { navigator.pop() }
//            )
//            ConnectAccountScreen(viewModel, titleAlignment = TextAlign.Center)
//        }
//
//        LaunchedEffect(viewModel) {
//            viewModel.dispatchEvent(ConnectAccountViewModel.Event.OnReasonChanged(IdentityConnectionReason.Login))
//        }
//
//        AnalyticsScreenWatcher(action = Action.OpenConnectAccount)
//    }
//}
