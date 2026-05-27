package com.flipcash.app.permissions.internal.notifications.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.shared.permissions.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.permissions.PermissionHandle
import com.getcode.util.resources.LocalResources

@Composable
internal fun NotificationPermissionBottomBar(
    permission: PermissionHandle,
    onSkip: () -> Unit,
) {
    val resources = LocalResources.current
    Column(
        modifier = Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = CodeTheme.dimens.grid.x3),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CodeButton(
            onClick = { permission.launch() },
            text = stringResource(R.string.action_ok),
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
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.error_title_ignoredPushPermissions),
                    message = resources.getString(R.string.error_description_ignoredPushPermissions),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_okAllow)
                        ) {
                            permission.launch()
                        },
                        BottomBarAction(
                            text = resources.getString(R.string.action_imSure),
                            style = BottomBarManager.BottomBarButtonStyle.Text
                        ) {
                            onSkip()
                        }
                    )
                )
            },
            text = stringResource(R.string.action_notNow),
            buttonState = ButtonState.Subtle,
        )
    }
}