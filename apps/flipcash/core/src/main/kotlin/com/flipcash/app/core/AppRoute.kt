package com.flipcash.app.core

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.deposit.DepositResult
import com.flipcash.app.core.deposit.DepositStep
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
import com.flipcash.app.core.onboarding.OnboardingStep
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.flow.FlowRoute
import com.getcode.navigation.flow.FlowRouteWithResult
import com.getcode.opencode.exchange.VerifiedFiat
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
        data class ContactPermission(val postCreate: Boolean): Onboarding
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
    data class OnboardingFlow(
        val phase: Phase = Phase.Account,
        val seed: String? = null,
        val fromDeeplink: Boolean = false,
        val resumeAt: ResumePoint = ResumePoint.Login,
        val skipContacts: Boolean = false,
    ) : AppRoute, FlowRoute {
        enum class Phase { Account, Permissions }
        enum class ResumePoint { Login, AccessKey, AccessKeyThenPurchase, PostAccessKey }

        override val initialStack: List<NavKey>
            get() = when (phase) {
                Phase.Account -> when (resumeAt) {
                    ResumePoint.Login -> listOf(OnboardingStep.Start(seed, fromDeeplink))
                    ResumePoint.AccessKey -> listOf(OnboardingStep.Start(), OnboardingStep.AccessKey)
                    ResumePoint.AccessKeyThenPurchase ->
                        listOf(OnboardingStep.Start(), OnboardingStep.AccessKey, OnboardingStep.Purchase)
                    ResumePoint.PostAccessKey -> emptyList()
                }
                Phase.Permissions -> listOf(OnboardingStep.ContactPermission, OnboardingStep.NotificationPermission)
            }
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
        data object RegionSelection : Main

        @Serializable
        data class InviteContact(val phoneNumber: String) : com.getcode.navigation.Sheet, com.getcode.navigation.WrapContentSheet

        @Serializable
        @Parcelize
        data class Sheet(
            val initialRoute: AppRoute,
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
        val target: AppRoute? = null,
        val fullScreen: Boolean = false,
        val linkForPayment: Boolean = false,
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

        /**
         * Direct send flow — phone-verified user picks a contact and sends funds.
         *
         * @param resumed `true` when the flow is re-entered after an interrupting gate
         *   (e.g. phone verification). A distinct value produces a new route instance so
         *   Nav3 treats `replaceAll` as a forward push instead of a pop.
         */
        @Serializable
        data class Send(val resumed: Boolean = false): Sheets
        @Serializable
        data object Wallet : Sheets
        @Serializable
        data object Menu : Sheets

        @Serializable
        data object ShareApp : Sheets

    }

    @Serializable
    @Parcelize
    sealed interface Token : AppRoute {
        @Serializable
        data class Info(
            val mint: Mint,
            val shortfall: Fiat? = null,
            val fromDeeplink: Boolean = false
        ) : Token

        @Serializable
        data class Transactions(val mint: Mint) : Token
        @Serializable
        data class Swap(
            val purpose: SwapPurpose,
            val shortfall: Fiat? = null,
        ) : Token, FlowRouteWithResult<SwapResult> {
            override val initialStack: List<NavKey>
                get() = listOf(SwapStep.Entry(purpose, initialAmount = shortfall))
        }

        @Serializable
        data object PhantomConnectInfo: Token

        @Serializable
        data object PhantomConfirmTransaction: Token

        @Serializable
        data class TxProcessing(
            val swapId: SwapId,
            val swapPurpose: SwapPurpose? = null,
            val amount: VerifiedFiat? = null,
            val isFundingShortfall: Boolean = false,
        ) : Token, NonDismissableRoute, NonDraggableRoute

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
        data class Deposit(val showOtherOptions: Boolean = true): Transfers, FlowRouteWithResult<DepositResult> {
            override val initialStack: List<NavKey>
                get() =  listOf(DepositStep.UsdcInformational(showOtherOptions))
        }

        @Serializable
        data class Withdrawal(val showOtherOptions: Boolean = true) : Transfers, FlowRouteWithResult<WithdrawalResult> {
            override val initialStack: List<NavKey>
                get() =  listOf(WithdrawalStep.UsdcInformational(showOtherOptions))
        }
    }

    @Serializable
    @Parcelize
    sealed interface Menu : AppRoute {
        @Serializable
        data object MyAccount : Menu
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
        @Serializable
        data object NavBarSettings : Menu, com.getcode.navigation.Sheet, com.getcode.navigation.WrapContentSheet
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
        return listOf(VerificationStep.Intro(origin is AppRoute.Token.Swap))
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
