package xyz.flipchat.features.login

import android.os.Parcelable
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.features.login.LoginHome

@Parcelize
data class LoginScreen(val seed: String? = null) : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
//        val analytics = LocalAnalytics.current
//
        if (seed != null) {
//            SeedDeepLink(getViewModel(), seed)
        } else {
            LoginHome(
                createAccount = {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Registration))
                },
                login = {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Login.SeedInput))
                }
            )
        }
    }
}