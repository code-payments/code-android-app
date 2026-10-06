package com.flipcash.app.myaccount.internal.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.core.R
import com.getcode.libs.biometrics.Biometrics
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.BetaIndicator
import com.getcode.ui.components.ListItem
import com.getcode.ui.components.ListItemDefaults
import com.getcode.ui.components.text.SectionHeader
import com.getcode.ui.core.noRippleClickable
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    SettingsScreenContent(state = state, dispatch = viewModel::dispatchEvent)
}

@Composable
private fun SettingsScreenContent(
    state: SettingsViewModel.State,
    dispatch: (SettingsViewModel.Event) -> Unit,
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
        modifier = Modifier.fillMaxSize(),
        // The version footer is the last row; without this it scrolls under the gesture bar.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
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

        item(key = "version_footer", contentType = "footer") {
            VersionFooter(
                state = state,
                onClick = { dispatch(SettingsViewModel.Event.OnVersionInfoClicked) },
                modifier = Modifier.padding(
                    top = CodeTheme.dimens.grid.x6,
                    bottom = CodeTheme.dimens.grid.x3,
                ),
            )
        }
    }
}

/** The "Version … • Build …" footer; its repeated tap toggles beta access (see the ViewModel). */
@Composable
private fun VersionFooter(
    state: SettingsViewModel.State,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .noRippleClickable { onClick() },
            text = stringResource(
                R.string.subtitle_appVersionInfoFooter,
                state.appVersionInfo.versionName,
                state.appVersionInfo.versionCode,
                state.releaseTrack,
            ),
            color = CodeTheme.colors.textSecondary,
            style = CodeTheme.typography.textSmall.copy(textAlign = TextAlign.Center),
        )
    }
}
