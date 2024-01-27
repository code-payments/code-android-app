package com.getcode.view.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Composable
fun NotificationPermission(navigator: CodeNavigator = LocalCodeNavigator.current) {
    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (isGranted) {
            navigator.replaceAll(HomeScreen())
        }
    }
    val notificationPermissionCheck = notificationPermissionCheck(onResult = { onNotificationResult(it) })

    SideEffect {
        notificationPermissionCheck(false)
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        val (image, caption, button, buttonSkip) = createRefs()

        Image(
            painter = painterResource(id = R.drawable.ic_notification_request),
            contentDescription = "",
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.grid.x8)
                .padding(top = CodeTheme.dimens.grid.x10)
                .fillMaxHeight(0.6f)
                .fillMaxWidth()
                .constrainAs(image) {
                    top.linkTo(parent.top)
                    bottom.linkTo(caption.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        )

        Text(
            modifier = Modifier
                .padding(horizontal = CodeTheme.dimens.inset)
                .constrainAs(caption) {
                    top.linkTo(image.bottom)
                    bottom.linkTo(button.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            text = stringResource(R.string.permissions_description_push),
            style = CodeTheme.typography.body1
                .copy(textAlign = TextAlign.Center),
        )

        CodeButton(
            onClick = { notificationPermissionCheck(true) },
            text = stringResource(R.string.action_allowPushNotifications),
            buttonState = ButtonState.Filled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .constrainAs(button) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    linkTo(button.bottom, buttonSkip.top, bias = 1.0F)
                },
        )

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.grid.x2)
                .padding(horizontal = CodeTheme.dimens.inset)
                .constrainAs(buttonSkip) {
                    linkTo(buttonSkip.bottom, parent.bottom, bias = 1.0F)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            onClick = {
                onNotificationResult(true)
            },
            text = stringResource(R.string.action_notNow),
            buttonState = ButtonState.Subtle,
        )
    }
}