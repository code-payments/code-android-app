package xyz.flipchat.app.features.home.tabs

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.CodeNavigatorStub
import com.getcode.navigation.core.NavigationLocator
import com.getcode.navigation.core.NavigatorWrapper
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R

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