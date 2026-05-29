package com.flipcash.app.permissions.internal.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.StubFlipcashAnalytics
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.permissions.internal.notifications.components.AnimatedSwitchPreview
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.shared.permissions.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
fun NotificationRationalePermissionContent(
    permanentlyDenied: Boolean = false,
    onComplete: () -> Unit,
) {
    val analytics = rememberAnalytics()
    val context = LocalContext.current
    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CodeButton(
                    onClick = { context.launchAppSettings() },
                    text = stringResource(R.string.action_openSettings),
                    buttonState = ButtonState.Filled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                )

                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    onClick = {
                        analytics.action(Button.SkipPush)
                        onComplete()
                    },
                    text = if (permanentlyDenied) {
                        stringResource(R.string.action_notNow)
                    } else {
                        stringResource(R.string.action_imSure)
                    },
                    buttonState = ButtonState.Subtle,
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedSwitchPreview(animate = false)

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        CodeTheme.dimens.grid.x2,
                        Alignment.Bottom
                    ),
                ) {
                    Text(
                        modifier = Modifier,
                        text = if (permanentlyDenied) {
                            stringResource(R.string.permissions_title_push)
                        } else {
                            stringResource(R.string.permissions_title_push_rationale)
                        },
                        style = CodeTheme.typography.displaySmall,
                        color = CodeTheme.colors.textMain,
                    )

                    Text(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(horizontal = CodeTheme.dimens.inset),
                        text = if (permanentlyDenied) {
                            stringResource(R.string.permissions_description_push)
                        } else {
                            stringResource(R.string.permissions_description_push_rationale)
                        },
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    BackHandler {
        // prevent dispatching back
    }
}

@Composable
@Preview
private fun PreviewNotificationRationalePermissionScreen() {
    FlipcashPreview(showBackground = true) {
        CompositionLocalProvider(LocalAnalytics provides StubFlipcashAnalytics()) {
            NotificationRationalePermissionContent(onComplete = {})
        }
    }
}