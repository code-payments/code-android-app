package com.flipcash.app.permissions.internal.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.analytics.StubFlipcashAnalytics
import com.flipcash.app.permissions.internal.notifications.components.AnimatedNotificationPreview
import com.flipcash.app.permissions.internal.notifications.components.NotificationPermissionBottomBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.shared.permissions.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.permissions.PermissionHandle
import com.getcode.util.permissions.ProvideTestPermissions
import com.getcode.util.permissions.rememberNotificationPermission


@Composable
fun NotificationScreenContent(
    permissionState: PermissionHandle,
    onSkip: () -> Unit,
) {
    CodeScaffold(
        bottomBar = {
            NotificationPermissionBottomBar(
                permission = permissionState,
                onSkip = onSkip,
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedNotificationPreview(animate = false)

                Text(
                    text = stringResource(R.string.permissions_title_push),
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = CodeTheme.dimens.grid.x2)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.permissions_description_push),
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
private fun PreviewNotificationPermissionScreen() {
    FlipcashPreview(showBackground = true) {
        CompositionLocalProvider(LocalAnalytics provides StubFlipcashAnalytics()) {
            ProvideTestPermissions(granted = emptySet()) {
                val state = rememberNotificationPermission()
                NotificationScreenContent(state) { }
            }
        }
    }
}