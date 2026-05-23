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
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
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
internal fun LabsScreenContent(viewModel: LabsScreenViewModel) {
    val betaFlagsController = LocalFeatureFlags.current
    val betaFlags by betaFlagsController.observe().collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val isStaff by viewModel.isStaff.collectAsStateWithLifecycle()

    val state = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .verticalScrollStateGradient(
                scrollState = state,
                isLongGradient = true,
            ).sheetResignmentBehavior(state),
        contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x3),
    ) {
        item {
            SectionHeader(stringResource(R.string.title_settingsSectionFeatures))
        }
        items(betaFlags, key = { it.flag.key }) { feature ->
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

        item { SectionHeader(stringResource(R.string.title_settingsSectionHomeScreen)) }
        item {
            ListItem(
                headline = stringResource(R.string.title_settingsButtonOrder),
                icon = painterResource(R.drawable.ic_bottom_navigation),
            ) {
                navigator.navigate(AppRoute.Menu.NavBarSettings)
            }
        }

        if (betaFlags.isEmpty()) {
            item {
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

        if (isStaff) {
            item { SectionHeader(stringResource(R.string.title_settingsSectionDeveloper)) }
            item {
                ListItem(
                    headline = stringResource(R.string.subtitle_settingsUserFlags),
                    icon = rememberVectorPainter(Icons.Default.Token),
                ) {
                    navigator.navigate(AppRoute.UserFlags)
                }
            }
        }

        if (isLoggedIn) {
            item { SectionHeader(stringResource(R.string.title_settingsSectionAccount)) }
            item {
                ListItem(
                    headline = stringResource(R.string.action_unlinkPhone),
                    icon = rememberVectorPainter(Icons.Default.PhonelinkErase),
                ) {
                    viewModel.unlinkPhone()
                }
            }
            item {
                ListItem(
                    headline = stringResource(R.string.action_unlinkEmail),
                    icon = rememberVectorPainter(Icons.Default.MarkEmailUnread),
                ) {
                    viewModel.unlinkEmail()
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