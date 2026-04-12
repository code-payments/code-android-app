package com.flipcash.app.core

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.core.verification.VerificationStep
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.flow.FlowRouteWithResult
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
        val includePhone: Boolean = true,
        val includeEmail: Boolean = true,
        val email: String? = null,
        val emailVerificationCode: String? = null,
    ) : AppRoute, FlowRouteWithResult<VerificationResult> {
        override val initialStack: List<NavKey>
            get() = buildVerificationInitialStack(
                origin = origin,
                includePhone = includePhone,
                includeEmail = includeEmail,
                emailAddress = email,
                emailVerificationCode = emailVerificationCode,
            )
    }

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
        data class Swap(
            val purpose: SwapPurpose,
            val forNeededFunds: Boolean = false,
        ) : Token, FlowRouteWithResult<SwapResult> {
            override val initialStack: List<NavKey>
                get() = listOf(SwapStep.Entry(purpose))
        }

        @Serializable
        data class TxProcessing(val swapId: SwapId, val awaitExternalWallet: Boolean = false) :
            Token, NonDismissableRoute, NonDraggableRoute

        @Serializable
        data class OnRamp(val mint: Mint) : Token

        @Serializable
        data object Discovery: AppRoute

        @Serializable
        data object CurrencyCreator : Token, FlowRouteWithResult<CurrencyCreatorResult> {
            override val initialStack: List<NavKey>
                get() = listOf(CurrencyCreatorStep.Info)
        }
    }

    @Serializable
    @Parcelize
    sealed interface Transfers : AppRoute {

        @Serializable
        data class Withdrawal(val mint: Mint) : Transfers, FlowRouteWithResult<WithdrawalResult> {
            override val initialStack: List<NavKey>
                get() = listOf(WithdrawalStep.Amount(mint))
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
        data object DeviceLogs : Menu
        @Serializable
        data object Lab : Menu
    }

    @Serializable
    @Parcelize
    data object UserFlags : AppRoute
}

private fun buildVerificationInitialStack(
    origin: AppRoute,
    includePhone: Boolean,
    includeEmail: Boolean,
    emailAddress: String?,
    emailVerificationCode: String?,
): List<NavKey> {
    if (includePhone && includeEmail) {
        return listOf(VerificationStep.Intro(origin is AppRoute.Token.OnRamp))
    }
    if (includePhone) {
        return listOf(VerificationStep.PhoneEntry)
    }
    if (includeEmail) {
        return buildList {
            add(VerificationStep.EmailEntry)
            if (emailAddress != null && emailVerificationCode != null) {
                add(VerificationStep.EmailMagicLink(emailAddress, emailVerificationCode))
            }
        }
    }
    return emptyList()
}
