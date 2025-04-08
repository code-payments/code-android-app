package xyz.flipchat.app.features.home.tabs

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.NavigationLocator
import com.getcode.navigation.core.NavigatorStub
import com.getcode.navigation.core.NavigatorWrapper
import com.getcode.navigation.screens.ChildNavTab
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R

@Parcelize
class ProfileTab(override val ordinal: Int): ChildNavTab, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override var childNav: NavigationLocator = NavigatorStub

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = ordinal.toUShort(),
            title = stringResource(R.string.title_profileTab),
            icon = rememberVectorPainter(Icons.Outlined.Person)
        )

    @Composable
    override fun Content() {
        Navigator(ScreenRegistry.get(NavScreenProvider.OwnProfile)) { navigator ->
            childNav = NavigatorWrapper(navigator)
            SlideTransition(navigator)
        }
    }
}