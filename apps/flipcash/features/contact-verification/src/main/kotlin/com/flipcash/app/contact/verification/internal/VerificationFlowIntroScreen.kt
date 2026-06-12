package com.flipcash.app.contact.verification.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.core.verification.VerificationStep
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.LocalOuterCodeNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
fun VerificationFlowIntroContent(
    isForOnRamp: Boolean = true,
) {
    val flowNavigator = rememberFlowNavigator<VerificationStep, VerificationResult>()

    VerificationFlowIntroScreenContent(
        isForOnRamp = isForOnRamp,
        onClick = { flowNavigator.navigateTo(VerificationStep.PhoneEntry) },
    )

    val analytics = rememberAnalytics()
    LaunchedEffect(Unit) {
        analytics.onrampVerification(Analytics.OnrampVerificationStep.ShowInfo)
    }
}

@Composable
private fun VerificationFlowIntroScreenContent(
    isForOnRamp: Boolean,
    onClick: () -> Unit,
) {
    val navigator = LocalOuterCodeNavigator.current
    val isSheetRoot = remember { navigator.backStack.size <= 1 }
    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        topBar = {
            if (isSheetRoot) {
                AppBarWithTitle(
                    isInModal = true,
                    endContent = {
                        AppBarDefaults.Close { navigator.hide() }
                    },
                )
            } else {
                AppBarWithTitle(
                    isInModal = true,
                    backButton = true,
                    onBackIconClicked = { navigator.pop() },
                )
            }
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x3)
                    .imePadding(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_next),
            ) { onClick() }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.ic_contact_method_verification),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.inset),
                    text = stringResource(R.string.title_verificationFlow),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )

                if (isForOnRamp) {
                    Text(
                        modifier = Modifier
                            .padding(top = CodeTheme.dimens.grid.x3)
                            .padding(horizontal = CodeTheme.dimens.inset),
                        text = stringResource(R.string.subtitle_verificationFlowForOnramp),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Preview
@Composable
private fun Preview_FlowIntro() {
    FlipcashPreview {
        Box(modifier = Modifier.fillMaxSize().background(CodeTheme.colors.background)) {
            VerificationFlowIntroScreenContent(
                isForOnRamp = true,
                onClick = { }
            )
        }
    }
}
