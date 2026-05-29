package com.flipcash.app.lab.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.navigation.GiveButtonLabel
import com.flipcash.app.core.navigation.NavBarConfig
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.ui.components.text.SectionHeader
import com.getcode.ui.theme.CodeSegmentedControl

@Composable
internal fun NavBarSettingsContent() {
    val featureFlags = LocalFeatureFlags.current
    val configString by featureFlags.getOption(FeatureFlag.NavBar)
        .collectAsStateWithLifecycle()
    val config = remember(configString) { NavBarConfig.deserialize(configString) }

    Column(
        modifier = Modifier
            .wrapContentHeight(),
    ) {
        Text(
            text = stringResource(R.string.subtitle_settingsButtonOrder),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x1)
                .clip(CodeTheme.shapes.large)
                .background(White05)
                .padding(vertical = CodeTheme.dimens.grid.x4),
            contentAlignment = Alignment.Center,
        ) {
            NavigationBar(
                config = config,
                onOrderChanged = { newOrder ->
                    val updated = config.copy(order = newOrder)
                    featureFlags.setOption(FeatureFlag.NavBar, updated.serialize())
                },
            )
        }

        SectionHeader(stringResource(R.string.title_settingsSectionGiveButtonLabel))

        CodeSegmentedControl(
            options = GiveButtonLabel.entries,
            selected = config.giveButtonLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x3)
                .navigationBarsPadding(),
            mapper = { option ->
                Text(text = stringResource(option.labelRes))
            },
            onSelectionChanged = { label ->
                val updated = config.copy(giveButtonLabel = label)
                featureFlags.setOption(FeatureFlag.NavBar, updated.serialize())
            },
        )
    }
}
