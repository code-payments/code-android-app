package com.flipcash.app.login.accesskey

import android.Manifest
import android.os.Build
import android.os.Parcelable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.features.login.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.Cloudy
import com.getcode.ui.components.SelectionContainer
import com.getcode.ui.components.rememberSelectionState
import com.getcode.ui.core.addIf
import com.getcode.ui.core.measured
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.PermissionChecker
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.getPermissionLauncher
import com.getcode.util.permissions.rememberPermissionHandler
import kotlinx.coroutines.delay
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class AccessKeyScreen : Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_accessKey)

    @Composable
    override fun Content() {
        val viewModel = getViewModel<LoginAccessKeyViewModel>()
        val navigator = LocalCodeNavigator.current
        val permissions = LocalPermissionChecker.current

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                titleAlignment = Alignment.CenterHorizontally,
            )
            AccessKeyScreenContent(viewModel) {
                val nextScreen = when {
                    permissions.isDenied(Manifest.permission.CAMERA) -> {
                        ScreenRegistry.get(NavScreenProvider.Login.CameraPermission(true))
                    }

                    permissions.isDenied(Manifest.permission.POST_NOTIFICATIONS) -> {
                        ScreenRegistry.get(NavScreenProvider.Login.NotificationPermission(true))
                    }

                    else -> ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner())
                }
                navigator.push(nextScreen)
            }
        }

        BackHandler { /* intercept */ }
    }
}


@Composable
internal fun AccessKeyScreenContent(viewModel: LoginAccessKeyViewModel, onCompleted: () -> Unit) {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val dataState by viewModel.uiFlow.collectAsState()

    var isExportSeedRequested by remember { mutableStateOf(false) }
    var isStoragePermissionGranted by remember { mutableStateOf(false) }
    val isAccessKeyVisible = remember { MutableTransitionState(false) }

    val onPermissionResult = { result: PermissionResult ->
        isStoragePermissionGranted = result == PermissionResult.Granted

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

    val launcher =
        getPermissionLauncher(Manifest.permission.WRITE_EXTERNAL_STORAGE, onPermissionResult)
    val permissionChecker = rememberPermissionHandler()

    LaunchedEffect(isExportSeedRequested, isStoragePermissionGranted) {
        if (isExportSeedRequested && isStoragePermissionGranted) {
            viewModel.saveImage()
                .onSuccess {
                    delay(400)
                    onCompleted()
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
        onCompleted()
    }

    var buttonHeight by remember {
        mutableStateOf(0.dp)
    }

    val selectionState = rememberSelectionState(
        content = dataState.words.joinToString(" ")
    )

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
                                    negativeText = "",
                                    tertiaryText = context.getString(R.string.action_cancel),
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
                    positiveText = context.getString(R.string.action_exit),
                    negativeText = "",
                    tertiaryText = context.getString(R.string.action_cancel),
                    onPositive = { navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home())) },
                    type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                    onNegative = {}
                )
            )
        }

        LaunchedEffect(dataState.accessKeyCroppedBitmap) {
            isAccessKeyVisible.targetState = dataState.accessKeyCroppedBitmap != null
        }
    }
}