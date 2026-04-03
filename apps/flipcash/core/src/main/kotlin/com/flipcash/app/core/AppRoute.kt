package com.flipcash.app.core

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.ui.core.RestrictionType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
sealed interface AppRoute : NavKey, Parcelable {

    /** Initial loading/splash route shown while auth state resolves. */
    @Serializable
    @Parcelize
    data object Loading : AppRoute

    @Serializable
    @Parcelize
    // TODO: turn into a Flow
    sealed interface Onboarding : AppRoute {
        @Serializable
        data class Login(val seed: String? = null, val fromDeeplink: Boolean = false) : Onboarding
        @Serializable
        data object SeedInput : Onboarding
        @Serializable
        data object AccessKey : Onboarding
        @Serializable
        data object AccessKeySavedLocation : Onboarding
        @Serializable
        data class Purchase(val fromLogin: Boolean = false) : Onboarding

        @Serializable
        data class NotificationPermission(val postCreate: Boolean = false) : Onboarding
        @Serializable
        data class NotificationPermissionRationale(val permanentlyDenied: Boolean = false) : Onboarding

        @Deprecated("Onboarding streamlined; permissions now requested at time of use")
        @Serializable
        data class CameraPermission(val postCreate: Boolean = false) : Onboarding
    }


    @Serializable
    @Parcelize
    sealed interface Main : AppRoute {
        @Serializable
        data class AppRestricted(val restrictionType: RestrictionType) : Main
        @Serializable
        data object Scanner : Main

        // TODO: is there a better place for this to live?
        @Serializable
        data class RegionSelection(val kind: RegionSelectionKind) : Main

        @Serializable
        @Parcelize
        data class Sheet(
            val initialRoute: Sheets,
            val innerRoutes: List<AppRoute> = emptyList(),
        ) : Main, com.getcode.navigation.Sheet
    }

    @Serializable
    @Parcelize
    data class Verification(
        val origin: AppRoute,
        val target: AppRoute? = null,
        val includePhone: Boolean = true,
        val includeEmail: Boolean = true,
        val email: String? = null,
        val emailVerificationCode: String? = null,
    ) : AppRoute

    @Serializable
    @Parcelize
    sealed interface Sheets : AppRoute {
        @Serializable
        data class TokenSelection(val purpose: TokenPurpose) : Sheets
        @Serializable
        data class Give(val mint: Mint? = null, val fromTokenInfo: Boolean = false) : Sheets
        @Serializable
        data object Wallet : Sheets
        @Serializable
        data object Menu : Sheets
        @Serializable
        data object Lab : Sheets
        @Serializable
        data object ShareApp : Sheets
    }

    @Serializable
    @Parcelize
    sealed interface Token : AppRoute {
        @Serializable
        data class Info(
            val mint: Mint,
            val forNeededFunds: Boolean = false,
            val fromDeeplink: Boolean = false
        ) : Token

        @Serializable
        data class Transactions(val mint: Mint) : Token
        @Serializable
        data class SwapTransact(
            val purpose: TokenSwapPurpose,
            val forNeededFunds: Boolean = false
        ) : Token

        @Serializable
        data class TxProcessing(val swapId: SwapId, val awaitExternalWallet: Boolean = false) :
            Token, NonDismissableRoute, NonDraggableRoute

        @Serializable
        data object SellReceipt : Token

        @Serializable
        data object Discovery: AppRoute

    }
    @Serializable
    @Parcelize
    sealed interface OnRamp : AppRoute {
        @Serializable
        data class ProviderList(
            val from: AppRoute? = null,
            val neededAmount: Fiat? = null,
        ) : OnRamp

        @Serializable
        data class AmountEntry(val mint: Mint? = null) : OnRamp
    }

    @Serializable
    @Parcelize
    sealed interface Transfers : AppRoute {

        sealed interface Withdrawal {
            @Serializable
            data class Amount(val mint: Mint) : Transfers
            @Serializable
            data object Destination : Transfers
            @Serializable
            data object Confirmation : Transfers
        }
    }

    @Serializable
    @Parcelize
    sealed interface Menu : AppRoute {
        @Serializable
        data object MyAccount : Menu
        @Serializable
        data class Deposit(val mint: Mint) : Menu
        @Serializable
        data object BackupKey : Menu
        @Serializable
        data object AppSettings : Menu
        @Serializable
        data object AdvancedFeatures : Menu
        @Serializable
        data object Lab : Menu
    }

    @Serializable
    @Parcelize
    data object UserFlags : AppRoute
}
