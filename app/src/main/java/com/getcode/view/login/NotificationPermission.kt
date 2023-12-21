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
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.core.LocalCodeNavigator
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
    ) {
        val (image, caption, button, buttonSkip) = createRefs()

        Image(
            painter = painterResource(id = R.drawable.ic_notification_request),
            contentDescription = "",
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .padding(top = 50.dp)
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
                .padding(horizontal = 20.dp)
                .constrainAs(caption) {
                    top.linkTo(image.bottom)
                    bottom.linkTo(button.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
            text = stringResource(R.string.permissions_description_push),
            style = MaterialTheme.typography.body1
                .copy(textAlign = TextAlign.Center),
        )

        CodeButton(
            onClick = { notificationPermissionCheck(true) },
            text = stringResource(R.string.action_allowPushNotifications),
            buttonState = ButtonState.Filled,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .constrainAs(button) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    linkTo(button.bottom, buttonSkip.top, bias = 1.0F)
                },
        )

        CodeButton(
            modifier = Modifier
                .padding(bottom = 10.dp)
                .padding(horizontal = 20.dp)
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
            isPaddedVertical = false,
        )
    }
}