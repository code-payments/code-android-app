package com.flipcash.app.core

import cafe.adriel.voyager.core.registry.ScreenProvider
import com.getcode.ui.core.RestrictionType
import dev.theolm.rinku.DeepLink

sealed class NavScreenProvider : ScreenProvider {
    data class AppRestricted(val restrictionType: RestrictionType): NavScreenProvider()

    sealed class Login {
        data class Home(val seed: String? = null) : NavScreenProvider()
        data object SeedInput : NavScreenProvider()
        data class NotificationPermission(val fromOnboarding: Boolean = false) : NavScreenProvider()
    }

    sealed interface CreateAccount {
        data object Start: NavScreenProvider()
        data class AccessKey(val showInModal: Boolean = false) : NavScreenProvider()
        data object Purchase: NavScreenProvider()
    }

    sealed interface HomeScreen {
        data class Scanner(val deeplink: DeepLink? = null) : NavScreenProvider()
        data object Give: NavScreenProvider()
        data object Send: NavScreenProvider()
        data object Balance: NavScreenProvider()
        data object Menu: NavScreenProvider()
    }
}