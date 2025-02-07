package xyz.flipchat.app.features.profile

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.ui.LocalUserManager

@Parcelize
class ProfileScreen: Screen, Parcelable {
    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val userManager = LocalUserManager.current
        Column {
            AppBarWithTitle(
                endContent = {
                    if (userManager?.userFlags?.isStaff == true) {
                        AppBarDefaults.Settings {
                            navigator.push(ScreenRegistry.get(NavScreenProvider.Settings))
                        }
                    }
                }
            )
        }
    }
}