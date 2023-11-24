package com.getcode.view.login

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.App
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.theme.BrandLight
import com.getcode.util.IntentUtils
import com.getcode.view.ARG_SIGN_IN_ENTROPY_B64
import com.getcode.view.components.*

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun AccessKey(
    navController: NavController? = null,
    upPress: () -> Unit = {},
    arguments: Bundle? = null
) {
    val viewModel = hiltViewModel<AccessKeyViewModel>()
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
                    secondaryText = App.getInstance().getString(R.string.action_openSettings),
                    secondaryAction = { IntentUtils.launchAppSettings() }
                )
            )
        }
    }

    val launcher = getPermissionLauncher(onPermissionResult)

    if (isExportSeedRequested && isStoragePermissionGranted) {
        viewModel.onSubmit(navController, true)
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
        viewModel.onSubmit(navController, false)
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 85.dp)
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
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.body2.copy(textAlign = TextAlign.Center),
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
                .padding(bottom = 10.dp)
                .constrainAs(buttonSkip) {
                    linkTo(buttonSkip.bottom, parent.bottom, bias = 1.0F)
                },
            onClick = {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = App.getInstance().getString(R.string.prompt_title_wroteThemDown),
                        subtitle = App.getInstance()
                            .getString(R.string.prompt_description_wroteThemDown),
                        positiveText = App.getInstance()
                            .getString(R.string.action_yesWroteThemDown),
                        negativeText = App.getInstance().getString(R.string.action_cancel),
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
                title = App.getInstance().getString(R.string.prompt_title_exitAccountCreation),
                subtitle = App.getInstance()
                    .getString(R.string.prompt_description_exitAccountCreation),
                positiveText = App.getInstance().getString(R.string.action_exit),
                negativeText = App.getInstance().getString(R.string.action_cancel),
                onPositive = { navController?.navigateUp() },
                onNegative = {}
            )
        )
    }

    LaunchedEffect(rememberUpdatedState(Unit)) {
        arguments?.getString(ARG_SIGN_IN_ENTROPY_B64)
            ?.let { viewModel.initWithEntropy(it) }
    }

    LaunchedEffect(dataState.accessKeyCroppedBitmap) {
        isAccessKeyVisible.targetState = dataState.accessKeyBitmap != null
    }
}

