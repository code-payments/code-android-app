package com.flipcash.app.core

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.deposit.DepositResult
import com.flipcash.app.core.deposit.DepositStep
import com.flipcash.app.core.onboarding.OnboardingStep
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.navigation.SteppedFlowRoute
import com.flipcash.app.core.userprofile.UpdateProfileResult
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.core.verification.VerificationResult
import com.flipcash.app.core.verification.VerificationStep
import com.flipcash.app.core.withdrawal.WithdrawalResult
import com.flipcash.app.core.withdrawal.WithdrawalStep
import com.getcode.navigation.flow.FlowRoute
import com.getcode.navigation.flow.FlowRouteWithResult
import com.getcode.navigation.flow.FlowStep
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.ui.core.RestrictionType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

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
        val skipContacts: Boolean = true,
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
                Phase.Permissions -> buildList {
                    if (!skipContacts) add(OnboardingStep.ContactPermission)
                    add(OnboardingStep.NotificationPermission)
                }
            }
    }

    /**
     * Full-screen destinations pushed onto whichever stack the user is already on.
     *
     * The distinction that matters at navigation time is how a route arrives: a [Tabs] home
     * replaces the stack, a [Sheets] route is presented over it in a modal, and everything here
     * is an ordinary push.
     */
    @Serializable
    @Parcelize
    sealed interface Main : AppRoute {

        @Serializable
        data class AppRestricted(val restrictionType: RestrictionType) : Main

        // TODO: is there a better place for this to live?
        @Serializable
        data object RegionSelection : Main

        /** The give/cash screen, reached from the wallet tile and from a token's info screen. */
        @Serializable
        data class Give(val mint: Mint? = null, val fromTokenInfo: Boolean = false) : Main

        /** Full unified paged activity history — the "dive in" from the wallet's recent-activity preview. */
        @Serializable
        data object ActivityHistory : Main

        /**
         * One activity entry, opened from its row (Figma node 9708:105260).
         *
         * Carries the entry's id rather than the row that was tapped: the screen re-reads the entry
         * and stays live on it, so a cash link cancelled from this screen's own app bar redraws the
         * screen the cancel was issued from.
         */
        @Serializable
        data class TransactionDetails(val id: ID) : Main

        /**
         * A modal needing no nested navigation, so it carries the sheet marker directly rather
         * than going through [Sheets] and [Sheet].
         */
        @Serializable
        data class InviteContact(val phoneNumber: String) : com.getcode.navigation.Sheet, com.getcode.navigation.WrapContentSheet

        /**
         * A [Sheets] route hosted in a modal bottom sheet, its nested navigator seeded with
         * [initialRoute] followed by [innerRoutes].
         *
         * Built by `openAsSheet`/`resolveRoutes`; nothing navigates to it directly.
         */
        @Serializable
        @Parcelize
        data class Sheet(
            val initialRoute: AppRoute,
            val innerRoutes: List<AppRoute> = emptyList(),
        ) : Main, com.getcode.navigation.Sheet
    }

    /**
     * The four tab homes the hoisted nav bar swaps between.
     *
     * A tab home replaces the current stack rather than stacking on it, which is the whole reason
     * it is a type and not just a convention — `navigateAll` reads it off
     * [com.flipcash.app.core.navigation.asNavBarTab], which is total over this hierarchy.
     */
    @Serializable
    @Parcelize
    sealed interface Tabs : AppRoute {
        @Serializable
        data object Scanner : Tabs

        @Serializable
        data object Wallet : Tabs

        /** Home of the chats tab, seeded at the chats list. */
        @Serializable
        data class Tips(val resumed: Boolean = false) : Tabs

        /** The "You" tab: the menu (settings) surface, augmented with the tip card and share. */
        @Serializable
        data object Menu : Tabs
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
                forOnRamp = target is Token.Swap,
                includePhone = includePhone,
                includeEmail = includeEmail,
                emailAddress = email,
                emailVerificationCode = emailVerificationCode,
            )
    }

    @Serializable
    @Parcelize
    /**
     * The profile editor, as an ordered list of the [steps] the caller wants. Each step carries its
     * own parameters, so asking for a subset costs nothing beyond a shorter list.
     */
    data class UpdateUserProfile(
        val origin: AppRoute,
        val steps: List<UpdateProfileStep>,
        val target: AppRoute? = null,
        // When false, the first step has no back affordance and system back is swallowed —
        // used in onboarding where display-name entry is a mandatory, non-dismissable step.
        val allowBack: Boolean = true,
    ): AppRoute, FlowRouteWithResult<UpdateProfileResult> {
        override val initialStack: List<NavKey>
            get() = steps
    }

    /**
     * Modal bottom sheets that host their own inner backstack.
     *
     * Membership is what makes `resolveRoutes` wrap the route in [Main.Sheet], seeding that
     * sheet's nested navigator with whatever routes followed it — that packing is the only thing
     * this grouping buys, so a modal with no nested navigation does not belong here. Such a
     * modal implements [com.getcode.navigation.Sheet] directly instead ([Main.InviteContact],
     * [com.flipcash.app.core.chat.ChatStep.AmountEntry]); that marker, not this interface, is what
     * the scene strategy reads to present a route as a sheet at all.
     */
    @Serializable
    @Parcelize
    sealed interface Sheets : AppRoute {
        @Serializable
        data class TokenSelection(val purpose: TokenPurpose) : Sheets

        /**
         * Custom tip-amount entry, opened over the still-visible tip card + modal. The entered
         * amount is written back to the shared tip selection so the modal reflects it on dismiss.
         */
        @Serializable
        data object TipAmountEntry : Sheets

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
            val fromDeeplink: Boolean = false,
            // A normal stack PUSH (slide in, back arrow) rather than the wallet card-expand presentation
            // (fade-in-place, ✕ dismiss). Set when the screen is reached by drilling in from a list — e.g.
            // token discovery — where a back arrow that slides back is the expected navigation.
            val asPush: Boolean = false,
        ) : Token

        @Serializable
        data class Transactions(val mint: Mint) : Token
        @Serializable
        data class Swap(
            val purpose: SwapPurpose,
            val shortfall: Fiat? = null,
            val popToRoot: Boolean = false,
        ) : Token, FlowRouteWithResult<SwapResult> {
            override val initialStack: List<NavKey>
                get() = when (purpose) {
                    is SwapPurpose.Buy -> {
                        if (purpose.fundingSource == FundingSource.Phantom) {
                            // adding money (deposit) via phantom
                            listOf(SwapStep.PhantomConnect)
                        } else {
                            listOf(SwapStep.Entry(purpose, initialAmount = shortfall))
                        }
                    }
                    is SwapPurpose.Sell -> listOf(SwapStep.Entry(purpose, initialAmount = shortfall))
                    is SwapPurpose.Convert -> listOf(SwapStep.Entry(purpose, initialAmount = shortfall))
                }



        }

        @Serializable
        data object PhantomConnectInfo: Token

        @Serializable
        data object PhantomConfirmTransaction: Token


        @Serializable
        data object Discovery: AppRoute

        @Serializable
        data object CurrencyCreator : Token, SteppedFlowRoute<CurrencyCreatorResult> {
            override val initialStack: List<NavKey>
                get() = listOf(CurrencyCreatorStep.Info)

            override val progressSteps: List<KClass<out FlowStep>>
                get() = listOf(
                    CurrencyCreatorStep.NameSelection::class,
                    CurrencyCreatorStep.IconSelection::class,
                    CurrencyCreatorStep.DescriptionSelection::class,
                    CurrencyCreatorStep.BillCustomization::class,
                    CurrencyCreatorStep.BillReview::class,
                )
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

        /**
         * The withdraw flow.
         *
         * [preselectedMint] picks the entry step: `null` opens the currency picker (the
         * "Withdraw Money" tile and the settings entry), Dollars/USDF detours through the
         * "Withdraw as USDC" intro, and any other currency lands straight on the amount screen.
         */
        @Serializable
        data class Withdrawal(
            val preselectedMint: Mint? = Mint.usdf,
        ) : Transfers, FlowRouteWithResult<WithdrawalResult> {
            override val initialStack: List<NavKey>
                get() = when (preselectedMint) {
                    null -> listOf(WithdrawalStep.SelectToken)
                    // The flow models USDF→USDC as a USDC withdrawal, so both mints mean the reserve.
                    Mint.usdf, Mint.usdc -> listOf(WithdrawalStep.UsdcInformational)
                    else -> listOf(WithdrawalStep.Amount(preselectedMint))
                }
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
        data object Blocklist: Menu
        @Serializable
        data object AdvancedFeatures : Menu
        @Serializable
        data object DeviceLogs : Menu
        @Serializable
        data object UserProfile : Menu
        @Serializable
        data class Lab(val onboarding: Boolean = false) : Menu
    }

    @Serializable
    @Parcelize
    sealed interface Messaging : AppRoute {
        @Serializable
        data class Chat(
            val identifier: ChatIdentifier,
            // Open straight into composing a reply with the keyboard up. Only the post-tip
            // hand-off (see TipCardDecorator) sets this; normal opens default to keyboard-closed.
            val openKeyboard: Boolean = false,
        ) : Messaging, FlowRoute {
            override val initialStack: List<NavKey>
                get() = listOf(ChatStep.Conversation)
        }

        /**
         * Starting a chat by typing someone's `@handle` (node 9442:5825), reached from the "+" on
         * the Chats list.
         *
         * A top-level route rather than a step of the tipping flow, even though the Chats list it
         * is reached from is one: the flow is a tab home, so a step pushed inside it keeps the tab
         * bar. This covers it, the way [Chat] does.
         */
        @Serializable
        @Parcelize
        data object NewChat : Messaging
    }

    @Serializable
    @Parcelize
    data object UserFlags : AppRoute
}

private fun buildVerificationInitialStack(
    forOnRamp: Boolean,
    includePhone: Boolean,
    includeEmail: Boolean,
    emailAddress: String?,
    emailVerificationCode: String?,
): List<NavKey> {
    if (includePhone && includeEmail) {
        return listOf(VerificationStep.Intro(forOnRamp))
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

// Ordered list of the steps the flow should walk (via FlowNavigator.proceed()) — name, then
// username, then photo. In edit mode only the requested step(s) are included.
/** Where a display-name entry flow was launched from. Reported as the `Source` analytics property. */
@Serializable
enum class DisplayNameSource { Onboarding, MyAccount, TipCardSetup }
