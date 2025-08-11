package com.flipcash.app.core

import cafe.adriel.voyager.core.registry.ScreenProvider
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.transfers.TransferDirection
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.core.RestrictionType

sealed class NavScreenProvider : ScreenProvider {
    data class AppRestricted(val restrictionType: RestrictionType) : NavScreenProvider()

    sealed class Login {
        data class Home(val seed: String? = null, val fromDeeplink: Boolean = false) :
            NavScreenProvider()

        data object SeedInput : NavScreenProvider()

        data object Lab : NavScreenProvider()
    }

    sealed interface Permissions {
        data class Notification(val fromOnboarding: Boolean = false) : NavScreenProvider()
        data class Camera(val fromOnboarding: Boolean = false) : NavScreenProvider()
    }

    sealed interface CreateAccount {
        data object AccessKey : NavScreenProvider()
        data class Purchase(val fromLogin: Boolean = false) : NavScreenProvider()
    }

    sealed interface HomeScreen {
        data class Scanner(val deeplink: DeeplinkType? = null) : NavScreenProvider()
        // Combined into a single Cash screen
        data object Cash : NavScreenProvider()

//        data object Give : NavScreenProvider()
//        data object Send : NavScreenProvider()
        data object Balance : NavScreenProvider()

        sealed class Pools {
            data object Root : NavScreenProvider()
            sealed class Create {
                data object Amount : NavScreenProvider()
                data object Name : NavScreenProvider()
                data object Confirmation : NavScreenProvider()
            }
            data class ChoiceSelection(
                val poolId: ID? = null,
                val rendezvous: Ed25519.KeyPair? = null,
                val standalone: Boolean = false
            ) : NavScreenProvider()
        }

        data object ShareApp : NavScreenProvider()

       sealed class OnRamp {
           data class ProviderList(
               val from: NavScreenProvider? = null,
               val neededAmount: Fiat? = null,
           ): NavScreenProvider()
           data object Amount : NavScreenProvider()
           data object Success: NavScreenProvider()
       }

        sealed class Menu {
            data object Root : NavScreenProvider()

            data object Transfers {
                data class Learn(val direction: TransferDirection) : NavScreenProvider()
                data object Deposit : NavScreenProvider()

                sealed class Withdrawal {
                    data object Amount : NavScreenProvider()
                    data object Destination : NavScreenProvider()
                    data object Confirmation : NavScreenProvider()
                }
            }

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