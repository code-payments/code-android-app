package com.flipcash.app.core

import android.os.Parcelable
import cafe.adriel.voyager.core.registry.ScreenProvider
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.core.transfers.TransferDirection
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.ui.core.RestrictionType
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface AppRoute : ScreenProvider, Parcelable {

    @Parcelize
    // TODO: turn into a Flow
    sealed interface Onboarding: AppRoute {
        data class Login(val seed: String? = null, val fromDeeplink: Boolean = false) : Onboarding
        data object SeedInput : Onboarding
        data object AccessKey : Onboarding
        data object AccessKeySavedLocation: Onboarding
        data class Purchase(val fromLogin: Boolean = false) : Onboarding
        @Deprecated("Onboarding streamlined; permissions now requested at time of use")
        data class NotificationPermission(val postCreate: Boolean = false) : Onboarding
        @Deprecated("Onboarding streamlined; permissions now requested at time of use")
        data class CameraPermission(val postCreate: Boolean = false) : Onboarding
    }


    @Parcelize
    sealed interface Main: AppRoute {
        data class AppRestricted(val restrictionType: RestrictionType) : Main
        data class Scanner(val deeplink: DeeplinkType? = null) : Main

        // TODO: is there a better place for this to live?
        data class RegionSelection(val kind: RegionSelectionKind) : Main

        data class Give(val mint: Mint? = null) : Main
    }

    @Parcelize
    data class Verification(
        val origin: AppRoute,
        val target: AppRoute? = null,
        val includePhone: Boolean = true,
        val includeEmail: Boolean = true,
        val email: String? = null,
        val emailVerificationCode: String? = null,
    ): AppRoute

    @Parcelize
    sealed interface Sheets: AppRoute {
        data class TokenSelection(val purpose: TokenPurpose): Sheets
        data object Wallet : Sheets
        data object Menu : Sheets
        data object Lab: Sheets
        data object ShareApp : Sheets
    }

    @Parcelize
    sealed interface Token: AppRoute {
        data class Info(val mint: Mint, val forNeededFunds: Boolean = false, val fromDeeplink: Boolean = false): Token
        data class Transactions(val mint: Mint): Token
        data class SwapTransact(val purpose: TokenSwapPurpose, val forNeededFunds: Boolean = false): Token
        data class TxProcessing(val swapId: SwapId): Token
        data object SellReceipt: Token
    }

    @Parcelize
    sealed interface OnRamp: AppRoute {
        data class ProviderList(
            val from: AppRoute? = null,
            val neededAmount: Fiat? = null,
        ) : OnRamp

        data object AmountEntry: OnRamp
    }

    @Parcelize
    sealed interface Transfers: AppRoute {

        sealed interface Withdrawal {
            data class Amount(val mint: Mint) : Transfers
            data object Destination : Transfers
            data object Confirmation : Transfers
        }
    }

    @Parcelize
    sealed interface Menu: AppRoute {
        data object MyAccount : Menu
        data class Deposit(val mint: Mint) : Menu
        data object BackupKey : Menu
        data object AppSettings : Menu
        data object AdvancedFeatures : Menu
        data object Lab : Menu
    }

    @Parcelize
    sealed interface Advanced: AppRoute
}