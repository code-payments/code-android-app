package com.flipcash.app.core

import cafe.adriel.voyager.core.registry.ScreenProvider
import com.getcode.ui.core.RestrictionType
import dev.theolm.rinku.DeepLink

sealed class NavScreenProvider : ScreenProvider {
    data class AppRestricted(val restrictionType: RestrictionType): NavScreenProvider()

    sealed class Login {
        data class Home(val seed: String? = null) : NavScreenProvider()
        data object SeedInput : NavScreenProvider()
        data object AccessKey : NavScreenProvider()
    }

    sealed interface Permissions {
        data class Notification(val fromOnboarding: Boolean = false) : NavScreenProvider()
        data class Camera(val fromOnboarding: Boolean = false) : NavScreenProvider()
    }

    sealed interface CreateAccount {
        data class AccessKey(val showInModal: Boolean = false) : NavScreenProvider()
        data object Purchase: NavScreenProvider()
    }

    sealed interface HomeScreen {
        data class Scanner(val deeplink: DeepLink? = null) : NavScreenProvider()
        data object Give: NavScreenProvider()
        data object Send: NavScreenProvider()
        data object Balance: NavScreenProvider()
        sealed class Menu {
            data object Root: NavScreenProvider()
            data object Deposit: NavScreenProvider()
            data object Withdraw: NavScreenProvider()
            data object MyAccount: NavScreenProvider()
            data object AppSettings: NavScreenProvider()
            data object Labs: NavScreenProvider()
        }
    }
}