package com.flipcash.app.lab.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureTrack
import com.flipcash.app.featureflags.FlagOption
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.featureflags.message
import com.flipcash.app.featureflags.title
import com.flipcash.features.lab.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.ListItem
import com.getcode.ui.components.SettingsSwitchRow
import com.getcode.ui.components.text.SectionHeader
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeSegmentedControl
import com.getcode.ui.utils.sheetResignmentBehavior

@Composable
internal fun LabsScreenContent(viewModel: LabsScreenViewModel, onboarding: Boolean = false) {
    val betaFlagsController = LocalFeatureFlags.current
    val allFlags by betaFlagsController.observe().collectAsStateWithLifecycle()
    val betaOverride by viewModel.betaOverride.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val isStaff by viewModel.isStaff.collectAsStateWithLifecycle()

    // Keep showing all flags even after toggling off override, until leaving the screen
    var showAllFlags by remember { mutableStateOf(betaOverride) }
    LaunchedEffect(betaOverride) {
        if (betaOverride) showAllFlags = true
    }

    val betaFlags = remember(allFlags, showAllFlags, onboarding) {
        if (showAllFlags) allFlags
        else allFlags.filter {
            it.flag.minTrack == FeatureTrack.Production || (onboarding && it.flag.onboarding)
        }
    }

    val state = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollStateGradient(
                scrollState = state,
                isLongGradient = true,
            )
            .sheetResignmentBehavior(state),
        contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x3),
    ) {
        if (showAllFlags) {
            item(contentType = "override_toggle") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(top = CodeTheme.dimens.grid.x2),
                    shape = CodeTheme.shapes.medium,
                    color = CodeTheme.colors.surfaceVariant,
                ) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.title_betaOverride),
                        subtitle = stringResource(R.string.subtitle_betaOverride),
                        checked = betaOverride,
                    ) {
                        viewModel.disableBetaFeatures()
                    }
                }
            }
        }

        item(contentType = "section_header") {
            SectionHeader(
                modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                title = stringResource(R.string.title_settingsSectionFeatures)
            )
        }
        items(betaFlags, key = { it.flag.key }, contentType = { "feature_flag" }) { feature ->
            Column(modifier = Modifier.animateItem()) {
                if (feature.flag.isOptionFlag) {
                    SettingsOptionRow(
                        title = feature.flag.title,
                        subtitle = feature.flag.message,
                        options = feature.flag.options,
                        selectedOption = feature.selectedOption ?: feature.flag.defaultOption,
                        onOptionSelected = { optionKey ->
                            betaFlagsController.setOption(feature.flag, optionKey)
                        },
                    )
                } else {
                    SettingsSwitchRow(
                        title = feature.flag.title,
                        subtitle = feature.flag.message,
                        checked = feature.enabled
                    ) {
                        betaFlagsController.set(feature.flag, !feature.enabled)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                    color = CodeTheme.colors.divider,
                    thickness = 0.5.dp
                )
            }
        }

        if (betaFlags.isEmpty()) {
            item(contentType = "empty_state") {
                Box {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "\uD83D\uDE2D",
                            style = CodeTheme.typography.displayMedium
                        )
                        Text(
                            text = stringResource(R.string.title_labsAreEmpty),
                            style = CodeTheme.typography.textLarge,
                            color = CodeTheme.colors.textMain
                        )

                        Text(
                            text = stringResource(R.string.subtitle_labsAreEmpty),
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }

        if (showAllFlags) {
            item(contentType = "section_header") {
                SectionHeader(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                    title = stringResource(R.string.title_settingsSectionHomeScreen)
                )
            }
            item(contentType = "list_item") {
                ListItem(
                    headline = stringResource(R.string.title_settingsButtonOrder),
                    icon = painterResource(R.drawable.ic_bottom_navigation),
                ) {
                    navigator.navigate(AppRoute.Menu.NavBarSettings)
                }
            }
        }

        if (showAllFlags && isStaff) {
            item(contentType = "section_header") {
                SectionHeader(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
                    title = stringResource(R.string.title_settingsSectionDeveloper)
                )
            }
            item(contentType = "list_item") {
                ListItem(
                    headline = stringResource(R.string.subtitle_settingsUserFlags),
                    icon = rememberVectorPainter(Icons.Default.Token),
                ) {
                    navigator.navigate(AppRoute.UserFlags)
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionRow(
    title: String,
    subtitle: String?,
    options: List<FlagOption>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.grid.x3)
            .padding(vertical = CodeTheme.dimens.grid.x3),
    ) {
        Text(
            text = title,
            color = CodeTheme.colors.textMain,
            style = CodeTheme.typography.textMedium,
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        CodeSegmentedControl(
            options = options,
            selected = options.find { it.key == selectedOption },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CodeTheme.dimens.grid.x2),
            mapper = { option ->
                Text(text = option.label)
            },
            onSelectionChanged = { option ->
                onOptionSelected(option.key)
            },
        )
    }
}