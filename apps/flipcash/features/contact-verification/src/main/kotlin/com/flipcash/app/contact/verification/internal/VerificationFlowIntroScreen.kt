package com.flipcash.app.contact.verification.internal

import android.os.Parcelable
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.contact.verification.LocalVerificationFlowNavigator
import com.flipcash.app.contact.verification.VerificationFlowStep
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.contact.verification.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class VerificationFlowIntroScreen(
    private val isForOnRamp: Boolean = true,
) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val codeNavigator = LocalCodeNavigator.current
        val flowNavigator = LocalVerificationFlowNavigator.current
        val keyboard = rememberKeyboardController()

        VerificationFlowIntroScreenContent(
            isForOnRamp = isForOnRamp,
            onClick = { flowNavigator.continueFlowFrom(VerificationFlowStep.Intro) },
        )

        BackHandler {
            keyboard.hideIfVisible {
                codeNavigator.pop()
            }
        }
    }
}

@Composable
private fun VerificationFlowIntroScreenContent(
    isForOnRamp: Boolean,
    onClick: () -> Unit,
) {
    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
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
    FlipcashDesignSystem {
        Box(modifier = Modifier.fillMaxSize().background(CodeTheme.colors.background)) {
            VerificationFlowIntroScreenContent(
                isForOnRamp = true,
                onClick = { }
            )
        }
    }
}