package com.flipcash.app.directsend.internal.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.directsend.R
import com.flipcash.shared.chat.ui.AnimatedConversationPreview
import com.getcode.navigation.flow.LocalFlowNavigator
import com.getcode.navigation.flow.PreviewFlowNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold


@Composable
internal fun PhoneGateLandingScreen() {
    val flowNavigator = rememberFlowNavigator<SendStep, SendResult>()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = "",
                endContent = {
                    AppBarDefaults.Close { flowNavigator.exitCanceled() }
                },
            )
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3)
                    .navigationBarsPadding(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_next),
                onClick = {
                    flowNavigator.navigate(
                        AppRoute.Verification(
                            origin = AppRoute.Sheets.Send(),
                            includePhone = true,
                            includeEmail = false,
                            linkForPayment = true,
                            target = AppRoute.Sheets.Send(resumed = true),
                        )
                    )
                },
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedConversationPreview(animate = false)

                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x8),
                    text = stringResource(R.string.title_sendFeatureIntro),
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = CodeTheme.dimens.grid.x2)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.subtitle_sendFeatureIntro),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewPhoneGateScreen() {
    CompositionLocalProvider(
        LocalFlowNavigator provides PreviewFlowNavigator<SendStep, SendResult>(),
        LocalExchange provides ExchangeStub(context = LocalContext.current),
    ) {
        PhoneGateLandingScreen()
    }
}