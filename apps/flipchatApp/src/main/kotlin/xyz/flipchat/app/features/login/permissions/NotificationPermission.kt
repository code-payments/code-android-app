package xyz.flipchat.app.features.login.permissions

import android.os.Parcelable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.constraintlayout.compose.ConstraintLayout
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import xyz.flipchat.app.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationPermissionScreen(val fromOnboarding: Boolean = false): Screen, Parcelable {
    @Composable
    override fun Content() {
        NotificationPermission(fromOnboarding)
    }

}
@Composable
fun NotificationPermission(fromOnboarding: Boolean = false) {
    val navigator = LocalCodeNavigator.current
//    val analytics = LocalAnalytics.current
    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (isGranted) {
            if (fromOnboarding) {
//                analytics.action(Action.CompletedOnboarding)
            }
            if (navigator.lastModalItem is NotificationPermissionScreen) {
                navigator.hide()
            } else {
                navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.AppHomeScreen()))
            }
        }
    }
    val notificationPermissionCheck =
        com.getcode.util.permissions.notificationPermissionCheck(onResult = {
            onNotificationResult(it)
        })

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
            text = stringResource(R.string.permissions_description_push_messages),
            style = CodeTheme.typography.textMedium
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