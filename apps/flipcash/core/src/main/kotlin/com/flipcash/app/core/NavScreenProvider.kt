package com.flipcash.app.core

import android.os.Parcelable
import cafe.adriel.voyager.core.registry.ScreenProvider
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.transfers.TransferDirection
import com.flipcash.app.core.verification.email.EmailDeeplinkOrigin
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.core.RestrictionType
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class NavScreenProvider : ScreenProvider, Parcelable {
    data class AppRestricted(val restrictionType: RestrictionType) : NavScreenProvider()

    sealed interface Login {
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

        sealed interface Pools {
            data object Root : NavScreenProvider()

            sealed interface Create {
                data object Amount : NavScreenProvider()
                data object Name : NavScreenProvider()
                data object Confirmation : NavScreenProvider()
            }

            data class ChoiceSelection(
                val poolId: ID? = null,
                val rendezvous: Ed25519.KeyPair? = null,
            ) : NavScreenProvider()
        }

        data object ShareApp : NavScreenProvider()

        sealed interface Verification {
            data class Flow(
                val origin: NavScreenProvider,
                val target: NavScreenProvider,
                val includePhone: Boolean = true,
                val includeEmail: Boolean = true,
                val email: String? = null,
                val emailVerificationCode: String? = null,
            ): NavScreenProvider()

//            data class Phone(val origin: NavScreenProvider? = null) : NavScreenProvider()
//            data object PhoneCountry : NavScreenProvider()
//            data object PhoneCode : NavScreenProvider()
//            data class Email(val origin: EmailDeeplinkOrigin? = null) : NavScreenProvider()
//            data class EmailMagicLink(val email: String? = null, val code: String? = null) : NavScreenProvider()
        }

        sealed interface OnRamp {
            data class ProviderList(
                val from: NavScreenProvider? = null,
                val neededAmount: Fiat? = null,
            ) : NavScreenProvider()

            data object Amount : NavScreenProvider()
            data object Success : NavScreenProvider()
        }

        sealed interface Menu {
            data object Root : NavScreenProvider()

            sealed interface Transfers {
                data class Learn(val direction: TransferDirection) : NavScreenProvider()
                data object Deposit : NavScreenProvider()

                sealed interface Withdrawal {
                    data object Amount : NavScreenProvider()
                    data object Destination : NavScreenProvider()
                    data object Confirmation : NavScreenProvider()
                }
            }

            sealed interface MyAccount {
                data object Root : NavScreenProvider()
                data object BackupKey : NavScreenProvider()
            }

            data object AppSettings : NavScreenProvider()

            data object Lab : NavScreenProvider()
        }

        data class CurrencySelection(val kind: CurrencySelectionKind) : NavScreenProvider()
    }
}