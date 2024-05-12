package com.getcode.view.login

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.LocalTopBarPadding
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.utils.measured
import com.getcode.ui.components.AccessKeySelectionContainer
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.Cloudy
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.PermissionCheck
import com.getcode.ui.components.getPermissionLauncher
import com.getcode.ui.components.rememberSelectionState
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.debugBounds
import com.getcode.util.launchAppSettings

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
                    secondaryAction = { context.launchAppSettings() }
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

    var buttonHeight by remember {
        mutableStateOf(0.dp)
    }

    val selectionState = rememberSelectionState(
        words = dataState.words.joinToString(" ")
    )

    AccessKeySelectionContainer(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        state = selectionState,
    ) {
        Cloudy(
            modifier = Modifier
                .fillMaxSize(),
            enabled = selectionState.shown
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(vertical = CodeTheme.dimens.grid.x4)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .measured { buttonHeight = it.height },
                ) {
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onExportClick,
                        text = stringResource(R.string.action_saveAccessKey),
                        buttonState = ButtonState.Filled,
                        isLoading = dataState.isLoading,
                        enabled = dataState.isEnabled,
                        isSuccess = dataState.isSuccess,
                    )

                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
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
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .padding(LocalTopBarPadding.current)
                .addIf(buttonHeight.isSpecified) { Modifier.padding(bottom = buttonHeight + CodeTheme.dimens.grid.x4) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    // highly specific aspect ratio from iOS :)
                    .aspectRatio(0.607f, matchHeightConstraintsFirst = true)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visibleState = isAccessKeyVisible,
                    enter = fadeIn(animationSpec = tween(300, 0)),
                    exit = fadeOut(animationSpec = tween(300, 0))
                ) {
                    dataState.accessKeyCroppedBitmap?.let { bitmap ->
                        Image(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .scale(selectionState.scale.value),
                            bitmap = bitmap.asImageBitmap(),
                            contentScale = ContentScale.Crop,
                            contentDescription = dataState.wordsFormatted,
                        )
                    }
                }
            }

            val textAlpha by animateFloatAsState(
                if (selectionState.shown) 0f else 1f,
                label = "text alpha"
            )

            Text(
                modifier = Modifier
                    .alpha(textAlpha)
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x5)
                    .padding(
                        top = CodeTheme.dimens.grid.x3,
                        bottom = CodeTheme.dimens.grid.x6
                    ),
                style = CodeTheme.typography.body2.copy(textAlign = TextAlign.Center),
                color = White,
                text = stringResource(R.string.subtitle_accessKeyDescription)
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
            isAccessKeyVisible.targetState = dataState.accessKeyCroppedBitmap != null
        }
    }
}

