package com.flipcash.app.myaccount.internal.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.flipcash.shared.common.ui.profile.ProfileActionButton
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.ui.components.CapsuleProgressBar
import com.getcode.ui.components.glass.floatingGlass
import com.getcode.ui.core.noRippleClickable
import dev.chrisbanes.haze.HazeState

/** Flipcash2 spec text green (#73EAA4); the theme's `successText` still resolves to the fill green. */
private val UpdateTextGreen = Color(0xFF73EAA4)

/**
 * The compact version card at the bottom of Settings (Figma, A8 columns of section 11435:162).
 * Inline it takes the You tab's stat card fill and the version-tap beta toggle ([onClick]); pinned
 * (a [VersionCardState.pinned] state) it floats on glass over [hazeState] and takes no taps.
 */
@Composable
internal fun VersionCard(
    state: VersionCardState,
    versionName: String,
    versionCode: Int,
    releaseTrack: String,
    onClick: () -> Unit,
    onUpdate: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val shape = CodeTheme.shapes.medium
    val surface = if (state.pinned) {
        Modifier.floatingGlass(hazeState, shape)
    } else {
        Modifier
            .clip(shape)
            .background(White05)
            .noRippleClickable(onClick = onClick)
    }
    val build = stringResource(
        R.string.subtitle_versionCardBuild,
        versionCode,
        if (state == VersionCardState.Debug) "" else releaseTrack,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(surface)
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val (title, caption, captionColor) = when (state) {
                    VersionCardState.Debug,
                    VersionCardState.UpToDate -> Triple(
                        stringResource(R.string.title_versionCard, versionName),
                        build,
                        CodeTheme.colors.textSecondary,
                    )
                    is VersionCardState.Available -> Triple(
                        stringResource(R.string.title_versionCard, versionName),
                        state.versionName?.let { stringResource(R.string.subtitle_versionAvailable, it) }
                            ?: stringResource(R.string.title_newVersionAvailable),
                        UpdateTextGreen,
                    )
                    is VersionCardState.Downloading -> Triple(
                        state.versionName?.let { stringResource(R.string.title_updateDownloading, it) }
                            ?: stringResource(R.string.title_updateDownloadingUnnamed),
                        build,
                        CodeTheme.colors.textSecondary,
                    )
                    is VersionCardState.Ready -> Triple(
                        state.versionName?.let { stringResource(R.string.title_updateReadyNamed, it) }
                            ?: stringResource(R.string.title_updateReady),
                        stringResource(R.string.subtitle_restartToFinishUpdating),
                        CodeTheme.colors.textSecondary,
                    )
                }
                Text(text = title, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textMain)
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                    text = caption,
                    style = CodeTheme.typography.caption,
                    color = captionColor,
                )
            }
            when (state) {
                VersionCardState.Debug -> Text(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(White10)
                        .padding(horizontal = CodeTheme.dimens.grid.x2, vertical = CodeTheme.dimens.grid.x1),
                    text = stringResource(R.string.label_debugBuild),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
                VersionCardState.UpToDate -> Text(
                    text = stringResource(R.string.label_upToDate),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
                is VersionCardState.Available -> ProfileActionButton(
                    text = stringResource(R.string.action_update),
                    onClick = onUpdate,
                )
                is VersionCardState.Downloading -> Text(
                    text = "${(state.fraction * 100).toInt()}%",
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
                is VersionCardState.Ready -> ProfileActionButton(
                    text = stringResource(R.string.action_restart),
                    onClick = onRestart,
                    contentColor = UpdateTextGreen,
                )
            }
        }
        when (state) {
            is VersionCardState.Downloading -> CapsuleProgressBar(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                fraction = state.fraction,
                // White while partial: a green partial bar reads as done.
                color = Color.White,
            )
            is VersionCardState.Ready -> CapsuleProgressBar(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                fraction = 1f,
                color = CodeTheme.colors.success,
            )
            else -> Unit
        }
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_VersionCard() {
    Column(
        modifier = Modifier.padding(CodeTheme.dimens.inset),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        listOf(
            VersionCardState.UpToDate,
            VersionCardState.Debug,
            VersionCardState.Available("2026.10.4"),
            VersionCardState.Downloading("2026.10.4", 0.62f),
            VersionCardState.Ready("2026.10.4"),
        ).forEach {
            VersionCard(it, "2026.10.3", 4824, "", onClick = {}, onUpdate = {}, onRestart = {})
        }
    }
}
