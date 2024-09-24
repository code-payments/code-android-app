package com.getcode.view.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.AppHomeScreen
import com.getcode.LocalAnalytics
import com.getcode.R
import com.getcode.analytics.Action
import com.getcode.navigation.screens.CodeLoginPermission
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.PermissionRequestScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton

@Composable
fun CameraPermission(navigator: CodeNavigator = LocalCodeNavigator.current, fromOnboarding: Boolean = false) {
    var isResultHandled by remember { mutableStateOf(false) }
    val analytics = LocalAnalytics.current
    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (!isResultHandled) {
            isResultHandled = true

            if (isGranted) {
                if (fromOnboarding) {
                    analytics.action(Action.CompletedOnboarding)
                }
                navigator.replaceAll(AppHomeScreen())
            } else {
                navigator.push(PermissionRequestScreen(CodeLoginPermission.Notifications, fromOnboarding))
            }
        }
    }

    val notificationPermissionCheck = notificationPermissionCheck { onNotificationResult(it) }

    val onCameraResult: (Boolean) -> Unit = { isGranted ->
        if (isGranted) {
            notificationPermissionCheck(false)
        }
    }

    val cameraPermissionCheck = cameraPermissionCheck { onCameraResult(it) }

    SideEffect {
        cameraPermissionCheck(false)
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = CodeTheme.dimens.grid.x4)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        val (image, caption, button) = createRefs()

        Image(
            painter = painterResource(R.drawable.ic_home_bill_image),
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
            text = stringResource(R.string.permissions_description_camera),
            style = CodeTheme.typography.textMedium
                .copy(textAlign = TextAlign.Center),
        )

        CodeButton(
            onClick = { cameraPermissionCheck(true) },
            text = stringResource(R.string.action_allowCameraAccess),
            buttonState = ButtonState.Filled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .constrainAs(button) {
                    linkTo(button.bottom, parent.bottom, bias = 1.0F)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
        )
    }
}