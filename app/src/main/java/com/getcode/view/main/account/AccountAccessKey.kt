package com.getcode.view.main.account

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.App
import com.getcode.R
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.TopBarManager
import com.getcode.theme.BrandLight
import com.getcode.util.IntentUtils
import com.getcode.view.components.*

@Composable
fun AccountAccessKey(navController: NavController) {
    val viewModel = hiltViewModel<AccountAccessKeyViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()

    val context = LocalContext.current
    var isExportSeedRequested by remember { mutableStateOf(false) }
    var isStoragePermissionGranted by remember { mutableStateOf(false) }
    val isAccessKeyVisible = remember { MutableTransitionState(false) }

    val onPermissionResult = { isSuccess: Boolean ->
        isStoragePermissionGranted = isSuccess

        if (!isStoragePermissionGranted) {
            TopBarManager.showMessage(
                TopBarManager.TopBarMessage(
                    title = App.getInstance().getString(R.string.error_title_failedToSave),
                    message = App.getInstance().getString(R.string.error_description_failedToSave),
                    type = TopBarManager.TopBarMessageType.ERROR,
                    secondaryText = App.getInstance().getString(R.string.action_openSettings),
                    secondaryAction = { IntentUtils.launchAppSettings() }
                )
            )
        }
    }

    val launcher = getPermissionLauncher(onPermissionResult)

    if (isExportSeedRequested && isStoragePermissionGranted) {
        viewModel.onSubmit(navController)
        isExportSeedRequested = false
    }

    val onExportClick = {
        isExportSeedRequested = true

        if (Build.VERSION.SDK_INT > 29) {
            isStoragePermissionGranted = true
        } else {
            PermissionCheck.requestPermission(
                context = context,
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                shouldRequest = true,
                onPermissionResult = onPermissionResult,
                launcher = launcher
            )
        }
    }

    AnalyticsScreenWatcher(
        lifecycleOwner = LocalLifecycleOwner.current,
        event = AnalyticsManager.Screen.Backup
    )

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        val (seedView, captionText, buttonAction) = createRefs()

        AnimatedVisibility(
            modifier = Modifier
                .fillMaxHeight()
                .constrainAs(seedView) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(captionText.top)
                    height = Dimension.fillToConstraints
                },
            visibleState = isAccessKeyVisible,
            enter = fadeIn(animationSpec = tween(300, 0)),
            exit = fadeOut(animationSpec = tween(300, 0))
        ) {
            dataState.accessKeyCroppedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "",
                    contentScale = ContentScale.Inside
                )
            }
        }

        Text(
            modifier = Modifier
                .constrainAs(captionText) {
                    //top.linkTo(seedView.bottom)
                    bottom.linkTo(buttonAction.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
            color = BrandLight,
            text = stringResource(R.string.subtitle_accessKeyDescription)
        )

        CodeButton(
            modifier = Modifier
                .constrainAs(buttonAction) {
                    //top.linkTo(captionText.bottom)
                    bottom.linkTo(parent.bottom)
                },
            onClick = {
                onExportClick()
            },
            text = stringResource(R.string.action_saveAccessKey),
            buttonState = ButtonState.Filled,
            isLoading = dataState.isLoading,
            enabled = dataState.isEnabled,
            isSuccess = dataState.isSuccess,
        )
    }


    SideEffect {
        viewModel.init()
    }

    LaunchedEffect(dataState.accessKeyCroppedBitmap) {
        isAccessKeyVisible.targetState = dataState.accessKeyBitmap != null
    }

}