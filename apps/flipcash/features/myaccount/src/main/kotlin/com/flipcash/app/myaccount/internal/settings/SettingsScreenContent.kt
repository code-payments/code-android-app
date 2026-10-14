package com.flipcash.app.myaccount.internal.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.myaccount.BuildConfig
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getcode.libs.biometrics.Biometrics
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.BetaIndicator
import com.getcode.ui.components.BottomScrollEdge
import com.getcode.ui.components.ListItem
import com.getcode.ui.components.ListItemDefaults
import com.getcode.ui.components.text.SectionHeader
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    viewModel: SettingsViewModel,
    listState: LazyListState,
    topPadding: Dp,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val updater = LocalAppUpdater.current
    val update by updater.optionalUpdate.collectAsStateWithLifecycle()
    val downloaded by updater.updateDownloaded.collectAsStateWithLifecycle()
    val progress by updater.updateProgress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // The debug fake in AppUpdateModule is the one way a debug build holds an optional update, and
    // then it stands in for a store build.
    val card = versionCardState(
        isDebug = BuildConfig.DEBUG && update == null,
        update = update,
        downloaded = downloaded,
        progress = progress,
    )

    // The pinned card floats over the list, so the list leaves room for it.
    var pinnedHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pinnedGap = CodeTheme.dimens.grid.x3

    Box(modifier = modifier.fillMaxSize()) {
        SettingsScreenContent(
            state = state,
            dispatch = viewModel::dispatchEvent,
            listState = listState,
            topPadding = topPadding,
            card = card,
            bottomPadding = if (card.pinned) pinnedHeight + pinnedGap else navBars,
            onUpdate = { scope.launch { updater.startOptionalUpdate() } },
            onRestart = updater::completeUpdate,
            modifier = Modifier.hazeSource(hazeState),
        )
        // Rows fade out toward the bottom on scroll, under the pinned card when it shows.
        BottomScrollEdge(
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
            listState = listState,
        )
        if (card.pinned) {
            // The card floats 30dp above the screen bottom (Figma) on its own glass; the list scrolls
            // behind it and leaves room through bottomPadding.
            val bottomGap = 30.dp.coerceAtLeast(navBars)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { pinnedHeight = with(density) { it.height.toDp() } }
                    .padding(top = pinnedGap, bottom = bottomGap)
                    .padding(horizontal = 20.dp),
            ) {
                VersionCard(
                    state = card,
                    versionName = state.appVersionInfo.versionName,
                    versionCode = state.appVersionInfo.versionCode,
                    releaseTrack = state.releaseTrack,
                    // The beta toggle lives on the inline card only.
                    onClick = {},
                    onUpdate = { scope.launch { updater.startOptionalUpdate() } },
                    onRestart = updater::completeUpdate,
                    hazeState = hazeState,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreenContent(
    state: SettingsViewModel.State,
    dispatch: (SettingsViewModel.Event) -> Unit,
    listState: LazyListState,
    topPadding: Dp,
    card: VersionCardState,
    bottomPadding: Dp,
    onUpdate: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Flipping the biometrics requirement has to be authenticated by the biometrics themselves,
    // so the row routes through a prompt before the toggle is dispatched. The switch is display
    // only; tapping anywhere on the row (the switch included) runs this.
    val toggleBiometrics = {
        if (state.biometricsAvailable) {
            scope.launch {
                Biometrics.prompt(context, delay = 300)
                    .onSuccess { dispatch(SettingsViewModel.Event.OnBiometricsToggled) }
            }
        }
        Unit
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            // Rows scroll under the app bar but come to rest clear of it.
            top = topPadding,
            // The last row scrolls clear of the gesture bar and, when pinned, of the version card.
            bottom = bottomPadding,
        ),
    ) {
        state.sections.forEach { section ->
            item(key = section.title, contentType = "header") {
                SectionHeader(
                    title = stringResource(section.title).uppercase(),
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x5),
                )
            }
            items(section.items, key = { it.id }, contentType = { "row" }) { item ->
                val isBiometrics = item == RequireBiometrics
                ListItem(
                    headline = item.name,
                    icon = item.icon,
                    modifier = Modifier,
                    // Only the biometrics row can be inert, and only when the hardware has nothing
                    // enrolled.
                    enabled = !isBiometrics || state.biometricsAvailable,
                    supportingText = state.biometricsDescription
                        ?.takeIf { isBiometrics }
                        ?.let { stringResource(it) },
                    onClick = { if (isBiometrics) toggleBiometrics() else dispatch(item.action) },
                    endSlot = {
                        if (item.showBetaIndicator) {
                            BetaIndicator()
                            Spacer(Modifier.width(CodeTheme.dimens.grid.x2))
                        }
                        if (isBiometrics) {
                            ListItemDefaults.Toggle(
                                checked = state.biometricsRequired,
                                enabled = state.biometricsAvailable,
                            )
                        } else {
                            ListItemDefaults.Chevron()
                        }
                    },
                )
            }
        }

        if (!card.pinned) {
            item(key = "version_card", contentType = "footer") {
                VersionCard(
                    state = card,
                    versionName = state.appVersionInfo.versionName,
                    versionCode = state.appVersionInfo.versionCode,
                    releaseTrack = state.releaseTrack,
                    // Its repeated tap toggles beta access (see the ViewModel).
                    onClick = { dispatch(SettingsViewModel.Event.OnVersionInfoClicked) },
                    onUpdate = onUpdate,
                    onRestart = onRestart,
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(top = CodeTheme.dimens.grid.x6, bottom = CodeTheme.dimens.grid.x3),
                )
            }
        }
    }
}
