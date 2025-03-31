package xyz.flipchat.app.features.home.tabs

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.CodeNavigatorStub
import com.getcode.navigation.core.NavigationLocator
import com.getcode.navigation.core.NavigatorWrapper
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.settings.SettingsViewModel

@Parcelize
internal class CashTab(override val ordinal: Int) : ChildNavTab, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override var childNav: NavigationLocator = CodeNavigatorStub

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = ordinal.toUShort(),
            title = stringResource(R.string.title_cashTab),
            icon = painterResource(R.drawable.ic_fc_balance)
        )

    @Composable
    override fun Content() {
        Column {
            AppBarWithTitle(title = options.title)
            Navigator(ScreenRegistry.get(NavScreenProvider.Balance)) { navigator ->
                childNav = NavigatorWrapper(navigator)
                SlideTransition(navigator)
            }
        }
    }
}