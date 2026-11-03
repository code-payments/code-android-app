package com.flipcash.app.core

import com.getcode.libs.parcelable.Parcelable
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.core.reporting.ReportStep
import com.flipcash.app.core.chat.NewGroupStep
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.app.core.deposit.DepositResult
import com.flipcash.app.core.deposit.DepositStep
import com.flipcash.app.core.onboarding.OnboardingStep
import com.flipcash.app.core.tokens.CurrencyCreatorResult
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.tokens.SwapStep
import com.flipcash.app.core.tokens.TokenInfoEntry
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
import com.getcode.libs.parcelable.Parcelize
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

    @Serializable
    @Parcelize
    sealed interface Main : AppRoute {

        @Serializable
        data class AppRestricted(val restrictionType: RestrictionType) : Main

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
     * The homes of the hoisted nav bar's tabs. They sit flat on the root backstack, so the bar stays
     * visible over them, and a route list leading with one is applied as a tab switch (see
     * `navigateAll`). `NavBarRoutes.kt` maps each to its `NavBarButton`.
     */
    @Serializable
    @Parcelize
    sealed interface Tabs : AppRoute {
        @Serializable
        data object Scanner : Tabs

        @Serializable
        data object Wallet : Tabs

        /** Tip DMs and groups. */
        @Serializable
        data object Chats : Tabs

        /** Settings, plus the user's tip card. The nav bar labels it "You". */
        @Serializable
        data object Menu : Tabs
    }

    @Serializable
    @Parcelize
    sealed interface Sheets : AppRoute {
        @Serializable
        data class TokenSelection(val purpose: TokenPurpose) : Sheets
        @Serializable
        data class Give(
            val mint: Mint? = null,
            val fromTokenInfo: Boolean = false,
            /**
             * An amount in the viewer's preferred currency to give as-is. When set, the screen
             * skips its own amount entry and goes straight to the bill.
             */
            val amount: Double? = null,
        ) : Sheets

        /**
         * Send from Currency Info: an amount of [mint], then a recipient from the viewer's DMs.
         * Dismisses back to Currency Info after the send.
         */
        @Serializable
        data class CurrencySend(val mint: Mint) : Sheets

        /** Full unified paged activity history — the "dive in" from the wallet's recent-activity preview. */
        @Serializable
        data object ActivityHistory : Sheets

        /**
         * One activity entry, opened from its row (Figma node 9708:105260).
         *
         * Carries the entry's id rather than the row that was tapped: the screen re-reads the entry
         * and stays live on it, so a cash link cancelled from this screen's own app bar redraws the
         * screen the cancel was issued from.
         */
        @Serializable
        data class TransactionDetails(val id: ID) : Sheets

        @Serializable
        data object ShareApp : Sheets

    }

    @Serializable
    @Parcelize
    sealed interface Token : AppRoute {
        @Serializable
        data class Info(
            val mint: Mint,
            val entry: TokenInfoEntry = TokenInfoEntry.Wallet,
        ) : Token

        @Serializable
        data class Transactions(val mint: Mint) : Token
        @Serializable
        data class MarketCapExplainer(val mint: Mint) : Token
        /** "Top Community Chats": View All from Currency Info. */
        @Serializable
        data class CommunityChats(val mint: Mint) : Token
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
        data class Deposit(
            val showOtherOptions: Boolean = true,
            /** When set, opens straight on that currency's deposit address instead of the USDC intro. */
            val mint: Mint? = null,
        ): Transfers, FlowRouteWithResult<DepositResult> {
            override val initialStack: List<NavKey>
                get() = mint?.let { listOf(DepositStep.Destination(it)) }
                    ?: listOf(DepositStep.UsdcInformational(showOtherOptions))
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
        data object Settings : Menu

        /** Your cover, photo, name, handle, bio and minimum to chat, each opening its editor. */
        @Serializable
        data object EditProfile : Menu
        @Serializable
        data object BackupKey : Menu

        @Serializable
        data object Blocklist: Menu
        @Serializable
        data object TrustedWebsites : Menu
        @Serializable
        data object DeviceLogs : Menu
        @Serializable
        data object LinkDesktop : Menu
        data object NotificationStatus : Menu
        @Serializable
        data object AccountSelection : Menu
        @Serializable
        data object UserProfile : Menu

        /**
         * [userId]'s profile card, full screen, with its Download action. Opened from the
         * Show Profile Card tile of [Messaging.ShareProfileToChats], for the viewer's own profile
         * and for anyone else's. A [com.getcode.navigation.FullscreenOverlay], so that sheet stays
         * open beneath it.
         */
        @Serializable
        data class ProfileCard(val userId: ID) : Menu, com.getcode.navigation.FullscreenOverlay

        /**
         * A group's scan card: its invite link as a QR, full screen, with Download. Opened from the
         * Show QR Code tile of the group invite sheet, which stays open beneath it. The group's
         * identity rides on the route because the host screen already holds it and the card makes
         * no request of its own.
         */
        @Serializable
        data class GroupCard(
            val chatId: com.flipcash.services.models.chat.ChatId,
            val title: String?,
            val picture: com.flipcash.services.models.chat.MediaItem?,
            val memberCount: Long,
        ) : Menu, com.getcode.navigation.FullscreenOverlay

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
            // Open on the group's profile instead of the transcript. Set where a group is shown as
            // something to look at rather than a chat to resume: a profile's featured groups. The
            // profile is the flow's root, so back returns to wherever the tap came from, and Open
            // Chat pushes the transcript on top of it.
            val openOnProfile: Boolean = false,
            // Open on a private group's Join Requests as the flow's root, so back returns to the
            // Requests screen that set it, the way iOS does.
            val openOnJoinRequests: Boolean = false,
            // Opened from a group invite: a tapped or scanned /chat link, or an invite card. A chat
            // the server has no record of is then a dead link, so the flow says so and closes. Any
            // other open keeps the empty chat, because a DM on its derived id has no record either
            // until someone writes in it.
            val isGroupLink: Boolean = false,
            // Open scrolled to this message (its per-chat sequence number) and flash it: a message
            // link's landing.
            val jumpToMessageId: Long? = null,
            // The title of the chat a message link was followed from, when it was followed from
            // inside the app. The conversation offers a chip back to it; a link from outside the
            // app has nowhere to go back to and sets none.
            val returnToTitle: String? = null,
            // Open with this request's details (its id in base58) over the transcript: where a
            // payment of a share leads back to its request. Needs a [ChatIdentifier.ByChatId], since
            // the details are keyed by chat.
            val openRequestId: String? = null,
        ) : Messaging, FlowRoute {
            override val initialStack: List<NavKey>
                get() = when {
                    openOnJoinRequests -> listOf(ChatStep.JoinRequests)
                    openOnProfile -> listOf(ChatStep.GroupProfile)
                    openRequestId != null && identifier is ChatIdentifier.ByChatId ->
                        listOf(ChatStep.Conversation, ChatStep.RequestDetails(identifier.chatId, openRequestId))
                    else -> listOf(ChatStep.Conversation)
                }
        }

        /**
         * A group invite link opened by the joiner (nodes 12846:59 to 12846:259). [secret] is the
         * link's key material, so it is redacted from [toString]; null is a link with no valid
         * secret, which shows the broken-link state rather than a chat.
         */
        @Serializable
        @Parcelize
        class GroupInvite(val secret: ByteArray?) : Messaging {
            override fun equals(other: Any?): Boolean =
                other is GroupInvite && secret.contentEquals(other.secret)
            override fun hashCode(): Int = secret.contentHashCode()
            override fun toString(): String = "GroupInvite(redacted)"
        }

        /**
         * Node 10127:117987 — the two ways to start a chat, reached from the "+" on the Chats list.
         *
         * A top-level route rather than something drawn inside [Tabs.Chats]: that is a tab home, so
         * anything inside it keeps the tab bar. This covers it, the way [Chat] does. [FindByUsername] and [NewGroup] are pushed on
         * top of it for the same reason.
         */
        @Serializable
        @Parcelize
        data object NewChat : Messaging

        /**
         * Search across chats, currencies, messages and links (node 12248:72), behind the search
         * button on the Chats list. Pushed for the reason [NewChat] is.
         */
        @Serializable
        @Parcelize
        data object Search : Messaging

        /**
         * The chats the viewer archived, behind the Archived row on the Chats list.
         *
         * A pushed route rather than an in-place screen, for the reason [NewChat] is: the Chats
         * list is a tab home, and anything drawn inside it would leave the tab bar over this one.
         */
        @Serializable
        @Parcelize
        data object ArchivedChats : Messaging

        /**
         * Join requests in both directions, behind the Requests button on the Chats header. Pushed
         * for the reason [ArchivedChats] is.
         */
        @Serializable
        @Parcelize
        data object Requests : Messaging

        /**
         * Another person's profile, opened on its own rather than from inside a chat — the
         * destination of a `flipcash.com/{username}` or `flipcash.com/{uuid}` link.
         *
         * A top-level route rather than [ChatStep.Profile]: that step belongs to a conversation
         * and reads the person off the chat's view model, and a link names a person with no
         * conversation behind it. Its Message shortcut pushes [Chat] on top of it.
         *
         * Not [Menu.UserProfile], which is the viewer's own account.
         */
        @Serializable
        @Parcelize
        data class Profile(
            val address: ProfileAddress,
            val origin: ProfileOrigin = ProfileOrigin.Link,
        ) : Messaging

        /** Node 9442:5825 — starting a chat by typing someone's `@handle`. */
        @Serializable
        @Parcelize
        data object FindByUsername : Messaging

        /**
         * Node 10153:22901 — creating a public group, from the empty form to its invite link; node
         * 11203:65 is the same flow for a private one.
         *
         * The three screens share one draft, so they are steps of a flow rather than routes; see
         * [NewGroupStep].
         */
        @Serializable
        @Parcelize
        data class NewGroup(
            /** Node 11203:65: approval to join, end-to-end encrypted, no balance requirement. */
            val isPrivate: Boolean = false,
        ) : Messaging, FlowRoute {
            override val initialStack: List<NavKey>
                get() = listOf(NewGroupStep.Form)
        }

        /**
         * Reporting a person, a group, or a single message.
         *
         * A top-level route rather than a step of [Chat], although every way in is a chat surface.
         * Reporting a person is about the person, not the conversation it was opened from, and the
         * flow outlives the surface: it is reachable from a profile, a group profile, and the
         * selection bar, and sharing one route means those three cannot drift apart. It also keeps
         * a half-written report out of the chat's view model.
         *
         * A [com.getcode.navigation.FullscreenSheet] without `WrapContentSheet`: it rests at a
         * fixed height and stays there while the steps change. The wrap-content version of this
         * deadlocked — such a sheet derives its drag anchors from the height its content reports
         * and reads them again while placing that content, so content that resizes itself never
         * settles. Fullscreen rather than the usual expanded detent because both steps are worked
         * through rather than glanced at, and the second is a text field with the keyboard up.
         */
        @Serializable
        @Parcelize
        data class Report(val subject: ReportSubject) :
            Messaging,
            FlowRoute,
            com.getcode.navigation.Sheet,
            com.getcode.navigation.FullscreenSheet {
            override val initialStack: List<NavKey>
                get() = listOf(ReportStep.ReasonSelection)
        }

        /**
         * How long to mute [chatId] for.
         *
         * A top-level route carrying the chat's id, for the reason [Report] is one: it is opened
         * both from inside a chat (the DM and group profiles) and from the chat list, which has no
         * chat flow behind it to ask which chat is meant. One route for both keeps the sheet and
         * the mute request in one place.
         *
         * A [com.getcode.navigation.WrapContentSheet]: five rows at most, and whatever opened it is
         * where you end up again once one is picked.
         */
        @Serializable
        @Parcelize
        data class MuteChat(val chatId: ChatId, val chatType: ChatType) :
            Messaging,
            com.getcode.navigation.Sheet,
            com.getcode.navigation.WrapContentSheet

        /**
         * How long to silence [userId] in the group [chatId]. Carries the words the sheet shows
         * ([firstName], [groupTitle]) so it needs no lookup before it can draw. Presented like
         * [MuteChat], for the same reason.
         */
        @Serializable
        @Parcelize
        data class SilenceUser(
            val chatId: ChatId,
            val userId: ID,
            val firstName: String,
            val groupTitle: String,
        ) : Messaging,
            com.getcode.navigation.Sheet,
            com.getcode.navigation.WrapContentSheet

        /**
         * Where a share from another app picks the conversation it goes to. Opened by the app
         * itself when a share is waiting in `PendingChatShares`, and sends the share from there
         * to the chats picked. Carries nothing: what was shared is held there, not on a route a back stack
         * would save and restore.
         */
        @Serializable
        @Parcelize
        data object SharePicker : Messaging

        /**
         * A person's profile link, shared by the system share sheet, copied, or sent into recent
         * chats. Opened from the viewer's own profile and from someone else's, so it carries what
         * the link and the share card are built from.
         *
         * @param directChatId the viewer's DM with the person, left out of the picker so nobody is
         * sent their own profile. Null for the viewer's own profile, and for a person with no DM
         * yet.
         *
         * Full screen, as iOS presents it as a cover.
         */
        @Serializable
        @Parcelize
        data class ShareProfileToChats(
            val userId: ID,
            val username: String?,
            val displayName: String?,
            val directChatId: ChatId?,
        ) : Messaging, com.getcode.navigation.Sheet, com.getcode.navigation.FullscreenSheet

        /** "Share <name>" from Currency Info: the page link shared, copied, or sent into recent chats. */
        @Serializable
        @Parcelize
        data class ShareCurrencyToChats(
            val mint: Mint,
            val name: String,
        ) : Messaging, com.getcode.navigation.Sheet, com.getcode.navigation.FullscreenSheet

        /**
         * What end-to-end encryption covers in a DM, opened from the DM profile's footer. A
         * [com.getcode.navigation.WrapContentSheet], for the reason [MuteChat] is one.
         */
        @Serializable
        @Parcelize
        data object E2eeDmInfo :
            Messaging,
            com.getcode.navigation.Sheet,
            com.getcode.navigation.WrapContentSheet

        /** The group counterpart of [E2eeDmInfo], opened from Group Info's footer. */
        @Serializable
        @Parcelize
        data object E2eeGroupInfo :
            Messaging,
            com.getcode.navigation.Sheet,
            com.getcode.navigation.WrapContentSheet

        /** [E2eeGroupInfo] for a private group, which is end-to-end encrypted (node 11210:71). */
        @Serializable
        @Parcelize
        data object E2eePrivateGroupInfo :
            Messaging,
            com.getcode.navigation.Sheet,
            com.getcode.navigation.WrapContentSheet
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
