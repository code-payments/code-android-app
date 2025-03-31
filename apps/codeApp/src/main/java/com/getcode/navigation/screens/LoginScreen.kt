package com.getcode.navigation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.analytics.Action
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.view.login.LoginHome
import com.getcode.view.login.SeedDeepLink
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

        if (seed != null) {
            SeedDeepLink(getViewModel(), seed)
        } else {
            LoginHome(
                createAccount = {
                    analytics.action(Action.CreateAccount)
                    navigator.push(LoginPhoneVerificationScreen(isNewAccount = true))
                },
                login = {
                    navigator.push(AccessKeyLoginScreen())
                }
            )
        }
    }
}