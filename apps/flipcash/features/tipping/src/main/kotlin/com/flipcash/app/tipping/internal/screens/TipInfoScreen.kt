package com.flipcash.app.tipping.internal.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.core.tipping.TipResult
import com.flipcash.app.core.tipping.TipStep
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.navigation.flow.LocalFlowNavigator
import com.getcode.navigation.flow.PreviewFlowNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun TipInfoScreen() {
    val flowNavigator = rememberFlowNavigator<TipStep, TipResult>()
    val userManager = LocalUserManager.current
    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                endContent = { AppBarDefaults.Close { flowNavigator.exitCanceled() }})
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .navigationBarsPadding()
                    .padding(vertical = CodeTheme.dimens.grid.x3),
                text = stringResource(R.string.action_startReceivingTips),
                buttonState = ButtonState.Filled,
                onClick = {
                    flowNavigator.navigate(
                        AppRoute.UpdateUserProfile(
                            origin = AppRoute.Sheets.Tips(),
                            includeName = userManager?.profile?.displayName.isNullOrEmpty(),
                            includePhoto = false, // explicity false for now
                            target = AppRoute.Sheets.Tips(resumed = true),
                        )
                    )
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_tipping_hand_large),
                    contentDescription = null,
                )

                Text(
                    text = stringResource(R.string.title_tipIntro),
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x2)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.subtitle_tipIntro),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_TipIntro() {
    CompositionLocalProvider(
        LocalFlowNavigator provides PreviewFlowNavigator<TipStep, TipResult>(),
    ) {
        TipInfoScreen()
    }
}
