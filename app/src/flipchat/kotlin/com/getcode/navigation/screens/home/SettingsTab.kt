package com.getcode.navigation.screens.home

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.getcode.R

internal object SettingsTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u,
            title = stringResource(R.string.title_settings),
            icon = painterResource(R.drawable.ic_settings_outline)
        )

    @Composable
    override fun Content() {
        Text("Settings")
    }
}