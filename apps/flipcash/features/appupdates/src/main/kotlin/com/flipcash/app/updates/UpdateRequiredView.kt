package com.flipcash.app.updates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.theme.FlipcashDesignSystem
import com.flipcash.features.appupdates.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.BiometricsState
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.RepeatOnLifecycle
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun UpdateRequiredBlockingView(
    biometricsState: BiometricsState,
    modifier: Modifier = Modifier,
) {
    val appUpdater = LocalAppUpdater.current
    val availableUpdate by appUpdater.availableUpdate.collectAsStateWithLifecycle()
    val composeScope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = availableUpdate?.isUpdateAvailable == true && biometricsState.passed,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            modifier = modifier,
            color = CodeTheme.colors.brand.copy(alpha = 0.87f),
        ) {
            CodeScaffold(
                bottomBar = {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x1)
                            .navigationBarsPadding(),
                        onClick = {
                            composeScope.launch {
                                appUpdater.startUpdate()
                            }
                        },
                        text = stringResource(id = R.string.action_unlockCode),
                        buttonState = ButtonState.Filled
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_app_update_required),
                            contentDescription = null,
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.title_updateRequired),
                                style = CodeTheme.typography.textLarge,
                                color = CodeTheme.colors.textMain,
                            )
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f),
                                text = stringResource(R.string.subtitle_updateRequired),
                                style = CodeTheme.typography.textMedium,
                                color = CodeTheme.colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    RepeatOnLifecycle(
        targetState = Lifecycle.State.RESUMED
    ) {
        appUpdater.checkForUpdate()
    }
}

@Composable
@Preview
private fun PreviewUpdateRequiredView() {
    FlipcashDesignSystem {
        val appUpdater = remember {
            object : AppUpdateController {
                override val availableUpdate = MutableStateFlow<UpdateInfo?>(
                    UpdateInfo(
                        updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                        updatePriority = 5,
                        clientVersionStalenessDays = null,
                        bytesDownloaded = 0,
                        totalBytesToDownload = 100,
                        installStatus = InstallStatus.UNKNOWN,
                        availableVersionCode = 3000
                    )
                )

                override suspend fun checkForUpdate() = Unit

                override suspend fun startUpdate(): Result<Unit> = Result.success(Unit)

            }
        }
        CompositionLocalProvider(LocalAppUpdater provides appUpdater) {
            UpdateRequiredBlockingView(
                biometricsState = BiometricsState(passed = true),
            )
        }
    }
}