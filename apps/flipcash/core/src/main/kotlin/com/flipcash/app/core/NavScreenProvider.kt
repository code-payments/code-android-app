package com.flipcash.app.core

import cafe.adriel.voyager.core.registry.ScreenProvider
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.navigation.DeeplinkType
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.ui.core.RestrictionType

sealed class NavScreenProvider : ScreenProvider {
    data class AppRestricted(val restrictionType: RestrictionType) : NavScreenProvider()

    sealed class Login {
        data class Home(val seed: String? = null, val fromDeeplink: Boolean = false) : NavScreenProvider()
        data object SeedInput : NavScreenProvider()
    }

    sealed interface Permissions {
        data class Notification(val fromOnboarding: Boolean = false) : NavScreenProvider()
        data class Camera(val fromOnboarding: Boolean = false) : NavScreenProvider()
    }

    sealed interface CreateAccount {
        data object Purchase : NavScreenProvider()
        data object AccessKey : NavScreenProvider()
    }
//    https://app.flipcash.com/login?data=NtNLaUA7mqB2VVDnzXFCGB
    sealed interface HomeScreen {
        data class Scanner(val deeplink: DeeplinkType? = null) : NavScreenProvider()
        data object Give : NavScreenProvider()
        data object Send : NavScreenProvider()
        data object Balance : NavScreenProvider()

        data object ShareApp: NavScreenProvider()

        sealed class Menu {
            data object Root : NavScreenProvider()
            data object Deposit : NavScreenProvider()

            sealed class Withdrawal {
                data object Amount : NavScreenProvider()
                data class Confirmation(val amount: LocalFiat) : NavScreenProvider()
            }

            data object Withdraw : NavScreenProvider()

            sealed class MyAccount {
                data object Root : NavScreenProvider()
                data object BackupKey : NavScreenProvider()
            }

            data object AppSettings : NavScreenProvider()
            data object Lab : NavScreenProvider()
        }

        data class CurrencySelection(val kind: CurrencySelectionKind) : NavScreenProvider()
    }
}