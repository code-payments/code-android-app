package com.getcode.view.login

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.util.IntentUtils
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.getPermissionLauncher

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun AccessKey(
    viewModel: AccessKeyViewModel = hiltViewModel(),
    arguments: LoginArgs = LoginArgs(),
) {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val dataState by viewModel.uiFlow.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    keyboardController?.hide()

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
        viewModel.onSubmit(navigator, true)
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
    val onSkipClick = {
        viewModel.onSubmit(navigator, false)
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.grid.x17)
    ) {
        val (seedView, captionText, buttonAction, buttonSkip) = createRefs()

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
                    modifier = Modifier
                        .constrainAs(seedView) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            bottom.linkTo(captionText.top)
                            height = Dimension.fillToConstraints
                        },
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
                    top.linkTo(captionText.bottom)
                    linkTo(buttonAction.bottom, buttonSkip.top, bias = 1.0F)
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

        CodeButton(
            modifier = Modifier
                .padding(bottom = CodeTheme.dimens.grid.x2)
                .constrainAs(buttonSkip) {
                    linkTo(buttonSkip.bottom, parent.bottom, bias = 1.0F)
                },
            onClick = {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = context.getString(R.string.prompt_title_wroteThemDown),
                        subtitle = context
                            .getString(R.string.prompt_description_wroteThemDown),
                        positiveText = context
                            .getString(R.string.action_yesWroteThemDown),
                        negativeText = context.getString(R.string.action_cancel),
                        onPositive = { onSkipClick() },
                        onNegative = {}
                    )
                )
            },
            text = stringResource(R.string.action_wroteThemDownInstead),
            buttonState = ButtonState.Subtle,
            enabled = dataState.isEnabled,
            isPaddedVertical = false,
        )
    }

    BackHandler {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = context.getString(R.string.prompt_title_exitAccountCreation),
                subtitle = context
                    .getString(R.string.prompt_description_exitAccountCreation),
                positiveText = context.getString(R.string.action_exit),
                negativeText = context.getString(R.string.action_cancel),
                onPositive = { navigator.popAll() },
                onNegative = {}
            )
        )
    }

    LaunchedEffect(viewModel) {
        arguments.signInEntropy
            ?.let { viewModel.initWithEntropy(it) }
    }

    LaunchedEffect(dataState.accessKeyCroppedBitmap) {
        isAccessKeyVisible.targetState = dataState.accessKeyBitmap != null
    }
}

