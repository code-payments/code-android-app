package com.flipchat.features.home.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.getcode.oct24.R
import com.getcode.navigation.NavScreenProvider

internal object CashTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = stringResource(R.string.title_kin),
            icon = painterResource(R.drawable.ic_kin_white_small)
        )

    @Composable
    override fun Content() {
        Navigator(ScreenRegistry.get(NavScreenProvider.Balance))
    }
}