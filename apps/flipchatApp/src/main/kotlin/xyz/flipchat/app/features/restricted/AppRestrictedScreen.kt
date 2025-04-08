package xyz.flipchat.app.features.restricted

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.ui.components.restrictions.ContentRestrictedView
import com.getcode.ui.core.RestrictionType
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.features.home.HomeViewModel

@Parcelize
class AppRestrictedScreen(private val restrictionType: RestrictionType): Screen, Parcelable {
    @Composable
    override fun Content() {
        val homeViewModel = getActivityScopedViewModel<HomeViewModel>()
        val navigator = LocalCodeNavigator.current
        ContentRestrictedView(restrictionType) {
            homeViewModel.logout(it) {
                navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
            }
        }
    }
}