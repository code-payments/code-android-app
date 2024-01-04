package com.getcode.view.main.account

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.util.IntentUtils
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.getPermissionLauncher

@Composable
fun AccountAccessKey(
    viewModel: AccountAccessKeyViewModel,
) {
    val navigator = LocalCodeNavigator.current
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
                    title = context.getString(R.string.error_title_failedToSave),
                    message = context.getString(R.string.error_description_failedToSave),
                    type = TopBarManager.TopBarMessageType.ERROR,
                    secondaryText = context.getString(R.string.action_openSettings),
                    secondaryAction = { IntentUtils.launchAppSettings() }
                )
            )
        }
    }

    val launcher = getPermissionLauncher(onPermissionResult)

    if (isExportSeedRequested && isStoragePermissionGranted) {
        viewModel.onSubmit(navigator)
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

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CodeTheme.dimens.inset)
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
                .padding(vertical = CodeTheme.dimens.grid.x2),
            style = CodeTheme.typography.body2.copy(textAlign = TextAlign.Center),
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