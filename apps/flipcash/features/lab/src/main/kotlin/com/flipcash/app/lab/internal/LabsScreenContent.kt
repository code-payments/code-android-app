package com.flipcash.app.lab.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.featureflags.message
import com.flipcash.app.featureflags.title
import com.flipcash.features.lab.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SettingsSwitchRow
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold

@Composable
internal fun LabsScreenContent(viewModel: LabsScreenViewModel) {
    val betaFlagsController = LocalFeatureFlags.current
    val betaFlags by betaFlagsController.observe().collectAsStateWithLifecycle()

    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                if (isLoggedIn) {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        text = stringResource(R.string.action_unlinkPhone),
                        buttonState = ButtonState.Subtle,
                        onClick = viewModel::unlinkPhone
                    )

                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        text = stringResource(R.string.action_unlinkEmail),
                        buttonState = ButtonState.Subtle,
                        onClick = viewModel::unlinkEmail
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            items(betaFlags, key = { it.flag.key }) { feature ->
                SettingsSwitchRow(
                    title = feature.flag.title,
                    subtitle = feature.flag.message,
                    checked = feature.enabled
                ) {
                    betaFlagsController.set(feature.flag, !feature.enabled)
                }
            }
            if (betaFlags.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize()
                    ) {
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
                                color = CodeTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}