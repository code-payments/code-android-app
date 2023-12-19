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
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.navigation.CodeLoginPermission
import com.getcode.navigation.CodeNavigator
import com.getcode.navigation.HomeScreen
import com.getcode.navigation.LocalCodeNavigator
import com.getcode.navigation.PermissionRequestScreen
import com.getcode.view.LoginSections
import com.getcode.view.MainSections
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Composable
fun CameraPermission(navigator: CodeNavigator = LocalCodeNavigator.current) {
    var isResultHandled by remember { mutableStateOf(false) }
    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        if (!isResultHandled) {
            isResultHandled = true

            if (isGranted) {
                navigator.replaceAll(HomeScreen())
            } else {
                navigator.push(PermissionRequestScreen(CodeLoginPermission.Notifications))
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
    ) {
        val (image, caption, button) = createRefs()

        Image(
            painter = painterResource(R.drawable.ic_home_bill_image),
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
            text = stringResource(R.string.permissions_description_camera),
            style = MaterialTheme.typography.body1
                .copy(textAlign = TextAlign.Center),
        )

        CodeButton(
            onClick = { cameraPermissionCheck(true) },
            text = stringResource(R.string.action_allowCameraAccess),
            buttonState = ButtonState.Filled,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .constrainAs(button) {
                    linkTo(button.bottom, parent.bottom, bias = 1.0F)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
        )
    }
}