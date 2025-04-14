package com.flipcash.app.login.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.features.login.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.permissions.cameraPermissionCheck

internal enum class Permission {
    Camera, Notifications
}

@Composable
internal fun PermissionScreenContent(
    permission: Permission,
    fromOnboarding: Boolean
) {
    val navigator = LocalCodeNavigator.current
    when (permission) {
        Permission.Camera -> CameraPermissionScreenContent(
            onGranted = {
                if (fromOnboarding) {
//                    analytics.action(Action.CompletedOnboarding)
                }
                navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
            },
            onNotGranted = {
                navigator.push(
                    ScreenRegistry.get(NavScreenProvider.Login.NotificationPermission(fromOnboarding))
                )
            }
        )
        Permission.Notifications -> NotificationScreenContent {
            if (fromOnboarding) {
//                analytics.action(Action.CompletedOnboarding)
            }
            navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
        }
    }
}

@Composable
internal fun CameraPermissionScreenContent(onGranted: () -> Unit, onNotGranted: () -> Unit) {
    var isResultHandled by remember { mutableStateOf(false) }
    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (!isResultHandled) {
            isResultHandled = true

            if (isGranted) {
                onGranted()
            } else {
                onNotGranted()
            }
        }
    }

    val notificationPermissionCheck =
        com.getcode.util.permissions.notificationPermissionCheck { onNotificationResult(it) }

    val onCameraResult: (Boolean) -> Unit = { isGranted ->
        if (isGranted) {
            notificationPermissionCheck(false)
        }
    }

    val cameraPermissionCheck = cameraPermissionCheck { onCameraResult(it) }

    SideEffect {
        cameraPermissionCheck(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_home_bill_image),
                contentDescription = "",
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.grid.x8)
                    .padding(top = CodeTheme.dimens.grid.x10)
                    .fillMaxHeight(0.6f)
                    .fillMaxWidth()
            )
            Text(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset),
                text = stringResource(R.string.permissions_description_camera),
                style = CodeTheme.typography.textMedium
                    .copy(textAlign = TextAlign.Center),
            )
            Spacer(Modifier.weight(1f))
            CodeButton(
                onClick = { cameraPermissionCheck(true) },
                text = stringResource(R.string.action_allowCameraAccess),
                buttonState = ButtonState.Filled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset),
            )
        }
    }
}

@Composable
internal fun NotificationScreenContent(onGranted: () -> Unit) {
    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (isGranted) {
            onGranted()
        }
    }
    val notificationPermissionCheck =
        com.getcode.util.permissions.notificationPermissionCheck(onResult = {
            onNotificationResult(it)
        })

    SideEffect {
        notificationPermissionCheck(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_notification_request),
                contentDescription = "",
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.grid.x8)
                    .padding(top = CodeTheme.dimens.grid.x10)
                    .fillMaxHeight(0.6f)
                    .fillMaxWidth(),
            )

            Text(
                modifier = Modifier
                    .padding(horizontal = CodeTheme.dimens.inset),
                text = stringResource(R.string.permissions_description_push),
                style = CodeTheme.typography.textMedium
                    .copy(textAlign = TextAlign.Center),
            )

            Spacer(Modifier.weight(1f))

            CodeButton(
                onClick = { notificationPermissionCheck(true) },
                text = stringResource(R.string.action_allowPushNotifications),
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
                    onNotificationResult(true)
                },
                text = stringResource(R.string.action_notNow),
                buttonState = ButtonState.Subtle,
            )
        }
    }
}