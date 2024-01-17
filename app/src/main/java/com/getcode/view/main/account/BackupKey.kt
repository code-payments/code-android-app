package com.getcode.view.main.account

import android.Manifest
import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.room.util.copy
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.util.IntentUtils
import com.getcode.util.measured
import com.getcode.util.rememberedClickable
import com.getcode.util.rememberedLongClickable
import com.getcode.util.swallowClicks
import com.getcode.view.components.AccessKeySelectionContainer
import com.getcode.view.components.ButtonState
import com.getcode.view.components.Cloudy
import com.getcode.view.components.CodeButton
import com.getcode.view.components.PermissionCheck
import com.getcode.view.components.getPermissionLauncher
import com.getcode.view.components.rememberSelectionState

@Composable
fun BackupKey(
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


    var buttonHeight by remember {
        mutableStateOf(0.dp)
    }

    var textHeight by remember {
        mutableStateOf(0.dp)
    }

    val selectionState = rememberSelectionState(words = dataState.wordsFormatted)

    AccessKeySelectionContainer(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(vertical = CodeTheme.dimens.grid.x4),
        state = selectionState,
    ) {
        Cloudy(modifier = Modifier.fillMaxSize(), enabled = selectionState.shown) {
            Text(
                modifier = Modifier
                    .padding(vertical = CodeTheme.dimens.grid.x2)
                    .measured { textHeight = it.height },
                style = CodeTheme.typography.body2.copy(textAlign = TextAlign.Center),
                color = BrandLight,
                text = stringResource(R.string.subtitle_accessKeyDescription)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .measured { buttonHeight = it.height }
            ) {
                CodeButton(
                    onClick = { onExportClick() },
                    text = stringResource(R.string.action_saveAccessKey),
                    buttonState = ButtonState.Filled,
                    isLoading = dataState.isLoading,
                    enabled = dataState.isEnabled,
                    isSuccess = dataState.isSuccess,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .padding(top = textHeight)
                .padding(bottom = buttonHeight),
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
                        contentDescription = dataState.wordsFormatted,
                    )
                }
            }
        }


    }

    LaunchedEffect(viewModel) {
        viewModel.init()
    }

    LaunchedEffect(dataState.accessKeyCroppedBitmap) {
        isAccessKeyVisible.targetState = dataState.accessKeyCroppedBitmap != null
    }
}