package com.flipcash.app.backupkey.internal

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.features.backupkey.R
import com.getcode.manager.TopBarManager
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
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun BackupKeyScreenContent(viewModel: BackupKeyScreenViewModel) {
    val context = LocalContext.current
    val dataState by viewModel.uiFlow.collectAsState()

    var isExportSeedRequested by remember { mutableStateOf(false) }
    var isStoragePermissionGranted by remember { mutableStateOf(false) }
    val isAccessKeyVisible = remember { MutableTransitionState(false) }

    var saved by remember(dataState.isSuccess) { mutableStateOf(dataState.isSuccess) }

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
                    delay(2.seconds)
                    saved = false
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

    var buttonHeight by remember {
        mutableStateOf(0.dp)
    }

    var textHeight by remember {
        mutableStateOf(Dp.Unspecified)
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
                Text(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(bottom = CodeTheme.dimens.grid.x2)
                        .measured { textHeight = it.height },
                    style = CodeTheme.typography.textSmall.copy(textAlign = TextAlign.Center),
                    color = CodeTheme.colors.textSecondary,
                    text = stringResource(R.string.subtitle_accessKeyDescription)
                        .replace(". ", ".\n")
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .measured { buttonHeight = it.height },
                ) {
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onExportClick,
                        text = stringResource(R.string.action_saveToPhotos),
                        buttonState = ButtonState.Filled,
                        isLoading = dataState.isLoading,
                        enabled = dataState.isEnabled,
                        isSuccess = saved,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .addIf(textHeight.isSpecified) { Modifier.padding(top = textHeight) }
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
        }


        LaunchedEffect(dataState.accessKeyCroppedBitmap) {
            isAccessKeyVisible.targetState = dataState.accessKeyCroppedBitmap != null
        }
    }
}