package com.flipcash.app.login.accesskey

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.login.internal.AccessKeyScreen
import com.flipcash.features.login.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun AccessKeyScreen() {
    val viewModel = hiltViewModel<LoginAccessKeyViewModel>()
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_accessKey),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { navigator.pop() },
        )
        AccessKeyScreen(viewModel) { requiresIap ->
            if (requiresIap) {
                navigator.push(AppRoute.Onboarding.Purchase())
            } else {
                val target = if (viewModel.isPhoneNumberSendEnabled) {
                    AppRoute.Onboarding.ContactPermission(postCreate = true)
                } else {
                    AppRoute.Onboarding.NotificationPermission(postCreate = true)
                }

                navigator.push(
                    AppRoute.Verification(
                        origin = AppRoute.Onboarding.AccessKey,
                        includePhone = true,
                        includeEmail = false,
                        target = target,
                        fullScreen = true,
                    )
                )
            }
        }
    }
}
