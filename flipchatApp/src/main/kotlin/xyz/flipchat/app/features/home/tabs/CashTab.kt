package xyz.flipchat.app.features.home.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.ui.components.AppBarWithTitle
import xyz.flipchat.app.R

internal object CashTab : ChildNavTab {
    override val ordinal: Int = 1

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = ordinal.toUShort(),
            title = stringResource(R.string.title_cashTab),
            icon = painterResource(R.drawable.ic_kin_white_outline)
        )

    @Composable
    override fun Content() {
        Column {
            AppBarWithTitle(
                title = options.title,
            )
            Navigator(ScreenRegistry.get(NavScreenProvider.Balance))
        }
    }
}