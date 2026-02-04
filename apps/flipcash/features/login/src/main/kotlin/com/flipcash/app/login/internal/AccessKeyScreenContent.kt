package com.flipcash.app.login.internal

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.accesskey.AccessKeyUiModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.login.accesskey.LoginAccessKeyViewModel
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.login.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.Cloudy
import com.getcode.ui.components.SelectionContainer
import com.getcode.ui.components.rememberSelectionState
import com.getcode.ui.core.addIf
import com.getcode.ui.core.measured
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.getPermissionLauncher
import com.getcode.util.permissions.rememberPermissionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AccessKeyScreen(viewModel: LoginAccessKeyViewModel, onCompleted: (requiresIap: Boolean) -> Unit) {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val dataState by viewModel.uiFlow.collectAsState()

    val composeScope = rememberCoroutineScope()

    var isExportSeedRequested by remember { mutableStateOf(false) }
    var isStoragePermissionGranted by remember { mutableStateOf(false) }

    val onPermissionResult = { result: PermissionResult ->
        isStoragePermissionGranted = result == PermissionResult.Granted

        if (!isStoragePermissionGranted) {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_failedToSave),
                message = context.getString(R.string.error_description_failedToSave),
                actions = listOf(
                    BottomBarAction.Ok,
                    BottomBarAction(
                        text = context.getString(R.string.action_openSettings),
                        style = BottomBarManager.BottomBarButtonStyle.Filled50,
                        onClick = { context.launchAppSettings() }
                    )
                ),
            )
        }
    }

    val launcher =
        getPermissionLauncher(Manifest.permission.WRITE_EXTERNAL_STORAGE, onPermissionResult)
    val permissionChecker = rememberPermissionHandler()

    LaunchedEffect(isExportSeedRequested, isStoragePermissionGranted) {
        if (isExportSeedRequested && isStoragePermissionGranted) {
            viewModel.saveImage()
                .onSuccess { requiresIap ->
                    delay(750)
                    onCompleted(requiresIap)
                }
                .onFailure {
                    isExportSeedRequested = false
                }
        }
    }

    val onExportClick = {
        isExportSeedRequested = true

        if (Build.VERSION.SDK_INT > 29) {
            isStoragePermissionGranted = true
        } else {
            permissionChecker.request(
                permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
                onPermissionResult = onPermissionResult,
                launcher = launcher
            )
        }
    }

    val onSkipClick = {
        composeScope.launch {
            viewModel.onWroteDownInstead()
                .onSuccess { requiresIap ->
                    delay(750)
                    onCompleted(requiresIap)
                }
        }
        Unit
    }

    AccessKeyScreenContent(
        dataState = dataState,
        onExport = onExportClick,
        onSkip = onSkipClick,
        onExit = {
            navigator.replaceAll(ScreenRegistry.get(AppRoute.Onboarding.Login()))
        }
    )
}

@Composable
private fun AccessKeyScreenContent(
    dataState: AccessKeyUiModel,
    onExport: () -> Unit,
    onSkip: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current

    var buttonHeight by remember {
        mutableStateOf(0.dp)
    }

    val selectionState = rememberSelectionState(
        content = dataState.words.joinToString(" ")
    )

    val isAccessKeyVisible = remember { MutableTransitionState(false) }


    SelectionContainer(
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
                    .padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x2)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .measured { buttonHeight = it.height },
                ) {
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onExport,
                        text = stringResource(R.string.action_saveAccessKey),
                        buttonState = ButtonState.Filled,
                        isLoading = dataState.exportState.loading,
                        enabled = dataState.exportState.isIdle,
                        isSuccess = dataState.exportState.success,
                    )

                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            BottomBarManager.showMessage(
                                BottomBarManager.BottomBarMessage(
                                    title = context.getString(R.string.prompt_title_wroteThemDown),
                                    subtitle = context
                                        .getString(R.string.prompt_description_wroteThemDown),
                                    showScrim = true,
                                    showCancel = true,
                                    actions = listOf(
                                        BottomBarAction(
                                            text = context
                                                .getString(R.string.action_wroteThemDownInstead),
                                            onClick = onSkip
                                        )
                                    )
                                )
                            )
                        },
                        text = stringResource(R.string.action_wroteThemDownInstead),
                        buttonState = ButtonState.Subtle,
                        isLoading = dataState.skipState.loading,
                        enabled = dataState.skipState.isIdle,
                        isSuccess = dataState.skipState.success,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .addIf(buttonHeight.isSpecified) { Modifier.padding(bottom = buttonHeight) },
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
                style = CodeTheme.typography.textSmall.copy(textAlign = TextAlign.Center),
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
                    showScrim = true,
                    showCancel = true,
                    actions = listOf(
                        BottomBarAction(
                            text = context
                                .getString(R.string.action_exit),
                            onClick = onExit
                        )
                    ),
                    type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                )
            )
        }

        LaunchedEffect(dataState.accessKeyCroppedBitmap) {
            isAccessKeyVisible.targetState = dataState.accessKeyCroppedBitmap != null
        }
    }
}

@Preview
@Composable
private fun Preview_AccessKeyScreen() {
    FlipcashPreview(showBackground = true) {
        AccessKeyScreenContent(
            dataState = AccessKeyUiModel(
                words = listOf("")
            ),
            onExport = { },
            onSkip = { },
            onExit = { },
        )
    }
}