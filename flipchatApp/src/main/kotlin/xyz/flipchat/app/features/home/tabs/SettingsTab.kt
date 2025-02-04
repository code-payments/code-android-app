package xyz.flipchat.app.features.home.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.CodeNavigatorStub
import com.getcode.navigation.core.NavigationLocator
import com.getcode.navigation.core.NavigatorStub
import com.getcode.navigation.core.NavigatorWrapper
import com.getcode.navigation.screens.ChildNavTab
import xyz.flipchat.app.R

internal object SettingsTab : ChildNavTab {

    override val ordinal: Int = 2

    override var childNav: NavigationLocator = NavigatorStub

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = ordinal.toUShort(),
            title = stringResource(R.string.title_settingsTab),
            icon = painterResource(R.drawable.ic_settings_outline)
        )

    @Composable
    override fun Content() {
        Navigator(ScreenRegistry.get(NavScreenProvider.Settings)) { navigator ->
            childNav = NavigatorWrapper(navigator)
            SlideTransition(navigator)
        }
    }
}