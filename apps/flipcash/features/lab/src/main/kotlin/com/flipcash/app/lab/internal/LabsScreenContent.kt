package com.flipcash.app.lab.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.features.lab.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SettingsSwitchRow

@Composable
internal fun LabsScreenContent() {
    val betaFlagsController = LocalFeatureFlags.current
    val betaFlags by betaFlagsController.observe().collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(betaFlags) { feature ->
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
                Box(modifier = Modifier.fillParentMaxSize()) {
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

private val FeatureFlag.title: String
    get() = ""

private val FeatureFlag.message: String
    get() = ""