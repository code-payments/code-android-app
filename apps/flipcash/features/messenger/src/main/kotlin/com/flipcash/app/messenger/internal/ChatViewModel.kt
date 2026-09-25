package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import com.flipcash.analytics.AddMoneySource
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.AddMoneyEvents
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.tokens.brandedName
import com.flipcash.app.core.tokens.isReserve
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.messenger.internal.link.CashCardTap
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.app.messenger.internal.link.ClaimReplyTargets
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.session.CashLinkClaims
import com.flipcash.app.session.SettledClaim
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.DeliveryStatus
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.isDmAddressable
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ActiveTypist
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatDraftSnapshot
import com.flipcash.shared.chat.ChatDraftStore
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.MessagePolicy
import com.flipcash.shared.chat.applying
import com.flipcash.shared.chat.chatDraftOf
import com.flipcash.shared.chat.groupAccess
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.models.splitAroundLinkCard
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.shared.chat.readOnly
import com.flipcash.shared.chat.resolveCapabilities
import com.flipcash.shared.chat.ui.detectUrls
import com.flipcash.shared.chat.ui.linkableText
import com.flipcash.shared.chat.withinWindows
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.ui.utils.generateComplementaryColorPalette
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TypingConstraints(
    val enabled: Boolean = false,
    // False until the async typing-enabled query (does this chat have a cash message?) has
    // answered at least once. The bottom bar keeps its layout invisible until this is true so it
    // never renders the default full-width state and then snaps to the resolved pill + input.
    val resolved: Boolean = false,
    val interval: Duration = 3.seconds,
    val timeout: Duration = 5.seconds,
)

@HiltViewModel
internal class ChatViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val contactCoordinator: ContactCoordinator,
    private val contactPaymentDelegate: ContactPaymentDelegate,
    private val tipPaymentDelegate: TipPaymentDelegate,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val purchaseMethodController: PurchaseMethodController,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
    private val clipboardManager: ClipboardManager,
    private val userFlags: UserFlagsCoordinator,
    private val linkCardClassifier: LinkCardClassifier,
    private val linkCardResolver: LinkCardResolver,
    private val cashLinkClaims: CashLinkClaims,
    private val chatDraftStore: ChatDraftStore,
    dispatchers: DispatcherProvider,
) : BaseViewModel<ChatViewModel.State, ChatViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    sealed interface ResolveState {
        data object Pending : ResolveState
        // The counterparty resolved to an on-chain address, so the send can proceed. The address
        // itself isn't held here — the payment delegates re-resolve it at send time (a cache hit),
        // keyed by the counterparty's phone number or user id.
        data object Resolved : ResolveState
        data object Failed : ResolveState
    }

    data class State(
        val separatorConfig: SeparatorConfig = SeparatorConfig.Continuous(),
        val chatId: ChatId? = null,
        val subject: ChatSubject? = null,
        /**
         * What this chat holds about the viewer, or null while nothing is known.
         *
         * On the chat rather than on the [ChatSubject] because it is not about who the
         * conversation is with: a DM is muted exactly the way a group is, and both profiles offer
         * the row. Carried whole rather than reduced to a muted flag — a timed mute lapses with
         * nothing sent to say so, so what is muted depends on when it is asked, and `isMutedAt`
         * needs the deadline this keeps.
         */
        val viewerState: ViewerState? = null,
        // The kind of DM this conversation is, resolved from the fast local contact lookup ahead of
        // the participant's server profile (which resolves over the network for tip DMs). Starts
        // UNKNOWN and settles to CONTACT_DM / TIP_DM as soon as the chat opens; the send button and
        // bottom bar read it to render the correct (condensed vs expanded) presentation immediately
        // instead of flashing the expanded white pill while a tip profile loads.
        val chatType: ChatType = ChatType.UNKNOWN,
        val chatInputState: TextFieldState = TextFieldState(),
        val typists: Set<ActiveTypist> = emptySet(),
        /** What the typing indicator draws ahead of its dots. See [typingAvatars]. */
        val typingAvatars: List<TypingAvatar> = emptyList(),
        val resolveState: ResolveState = ResolveState.Pending,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
        val isSelfTyping: Boolean = false,
        val typingConstraints: TypingConstraints = TypingConstraints(),
        val token: Token? = null,
        val limits: Limits? = null,
        val isAnonymous: Boolean = false,
        val cashSymbol: String = "$",
        /**
         * The recipient's fee to open a DM with them, already formatted, or null when there is no
         * such fee to name — a contact DM, a tip DM that already exists, or the moment before the
         * profile has resolved. The call-to-action pill renders on the chat's first frame and this
         * arrives over the network, so "no fee yet" and "no fee at all" are deliberately the same
         * value: both mean the pill falls back to its unpriced label.
         */
        val chatInitFee: String? = null,
        /**
         * Whether [Event.OnSendCash] would now take the path it will keep: the fee sheet for the
         * payment that opens a tip DM, the keypad for any other. See [isSendCashReady].
         *
         * Only a chat opened with send cash already started reads it. [chatInitFee] can't answer
         * this, because it is null both before the fee resolves and when there is no fee at all.
         */
        val sendCashReady: Boolean = false,
        // Transient "focus the message input" request. Set by OnStartMessageInput (dispatched when
        // returning from amount entry after a send, and on a post-tip chat open) and cleared by
        // OnMessageInputConsumed once the bottom bar has focused the field and shown the keyboard.
        // Kept as state (not a one-shot event) because eventFlow is replay-0: a request raised at
        // open would be missed by the bottom bar before it subscribes, whereas state is durable
        // until the input is actually composed and can consume it.
        val messageInputRequested: Boolean = false,
        /**
         * The message the selection bar is acting on, or `null` when the ordinary title bar is up.
         *
         * One message at a time: every capability the transcript resolves — copy, edit, delete —
         * applies to a single message, so a multi-selection would only ever be a bar with most of
         * its actions disabled.
         */
        val selection: ChatListItem.ContentBubble? = null,
        /** The message the composer is editing, or `null` when it is composing a new one. */
        val editing: EditingMessage? = null,
        /**
         * The message the composer is citing, or `null` when it is composing an ordinary message.
         *
         * Mutually exclusive with [editing]: an edit takes the composer over with the message's own
         * body, so citing another message from inside one would send a reply that overwrites a
         * third. Unlike [EditingMessage] this stashes no draft — the draft is the reply.
         *
         * Cleared by the send handler rather than by the reducer: dispatchEvent reduces before it
         * emits, so a reducer that cleared it would empty it before the handler could read it.
         */
        val replyingTo: ChatQuote? = null,
        /**
         * A message the transcript has been asked to scroll to, held until the list consumes it.
         *
         * State rather than a one-shot event, for the reason [messageInputRequested] is: eventFlow
         * is replay-0, so a request raised while the list is recomposing would be dropped.
         */
        val jumpTarget: Long? = null,
        /**
         * How far back [jumpTarget] sits from the newest message, which bounds the walk that loads
         * it. Set alongside [jumpTarget] and cleared with it.
         */
        val jumpBudget: Int? = null,
        /**
         * Where the "N Unread Messages" divider sits, read once per visit from the viewer's stored
         * READ pointer. Never re-read: the list advances that pointer as the reader scrolls, and a
         * boundary that followed it would slide the divider away from what was new at open.
         */
        val unreadBoundary: UnreadBoundary = UnreadBoundary.Resolving,
        /** How many stored messages sit past the boundary, which bounds the walk that opens at it. */
        val unreadWalkBudget: Int = 0,
        /**
         * True while the delete confirmation is up.
         *
         * The sheet is modal, so nothing behind it should still read as the focus: the selected
         * message falls back behind the backdrop with the rest of the transcript until the sheet
         * closes, rather than sitting sharp and half-clipped at the sheet's own edge.
         */
        val confirmingDelete: Boolean = false,
        /**
         * The edit and delete windows the server publishes through `UserFlags`.
         *
         * Held in state rather than read straight off the coordinator because the reducer needs
         * it: a selection has to be narrowed to what is still open at the moment it is made, and
         * the reducer is where the selection is set.
         */
        val messagePolicy: MessagePolicy = MessagePolicy.Default,
        /**
         * The token a group's balance requirement names, and how to write it — "Jeffy", not its mint
         * and not its ticker. Null until the token cache has it, and null for any chat without such
         * a rule.
         */
        val ruleCurrency: RuleCurrency? = null,
        /**
         * What this viewer may do here. Null for anything that is not a group — a DM has no gate,
         * and rendering one from a default would blur every contact conversation in the app.
         */
        val groupAccess: GroupAccess? = null,
        /**
         * The gate's Join button, same shape as [sendProgress]. Membership arrives from the roster
         * rather than from the join's own reply, so without this the button would sit unchanged for
         * the whole round trip and read as dead — which is what it looked like before it had one.
         */
        val joinProgress: LoadingSuccessState = LoadingSuccessState(),
    ) {
        /**
         * The DM counterparty, or `null` for a group.
         *
         * Kept as a derived property so the paths that predate [ChatSubject] — the quote accent,
         * the tip recipient flow and the payment branches — keep reading what they always read.
         * None of them is reachable from a group, which is exactly what a `null` says here.
         */
        val participant: ChatParticipant?
            get() = subject?.asParticipant()

        // Opening the participant's profile (the entry point to blocking) is the tip arm's answer
        // alone. Asking the subject rather than comparing chat types means a new arm has to say.
        val canViewProfile: Boolean
            get() = subject?.canViewProfile == true

        /** What the selection bar may offer, straight from what the transcript already resolved. */
        val selectionCapabilities: Set<MessageCapability>
            get() = selection?.capabilities.orEmpty()

        /**
         * Whether this conversation is being read from outside the group: a group the viewer has
         * not joined, or one whose membership is not known yet.
         */
        val isOutsideGroup: Boolean
            get() = subject is ChatSubject.Group && subject.isMember != true

        /**
         * Whether the server will hand this viewer the group's transcript in full without their
         * joining it.
         *
         * The contract's line (`messaging.v1.ViewMode`): a non-member who satisfies a group's
         * listener rules may read it in full, and a non-member of a group with no listener rules may
         * not read it at all. So [GroupAccess.Eligible] alone is not enough — it is also the answer
         * for a group with no rules, where there is nothing to read.
         *
         * Every unknown answers no. A null [groupAccess] is the balance or the staff flag not having
         * arrived, and a null membership is a group hydrated by id that GetChat could not place the
         * viewer in; reading either as eligible would show a transcript the viewer may not be owed.
         */
        val readsFromOutside: Boolean
            get() = subject is ChatSubject.Group &&
                subject.isMember == false &&
                groupAccess == GroupAccess.Eligible &&
                subject.rules?.listener.orEmpty().isNotEmpty()

        /**
         * Whether the transcript is blurred, and the placeholder stands in for it when empty.
         *
         * Read off the subject first, not off [groupAccess] alone, because this one has to be right
         * on the frame the group first renders. [groupAccess] arrives through the balance and staff
         * flows, so it is null for as long as those take — and a withheld transcript that starts
         * unblurred and then blurs has already shown what it was withholding. So the blur goes on
         * with the subject and only comes off once the access says [GroupAccess.Eligible]: an
         * eligible viewer watches it fade, which is the direction that leaks nothing.
         *
         * [GroupAccess.Blocked] keeps it on, and a balance that drops while the chat is open turns
         * [GroupAccess.Eligible] into [GroupAccess.Blocked] and puts it back.
         *
         * Not held by a succeeded join. The only viewer who can join is an eligible one, who is
         * already reading the transcript sharp, and a group with no listener rules unblurring as the
         * roster confirms the join is the same sharp-transcript-over-gate state an eligible viewer
         * sits in.
         *
         * Mirrors iOS `ConversationGatePresentation.obscuresTranscript`.
         */
        val obscuresTranscript: Boolean
            get() = isOutsideGroup && !readsFromOutside

        /**
         * Whether the gate stands where the composer does. Every viewer outside the group, eligible
         * or not: reading a group from outside is not posting in it.
         *
         * A succeeded join keeps it true through the button's checkmark, so the roster — which can
         * confirm membership on the next frame — cannot cut the confirmation short by swapping the
         * composer in under it.
         *
         * Mirrors iOS `ConversationGatePresentation.replacesComposer`.
         */
        val replacesComposer: Boolean
            get() = joinProgress.success || isOutsideGroup

        /**
         * What a tap on a cash card in this transcript does.
         *
         * Only a member collects. An eligible non-member reads the transcript sharp and so can see
         * and tap a card, but the cash was sent to the group, so the card tells them to join
         * instead. A blurred transcript has nothing to tap; it falls on the same side because it is
         * also outside the group.
         *
         * A member always collects, but the claim is only answered with a thank-you when the
         * composer is live, because the thank-you is a message the viewer posts. A deactivated DM
         * has no composer, and a reply from it would be refused.
         *
         * Mirrors iOS, where the tap is refused at the gate's `.join` and recorded for a reply only
         * at `.open`.
         */
        val cashCardTap: CashCardTap
            get() = when {
                isOutsideGroup -> CashCardTap.JoinToCollect
                else -> CashCardTap.Collect(thanks = !isAnonymous && !replacesComposer)
            }

        /**
         * The link that invites someone into this group, or `null` when there is nobody to invite:
         * a DM, or a group this viewer has not joined.
         *
         * Built from the chat's id — there is no invite RPC, and [Linkify] is the one place that
         * decides the link's shape, so what the empty state shares is what the create flow shares.
         */
        val groupInviteUrl: String?
            get() = (subject as? ChatSubject.Group)
                ?.takeIf { it.isMember == true }
                ?.let { Linkify.groupChatInvite(it.chatId) }
    }

    /**
     * An edit in progress.
     *
     * [stashedDraft] is whatever the composer held when the edit began; leaving edit mode — by
     * confirming, cancelling, or backing out — puts it back, so starting an edit never costs the
     * user a half-written message.
     */
    data class EditingMessage(
        val messageId: Long,
        val originalText: String,
        val stashedDraft: String,
    )

    sealed interface Event {
        data class OnChatOpened(val identifier: ChatIdentifier) : Event
        data class OnContactFound(val contact: DeviceContact): Event
        data class OnTipUserResolved(val userId: ID, val profile: UserProfile): Event
        data object OnTipDmDetected : Event

        /** The chat this screen is showing turned out to be a group, with this metadata. */
        data class OnGroupResolved(val membership: ChatMembership) : Event

        /** The token cache learned the currency behind the group's balance requirement. */
        data class OnRuleCurrencyResolved(val currency: RuleCurrency?) : Event

        /** The gate re-decided, because membership, the rules, or the balance moved. */
        data class OnGroupAccessResolved(val access: GroupAccess) : Event

        /** The gate's "Join Chat" button. */
        data object JoinChat : Event

        /**
         * The Join button's own state. Success is dispatched for the length of the checkmark and
         * then cleared, which is what releases the gate to the composer.
         */
        data class JoinStateUpdated(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        /** The invite sheet's "Copy Invite Link" row. */
        data object CopyInviteLink : Event

        /** The group profile's "Leave Chat" row, which puts the confirmation up. */
        data object LeaveChat : Event

        /** The user confirmed the leave. */
        data object LeaveConfirmed : Event

        /** The leave went through, so whatever is showing the group's profile should close. */
        data object LeftChat : Event

        /** This chat's viewer state moved, from the stream or from the viewer's own request. */
        data class OnViewerStateResolved(val viewerState: ViewerState?) : Event

        data class OnCurrencySymbolUpdated(val symbol: String): Event
        data class OnChatInitFeeUpdated(val formatted: String?) : Event
        data class OnSendCashReadinessChanged(val ready: Boolean) : Event
        data object RefreshContact : Event
        data class ChatFound(val chatId: ChatId) : Event
        data object OnSendCash: Event
        data object OnStartMessageInput: Event
        data object OnStopMessageInput: Event
        data object OnMessageInputConsumed: Event
        data class TypistsUpdated(val typists: Set<ActiveTypist>) : Event
        data class TypingAvatarsUpdated(val avatars: List<TypingAvatar>) : Event
        data object ResolveCompleted : Event
        data object ResolveFailed : Event

        data object SendMessage : Event
        data class RetryMessage(val pendingId: String?, val content: MessageContent) : Event

        data object NavigateToAmountEntry : Event

        /** Open the fixed-fee sheet that pays for the DM, instead of the keypad. */
        data object NavigateToInitPayment : Event
        data object PresentDepositOptions : Event
        data class OpenScreen(val route: AppRoute, val asSheet: Boolean = false): Event
        data object OnConfirmRequested : Event

        /** Confirm the DM-opening fee. The amount comes from the fee, not from the keypad. */
        data object OnInitPaymentConfirmed : Event
        data class OnSendRequested(
            val amount: Fiat,
            val token: Token,
        ) : Event
        data class SendStateUpdated(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event
        data class SendComplete(val amount: Fiat) : Event

        data object OnSelfTypingStarted : Event
        data object OnSelfTypingStill : Event
        data object OnSelfTypingStopped : Event
        data class TypingEnabled(val enabled: Boolean) : Event

        data class TokenUpdated(val token: Token) : Event
        data class LimitsChanged(val limits: Limits?) : Event
        data class AdvanceReadPointer(val messageId: Long) : Event
        data class ChatDeactivated(val isReadOnly: Boolean) : Event
        data class MessagePolicyChanged(val policy: MessagePolicy) : Event

        /** Selects [bubble], or leaves selection mode if it is already the selected one. */
        data class ToggleMessageSelection(val bubble: ChatListItem.ContentBubble) : Event
        data object ClearMessageSelection : Event

        // The message actions carry what they act on rather than reading it back off the selection:
        // the reducer runs before the handlers do, so an action that dismisses the selection bar
        // would otherwise have cleared its own subject before the handler saw it.
        data class CopyMessage(val text: String) : Event
        data class EditMessage(val messageId: Long, val text: String) : Event
        data class DeleteMessage(val messageId: Long) : Event

        // Carries nothing because it does nothing but dismiss the bar: report is its own top-level
        // flow, and the bar's caller has already captured the message to push the route with.
        data object ReportRequested : Event

        data object SubmitEdit : Event
        data object CancelEdit : Event
        data object EditingEnded : Event

        /**
         * Asks for a reply to [bubble]. Both entry points — the selection bar and the swipe — land
         * here rather than on [ReplyToMessage], because turning a bubble into a citation needs the
         * stored message, and that read belongs in one place.
         */
        data class ReplyRequested(val bubble: ChatListItem.ContentBubble) : Event

        /** Opens the composer's reply strip on an already-resolved citation. */
        data class ReplyToMessage(val quote: ChatQuote) : Event
        data object CancelReply : Event

        /**
         * The reader tapped a cash voucher in this transcript, naming the link.
         *
         * Only the name: the screen opens the link through the URL handler right after
         * dispatching this, and nothing here claims anything. Dispatched only when
         * [State.cashCardTap] lets the reader collect. See [initClaimReplies].
         */
        data class CashLinkOpened(val entropy: String) : Event

        /**
         * The reader tapped a cash voucher they cannot collect from here, per
         * [State.cashCardTap]. The link was not opened; this only tells them why.
         */
        data object CashLinkRefused : Event

        /** Asks the transcript to scroll to [messageId] — a tap on a quote. */
        data class JumpToMessage(val messageId: Long) : Event

        /** The same request, once the walk's bound is known. */
        data class JumpResolved(val messageId: Long, val budget: Int) : Event

        /** The chat's unread boundary, read once when its id became known. */
        data class UnreadBoundaryResolved(val boundary: UnreadBoundary, val walkBudget: Int) : Event
        data object JumpConsumed : Event
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messageStream = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeMessagesPaged(it) }
        // Cached here rather than after the mapping below so the overlay composes over the page
        // cache: an edit or delete awaiting the server re-runs the mapping without re-fetching.
        .cachedIn(viewModelScope)

    /** Edits and deletes the server has not answered yet, composed over the stored transcript. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val pendingMutations = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observePendingMutations(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val otherReadPointer = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeOtherReadPointer(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Re-runs the transcript mapping when the windows change, so a message resolved under the
     * defaults (both open, before the flags arrive) narrows as soon as the server's answer lands.
     */
    private val messagePolicy = stateFlow.map { it.messagePolicy }.distinctUntilChanged()

    /**
     * Re-runs the transcript mapping when the viewer joins or leaves, so a message resolved while
     * they were reading from outside picks up Reply the moment the join lands, rather than on the
     * next page.
     */
    private val viewerCanPost = stateFlow.map { !it.isOutsideGroup }.distinctUntilChanged()

    /**
     * Null for a DM, a map for a group — the distinction the transcript needs, because a DM's
     * counterparty is already named in the title bar and attributing each of their bubbles would be
     * noise. `null` rather than an empty map so the difference survives: an empty map in a group is
     * a real state (nothing resolved yet) and has to keep asking for profiles, where a DM must not
     * ask at all.
     *
     * Two sources behind it, because the roster is a truncated subset — members come from it, and
     * anyone it omits (a sender who left, a member past the cap) is asked for one profile at a time.
     * See SenderResolver.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val senderProfiles: StateFlow<Map<String, UserProfile>?> =
        stateFlow.map { it.chatType == ChatType.GROUP }
            .distinctUntilChanged()
            .flatMapLatest { isGroup ->
                if (isGroup) chatCoordinator.observeSenderProfiles() else flowOf(null)
            }
            // Held rather than cold so a tap on a sender's picture can read the profile that drew
            // it. The transcript is the only thing that asks for these, so the map the paging
            // stream is using is the one to answer from — looking the sender up again would be a
            // second source for an identity already on screen.
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The member behind [userId], as a participant a profile screen can be opened on.
     *
     * Null until the transcript has resolved them, which it has by the time their picture is
     * drawn — the picture is what this is reached from.
     */
    fun memberParticipant(userId: ID): ChatParticipant.TipUser? =
        senderProfiles.value?.get(userId.hexEncodedString())
            ?.let { ChatParticipant.TipUser(userId, it) }

    /**
     * How a drawn card reaches the lookup. Provided to the transcript, read by `LinkCardView`.
     *
     * The resolver rather than the transcript is what a card asks, so the type narrows to the part
     * of it `chat-ui` is allowed to see — the query and the signal that an answer moved, not the
     * eviction that produces the signal, which stays this screen's to decide.
     */
    val linkCardResolution: LinkCardResolution get() = linkCardResolver

    /**
     * Where a claim goes back to, for a voucher tapped in this transcript.
     *
     * Held here rather than carried down to the bubble: neither `TextBubble` nor `BareLinkCard` is
     * given a message id, and the card is drawn from several places inside them, so threading one
     * through would touch every call site to answer a question this side already knows.
     */
    private val claimReplyTargets = ClaimReplyTargets()

    /**
     * The transcript's rows before separators. Cached so a change to the unread boundary or the
     * separator config re-runs only [messages]' separator pass, not the per-message lookups here.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mappedMessages: Flow<PagingData<ChatListItem.ContentBubble>> =
        combine(
            messageStream,
            pendingMutations,
            messagePolicy,
            senderProfiles,
            viewerCanPost,
        ) { pagingData, mutations, policy, profiles, canPost ->
            pagingData.flatMap { stored ->
                val message = stored.applying(mutations[stored.messageId])
                message.content.flatMapIndexed { index, content ->
                    val enriched = if (content is MessageContent.Cash && content.tokenName.isBlank()) {
                        val token = tokenCoordinator.getTokenMetadata(content.mint).getOrNull()?.token
                        if (token != null) {
                            content.copy(tokenName = token.name, tokenImageUrl = token.imageUrl)
                        } else content
                    } else content

                    // Resolved here, next to the token-metadata lookup, because this is the one
                    // place in the transcript that already does async per-item work. A citation of
                    // a message this device never stored resolves to null, and the bubble renders
                    // its body with no panel rather than an error.
                    val quote = (content as? MessageContent.Reply)?.let { reply ->
                        stateFlow.value.chatId
                            ?.let { chatCoordinator.getMessage(it, reply.repliedMessageId) }
                            ?.toQuote()
                    }

                    // Built from the same detection pass the bubble underlines with, so the
                    // link that becomes a card is always one the reader can see is a link.
                    //
                    // Classified only. The card comes out of here in its loading state and runs
                    // its own lookup -- see `LinkCardResolution`. This pass used to await that,
                    // which meant a chat painted nothing until every link in the first window had
                    // been round-tripped, and that every link in every mapped message was queried
                    // whether or not the reader ever scrolled to it.
                    val linkCard = enriched.linkableText()
                        ?.let { text -> linkCardClassifier.firstCard(detectUrls(text)) }

                    // Noted while the voucher and the message it came on are in the same hand;
                    // see [ClaimReplyTargets]. Skipped for the reader's own messages, which is what
                    // makes "do not thank yourself for your own link" structural rather than a
                    // check at send time.
                    if (!message.isFromSelf) {
                        (linkCard as? LinkCard.Cash)
                            ?.let { claimReplyTargets.note(it.entropy, message.messageId) }
                    }

                    val receiptStatus = if (message.isFromSelf) {
                        when (message.deliveryStatus) {
                            DeliveryStatus.SENDING -> ReceiptStatus.SENDING
                            DeliveryStatus.FAILED -> ReceiptStatus.FAILED
                            DeliveryStatus.SENT -> ReceiptStatus.SENT
                        }
                    } else null

                    // A message the viewer sent needs no attribution, and neither does a DM —
                    // `profiles` is null for one. Asking for a profile that is missing is what keeps
                    // a sender from showing as a blank name for the rest of the session; the
                    // resolver dedupes, so asking once per page is asking once.
                    val sender = profiles?.let { resolved ->
                        message.senderId?.takeIf { !message.isFromSelf }?.let { senderId ->
                            val profile = resolved[senderId.hexEncodedString()]
                            if (profile == null) {
                                chatCoordinator.requestSenderProfile(senderId)
                                null
                            } else {
                                SenderIdentity(
                                    userId = senderId,
                                    displayName = profile.displayName,
                                    picture = profile.profilePicture,
                                )
                            }
                        }
                    }

                    ChatListItem.ContentBubble(
                        messageId = message.messageId,
                        contentIndex = index,
                        content = enriched,
                        isFromSelf = message.isFromSelf,
                        timestamp = message.timestamp,
                        receiptStatus = receiptStatus,
                        pendingClientIdHex = message.pendingClientIdHex,
                        isEdited = message.lastEditedTs != null,
                        // A null author is a moderation removal, which reads as someone else's.
                        deletedByViewer = (enriched as? MessageContent.Deleted)?.deletedBy
                            ?.let { it == userManager.accountId } == true,
                        // Resolved once, here, so no menu re-derives it: a later group-role
                        // taxonomy becomes another input to the resolver rather than a branch at
                        // each action site.
                        capabilities = resolveCapabilities(message, policy, canPost = canPost),
                        quote = quote,
                        sender = sender,
                        // Independent of the profile lookup above: the runs have to break by
                        // author from the first frame, and the map that names the authors lands
                        // after the first page does.
                        senderId = message.senderId?.takeIf { !message.isFromSelf },
                        linkCard = linkCard,
                    )
                        // A carded link takes a row of its own, with the prose either side of it
                        // on rows above and below. Reversed because the list is: this page runs
                        // newest-first under reverseLayout, so the row drawn lowest goes first.
                        .splitAroundLinkCard()
                        .asReversed()
                }
            }
        }.cachedIn(viewModelScope)

    /**
     * The transcript as the list draws it: [mappedMessages] with date separators and the unread
     * divider. The boundary and config are inputs rather than reads of the current state, so a
     * page never takes its separators from one config and its neighbour's from another.
     *
     * Nothing is emitted while the boundary is still resolving. The list decides where to open from
     * the first page it lays out, and a page drawn before the boundary landed has no divider to
     * open at. Both wait on the same chat id, and the resolution is two local queries.
     */
    val messages: Flow<PagingData<ChatListItem>> =
        combine(
            mappedMessages,
            stateFlow.map { it.unreadBoundary }
                .filterNot { it is UnreadBoundary.Resolving }
                .distinctUntilChanged(),
            stateFlow.map { it.separatorConfig }.distinctUntilChanged(),
        ) { paging, boundary, config -> paging.withSeparators(boundary, config) }

    /**
     * The citation shown for [this] message.
     *
     * The accent comes from the message's own sender id, not from `State.participant`:
     * [ChatParticipant.Contact] wraps a device contact and carries no user id, so the participant
     * is not a usable source for a counterparty's colour.
     */
    /**
     * Who the citation says said it.
     *
     * A DM's counterparty is the chat's, so the participant answers for every message they sent.
     * A group has no counterparty and a different name per message, so the answer is the cited
     * sender's own profile — the same map the transcript draws their bubble from. Missing means
     * the device has never seen them, which a request fixes for the next emission; the strip
     * showing a blank name is what it looked like before.
     */
    private suspend fun ChatMessage.quoteAuthorName(): String {
        if (isFromSelf) return resources.getString(R.string.title_you)

        val profiles = senderProfiles.value ?: return stateFlow.value.participant?.name.orEmpty()
        val senderId = senderId ?: return ""
        val profile = profiles[senderId.hexEncodedString()]
            ?: return "".also { chatCoordinator.requestSenderProfile(senderId) }

        return profile.displayName
    }

    private suspend fun ChatMessage.toQuote(): ChatQuote {
        val body = content.firstOrNull()
        val palette = senderId?.let { generateComplementaryColorPalette(it) }
        return ChatQuote(
            messageId = messageId,
            authorName = quoteAuthorName(),
            snippet = when (body) {
                is MessageContent.Cash -> ChatQuoteSnippet.Cash(
                    amount = body.amount,
                    tokenName = body.tokenName.ifBlank {
                        tokenCoordinator.getTokenMetadata(body.mint)
                            .getOrNull()?.token?.name.orEmpty()
                    },
                )

                is MessageContent.Text -> ChatQuoteSnippet.Text(body.text)

                // A reply to a reply cites the inner body, not the nested citation.
                is MessageContent.Reply -> ChatQuoteSnippet.Text(
                    body.content.filterIsInstance<MessageContent.Text>()
                        .firstOrNull()?.text.orEmpty()
                )

                else -> ChatQuoteSnippet.Text("")
            },
            accent = palette?.first,
            nameAccent = palette?.second,
            senderIdHex = senderId?.hexEncodedString(),
        )
    }

    private val maxAmountFlow by lazy {
        combine(
            transactionController.limits,
            tokenCoordinator.observeSelectedTokenMint()
                .flatMapLatest { mint -> tokenCoordinator.balanceForToken(mint) },
            exchange.observePreferredRate(),
        ) { limits, balance, rate ->
            val balanceInLocal = balance.convertingTo(rate)
            val sendLimit = limits?.sendLimitFor(rate.currency) ?: SendLimit.Zero
            Fiat(min(sendLimit.nextTransaction, balanceInLocal.toDouble()), rate.currency)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    // Only the payment that opens a tip DM is a tip — it buys the conversation, and it is the one
    // the recipient's fee applies to. Everything after it, and every contact DM, is a plain send.
    private fun amountStyle(isTip: Boolean) = AmountEntryStyle(
        // One label for both kinds of payment. The chat above the keypad already says who this is
        // going to and why; the slider only has to say what the gesture does.
        actionLabel = AmountEntryLabel.Plain(resources.getString(R.string.action_swipeToSend)),
        actionStyle = ConfirmationStyle.Slide,
        infoHint = { resources.getString(R.string.subtitle_sendHint, it) },
        overMaxHint = { resources.getString(R.string.subtitle_sendHintLimitExceeded, it) },
        belowMinHint = if (isTip) {
            { min -> resources.getString(R.string.subtitle_tipHintMinimum, min) }
        } else null,
        // A tip's ceiling is only the sender's own balance; the minimum is the recipient's rule
        // and the one worth stating up front, so it holds the hint line for the whole entry.
        standingHint = if (isTip) {
            AmountEntryStyle.StandingHint.Floor
        } else {
            AmountEntryStyle.StandingHint.Ceiling
        },
    )

    // The counterparty of a tip DM, whose server profile carries the fee they charge to open a DM.
    // Null for a contact DM: it is addressed by phone number and there is no profile to read one off.
    private val tipRecipientFlow = stateFlow
        .map { it.participant as? ChatParticipant.TipUser }
        .distinctUntilChanged()

    private val amountStyleFlow by lazy {
        openingTipRecipientFlow
            .map { amountStyle(isTip = it != null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), amountStyle(isTip = false))
    }

    /**
     * Whether this conversation already exists. Members are the same signal
     * [com.flipcash.shared.chat.DmChatResolver.getChatId] calls initialized: a chat the server has
     * created has a member row, one derived from a user id alone does not. Null until the member
     * store has answered; [isChatInitialized] reads that as false.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val chatExistence by lazy {
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMembers(it) }
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
            // Null until the member store has answered, which is not the same as "doesn't exist".
            .stateIn<Boolean?>(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    private val isChatInitialized by lazy {
        chatExistence.map { it == true }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    }

    /**
     * The user this payment would open a tip DM with — null once the conversation exists, and null
     * for a contact DM. It decides both the floor and the word the entry uses: the fee, and calling
     * the payment a tip, belong to the one that opens the chat.
     */
    private val openingTipRecipientFlow by lazy {
        combine(tipRecipientFlow, isChatInitialized) { recipient, initialized ->
            recipient?.takeUnless { initialized }
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    /**
     * The floor the entry enforces, and only for the payment that opens a tip DM.
     *
     * What the recipient sets is the fee to *open* a DM with them, so it gates that first payment
     * and nothing after it: once the conversation exists, sending cash in it has no minimum at all.
     * A contact DM never has one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val minAmountFlow by lazy {
        openingTipRecipientFlow
            .flatMapLatest { recipient ->
                if (recipient == null) flowOf(null)
                else tipPaymentDelegate.minimumToOpenDmWith(recipient.profile)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    val amountDelegate by lazy {
        AmountEntryDelegate(
            exchange = exchange,
            scope = viewModelScope,
            style = amountStyleFlow,
            loadingState = stateFlow.map { it.sendProgress }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState()),
            maxAmount = maxAmountFlow,
            minimumAmount = minAmountFlow,
            tokenChanges = tokenCoordinator.observeSelectedTokenMint(),
        )
    }

    /**
     * The last chance to write a draft. A backgrounded app can be killed without another callback
     * reaching this ViewModel, so STOPPED — not teardown — is what makes process death survivable.
     */
    private val draftFlushObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = flushDraft()
    }

    init {
        // Essential — needed immediately for chat display
        initChatHandlers()
        initLinkCardFreshness()
        initClaimReplies()
        initDraftHandlers()

        viewModelScope.launch {
            // Yield to let the first frame render before setting up remaining collectors
            initTokenAndExchangeObservers()
            initTypingHandlers()
            initSendHandlers()
            initMessageActionHandlers()
        }
    }

    /**
     * Keeps a rendered cash link honest about its claim state, from the two directions a claim can
     * come from.
     *
     * **This device.** The reader taps a voucher, the link goes back out through the URL handler,
     * and the shell claims it over a bill drawn on top of this screen. The card is never told; it
     * is still composed, still showing the answer it was drawn from, and the answer has just
     * stopped being true. [CashLinkClaims] names the entropy, the resolver forgets it and bumps its
     * revision, and the card re-asks and tears in place. Every settled attempt, not only a
     * successful one, because "already claimed" and "expired" are the same news arriving as an
     * error.
     *
     * **Anyone else.** Nothing tells this device that a link it is drawing was collected, so the
     * sender watches their own voucher say "Tap to claim" for cash that is gone. There is no
     * signal to wait for, so the alternative is to ask — see [refreshLinkCards] for why that is
     * cheaper than it sounds, and [CashLinkClaims] for the signal that would retire it.
     *
     * The same claim is also the other side's news, which is why [thankForClaim] hangs off this
     * collector: a claim made here is the one thing this device knows that the sender does not, and
     * a reply on the transcript is how it tells them.
     */
    private fun initLinkCardFreshness() {
        // One collector for two consequences rather than one each. The flow is replay-less over a
        // single-slot buffer that drops rather than suspends, so a second collector is a second
        // chance to be the one that misses an emission.
        cashLinkClaims.settledClaims
            .onEach { claim ->
                linkCardResolver.invalidateCash(claim.entropy)
                thankForClaim(claim)
            }
            .launchIn(viewModelScope)

        // STARTED rather than a timer of its own, which makes one construct cover both cases worth
        // covering: the first pass runs on every foreground edge, so a reader who put the phone
        // down and came back gets a fresh answer immediately, and the loop then carries the case
        // they are actually in -- watching the chat while the other side collects. Backgrounding
        // ends it, so nothing is asked on behalf of a screen nobody is looking at.
        viewModelScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshLinkCards()
                    delay(CLAIM_REFRESH_INTERVAL)
                }
            }
        }
    }

    /**
     * Re-asks about any voucher still drawn as claimable.
     *
     * Not a poll in the expensive sense: [LinkCardResolver.refreshClaimable] drops nothing unless
     * an answer it is holding says `Claimable`, and no drop means no revision bump and so no
     * query. A chat with no cash link in view — nearly all of them — costs a walk of an empty map
     * per tick. A claimed or expired card is terminal and is never asked about again.
     */
    private suspend fun refreshLinkCards() {
        linkCardResolver.refreshClaimable()
    }

    /**
     * Records which voucher the reader just tapped, so the claim that comes back has a message to
     * answer. Recorded, not acted on — [ClaimReplyTargets] says why a tap is not yet a claim.
     */
    private fun initClaimReplies() {
        eventFlow.filterIsInstance<Event.CashLinkOpened>()
            .onEach { event ->
                // Not recorded for a viewer who cannot post: nothing would come of the thank-you
                // but a send the server refuses.
                val tap = stateFlow.value.cashCardTap
                if (tap is CashCardTap.Collect && tap.thanks) claimReplyTargets.tapped(event.entropy)
            }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CashLinkRefused>()
            .onEach {
                BottomBarManager.showInfo(
                    title = resources.getString(R.string.title_joinToCollect),
                    message = resources.getString(R.string.description_joinToCollect),
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Replies to the voucher with a thank-you, once a link tapped here has actually been collected.
     *
     * This is what closes the loop for everyone else in the chat without a server change. The
     * sender is watching a card they cannot be told about — nothing pushes a claim, and the poll in
     * [refreshLinkCards] is the fallback for exactly that — but a message arrives on a transcript
     * that already fans out to every participant on both platforms, and it stays there for whoever
     * opens the chat tomorrow. It reads as the claimer because it is: a plain text reply, sent the
     * way the composer sends one, attributed to them.
     *
     * Which claims qualify is [ClaimReplyTargets]'s to answer: it is what limits this to a link the
     * reader opened from this transcript and a claim that actually moved money.
     *
     * A message because a message is what there is. A reaction on the voucher is the smaller thing
     * to say, and is where this goes once the transcript can carry one; only the last line here
     * changes, because what decides to thank anyone does not.
     */
    private fun thankForClaim(claim: SettledClaim) {
        val messageId = claimReplyTargets.settled(claim) ?: return
        val chatId = stateFlow.value.chatId ?: return

        // Decided on the collector, sent off it. The claim is consumed before this returns, so the
        // next one is not waiting behind a network call on a single-slot flow that drops rather
        // than suspends -- and a card's freshness does not wait on a thank-you either.
        viewModelScope.launch {
            chatCoordinator.sendMessage(
                chatId,
                resources.getString(R.string.message_cash_link_thanks),
                messageId,
            ).onFailure { trace("claim reply failed to send - ${it.localizedMessage}") }
        }
    }

    /**
     * Gets the chat on screen onto it: its own row if the device has one, otherwise the server's
     * copy, and then the transcript if the viewer is entitled to it.
     *
     * A chat reached by invite link or push tap may never have been synced — the group feed, a join
     * and the roster stream all only ever carry chats the viewer is already in — so without the
     * hydration the screen has no title, no info card and no gate, and nothing that would later
     * supply them. A chat the device already holds costs no round trip: [ChatCoordinator.hydrateChat]
     * answers null for it and the Room observation stays the only source, so opening a synced chat
     * behaves exactly as it did.
     *
     * The transcript is fetched only for a viewer the gate lets through. A withheld group is drawn
     * as a placeholder rather than fetched, so asking for its messages would pull the very ones the
     * blur is over — and an unknown membership is withheld here for the same reason it is on screen.
     * A member whose feed has not synced yet is not stranded: the group feed sync sends its own load
     * for a group whose cursor is still at zero. An eligible non-member's transcript is fetched once
     * the gate says so, off [State.readsFromOutside], rather than here, where the balance that
     * decides it has not arrived yet.
     */
    private suspend fun openTranscript(chatId: ChatId) {
        val hydrated = chatCoordinator.hydrateChat(chatId) ?: run {
            chatCoordinator.loadMessages(chatId)
            return
        }

        if (hydrated.metadata.type != ChatType.GROUP) {
            chatCoordinator.loadMessages(chatId)
            return
        }

        dispatchEvent(Event.OnGroupResolved(hydrated))
    }

    private fun initChatHandlers() {
        // Ahead of the transcript, so the first mapping already has the real windows rather than
        // the fallbacks the default carries.
        userFlags.resolvedFlags
            .map {
                MessagePolicy.fromFlags(
                    editWindow = it.messageEditWindow.effectiveValue,
                    deleteWindow = it.messageDeleteWindow.effectiveValue,
                )
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.MessagePolicyChanged(it)) }
            .launchIn(viewModelScope)

        // Unified chat open handler — resolves chatId and contact from the identifier
        eventFlow
            .filterIsInstance<Event.OnChatOpened>()
            .onEach { event ->
                val identifier = event.identifier

                // 1. Resolve chatId
                val chatId = when (identifier) {
                    is ChatIdentifier.ByContact -> identifier.chatId
                        ?: chatCoordinator.getChatId(identifier.contact).getOrNull()
                    is ChatIdentifier.ByChatId -> identifier.chatId
                    // Derived, not looked up: the canonical tip-DM id is a function of the two user
                    // ids, so it is known before the chat exists. Opening on it means the first tip
                    // lands in the chat the user is already looking at.
                    is ChatIdentifier.ByUser ->
                        chatCoordinator.generateChatId(identifier.userId).getOrNull()
                }

                // Re-entering the same, already-open chat (e.g. returning from the amount-entry
                // step) re-dispatches OnChatOpened. The chat is already resolved and its messages
                // are cached in Room, so skip the re-resolve + network reload that would invalidate
                // Paging and reflow the message list. Still keep the chat active and clear
                // notifications.
                if (chatId != null && stateFlow.value.chatId == chatId) {
                    chatCoordinator.setActiveChatId(chatId)
                    chatCoordinator.dismissNotifications(chatId)
                    return@onEach
                }

                if (chatId != null) {
                    dispatchEvent(Event.ChatFound(chatId))
                    restoreDraft(chatId)
                    chatCoordinator.setActiveChatId(chatId)
                    viewModelScope.launch { openTranscript(chatId) }
                    chatCoordinator.dismissNotifications(chatId)
                } else {
                    // No existing chat means no messages yet, so typing stays disabled. The
                    // observeMessages-driven typing flow only fires once a chatId exists, so mark
                    // the typing state resolved here explicitly — otherwise the bottom bar would
                    // stay hidden forever for a brand-new contact.
                    dispatchEvent(Event.TypingEnabled(false))
                }

                // 2. Resolve contact
                when (identifier) {
                    is ChatIdentifier.ByContact -> {
                        val resolved = contactCoordinator.lookupContact(identifier.contact.e164).getOrNull()
                            ?: chatId?.let { contactCoordinator.lookupContactByDmChatId(it.toString()) }
                            ?: identifier.contact
                        dispatchEvent(Event.OnContactFound(resolved))
                    }
                    is ChatIdentifier.ByChatId -> {
                        val contact = contactCoordinator.lookupContactByDmChatId(
                            identifier.chatId.toString()
                        )
                        val isGroup = chatCoordinator.observeMetadata(identifier.chatId).first()
                            ?.metadata?.type == ChatType.GROUP
                        if (contact != null) {
                            dispatchEvent(Event.OnContactFound(contact))
                        } else if (!isGroup) {
                            // No device contact backs this chat — it's a tip DM. Mark it immediately
                            // (this lookup is local) so the send button renders condensed without
                            // waiting on the profile below, then warm the member store (fetch +
                            // persist if nothing is cached) so the reactive tip-identity collector
                            // can resolve the counterparty from their server profile. Identity is set
                            // reactively (see initChatHandlers), not here, so it can't be missed by a
                            // fast tap on "Send $".
                            dispatchEvent(Event.OnTipDmDetected)
                            viewModelScope.launch { chatCoordinator.getOtherMember(identifier.chatId) }
                        }
                    }
                    // Identity came in with the identifier (the username lookup that produced it
                    // returned the profile), and the OnChatOpened reducer has already applied it.
                    // There is nothing to look up: a chat opened this way may have no members yet.
                    is ChatIdentifier.ByUser -> Unit
                }
            }
            .launchIn(viewModelScope)

        // Resolve owner authority for sending cash
        eventFlow
            .filterIsInstance<Event.OnContactFound>()
            .onEach { event ->
                viewModelScope.launch {
                    contactCoordinator.resolve(event.contact.e164)
                        .onSuccess { dispatchEvent(Event.ResolveCompleted) }
                        .onFailure { dispatchEvent(Event.ResolveFailed) }
                }
            }.launchIn(viewModelScope)

        // Re-resolve the contact from the device (e.g. after adding via system contacts)
        eventFlow
            .filterIsInstance<Event.RefreshContact>()
            .mapNotNull { (stateFlow.value.participant as? ChatParticipant.Contact)?.contact?.e164 }
            .onEach { e164 ->
                viewModelScope.launch {
                    val refreshed = contactCoordinator.refreshContact(e164)
                    if (refreshed != null) {
                        dispatchEvent(Event.OnContactFound(refreshed))
                    }
                }
            }
            .launchIn(viewModelScope)

        // Resolve the tip counterparty reactively from the chat members. Tip DMs have no device
        // contact, so identity (name + avatar + user id) comes from the other member's server
        // profile — the same source the tips list uses. Reactive so it settles as soon as the
        // members are available and can't be missed by the send gate. Never clobbers a device
        // contact: the OnTipUserResolved reducer keeps an existing Contact participant.
        //
        // Skipped for a group, whose "other member" is only the first row of the roster. A roster
        // change re-emits the members and the group's metadata together, so resolving one here would
        // re-title the group as a DM with that member until OnGroupResolved lands again.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatId ->
                combine(
                    chatCoordinator.observeMembers(chatId),
                    chatCoordinator.observeMetadata(chatId).map { it?.metadata?.type },
                ) { members, type -> members.takeUnless { type == ChatType.GROUP } }
            }
            .mapNotNull { members -> members?.firstOrNull { it.userId != userManager.accountId } }
            .distinctUntilChanged()
            .onEach { member -> dispatchEvent(Event.OnTipUserResolved(member.userId, member.userProfile)) }
            .launchIn(viewModelScope)

        // A group's identity is its own row, not a counterparty. Observed rather than read once
        // because every field the chrome shows moves under the user: a roster event changes the
        // member count, a join flips membership, a rules change moves the gate.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMetadata(it) }
            .filterNotNull()
            .filter { it.metadata.type == ChatType.GROUP }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnGroupResolved(it)) }
            .launchIn(viewModelScope)

        // The same metadata, minus the type filter: every chat has viewer state and every chat can
        // be muted, so this one is not the group chrome's business. Observed rather than read with
        // the chat because a mute made on another device arrives on the stream, and because leaving
        // clears it server-side.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMetadata(it) }
            .map { it?.metadata?.viewerState }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnViewerStateResolved(it)) }
            .launchIn(viewModelScope)

        // Observed rather than read once: the rule can change under an open screen, and a buy
        // hydrates a fresher copy of the token. See observeRuleToken for why it fetches as well.
        stateFlow.map { state ->
            (state.subject as? ChatSubject.Group)?.rules.balanceRequirement()
                ?.mints?.firstOrNull()?.let { Mint(it.bytes) }
        }
            .distinctUntilChanged()
            .flatMapLatest { mint -> mint?.let { tokenCoordinator.observeRuleToken(it) } ?: flowOf(null) }
            .map { token ->
                // The name rather than the symbol, matching the create form's own mint row: the two
                // screens name one holding, and the balance list a requirement is satisfied from
                // renders the name as well (`TokenWithBalance.displayName`). `brandedName` is the
                // same rule the link cards use, so the reserve reads "Dollars" here too rather than
                // "USDF".
                token?.let {
                    RuleCurrency(
                        name = it.brandedName(resources),
                        isReserve = it.isReserve,
                    )
                }
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnRuleCurrencyResolved(it)) }
            .launchIn(viewModelScope)

        // Re-resolved whenever membership or the rules move, and internally whenever the balance
        // does. flatMapLatest rather than combine because the balance flow is the inner one: a new
        // subject must cancel the gate it was deciding, not race it.
        stateFlow.map { it.subject as? ChatSubject.Group }
            .distinctUntilChanged()
            .flatMapLatest { group ->
                if (group == null) {
                    flowOf(null)
                } else {
                    tokenCoordinator.groupAccess(
                        isMember = group.isMember == true,
                        rules = group.rules,
                        isStaff = userFlags.resolvedFlags.map { it.isStaff.effectiveValue },
                    )
                }
            }
            .filterNotNull()
            .onEach { dispatchEvent(Event.OnGroupAccessResolved(it)) }
            .launchIn(viewModelScope)

        // Observe member identity — if the other member loses identity (e.g. unlinked
        // their phone), mark the chat as read-only. Gated by chat type through the same rule the
        // feed filters on, so a tip DM — addressed by user id, named by handle — is never
        // deactivated for lacking a name or a phone.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatId ->
                combine(
                    chatCoordinator.observeMembers(chatId),
                    stateFlow.map { it.chatType }.distinctUntilChanged(),
                ) { members, chatType -> members to chatType }
            }
            .map { (members, chatType) ->
                val selfId = userManager.accountId
                val other = members.firstOrNull { it.userId != selfId }
                if (other != null) !isDmAddressable(chatType, other.userProfile) else false
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.ChatDeactivated(isReadOnly = it)) }
            .launchIn(viewModelScope)

        // An eligible non-member reads the group in full, but nothing else fetches its transcript:
        // openTranscript leaves a group the viewer is outside of unloaded, and the group feed only
        // carries groups the viewer is in. Keyed on the chat so a balance that dips and recovers
        // refetches rather than trusting a page that may have moved on.
        stateFlow.map { state -> state.chatId.takeIf { state.readsFromOutside } }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { chatId -> chatCoordinator.loadMessages(chatId) }
            .launchIn(viewModelScope)

        // Advance read pointer when user scrolls to messages
        eventFlow
            .filterIsInstance<Event.AdvanceReadPointer>()
            .onEach { event ->
                val state = stateFlow.value
                val chatId = state.chatId ?: return@onEach
                // A read pointer is a member's: an eligible non-member can see the messages now,
                // but the server refuses to move a pointer for someone outside the chat.
                if (state.isOutsideGroup) return@onEach
                viewModelScope.launch { chatCoordinator.advanceReadPointer(chatId, event.messageId) }
            }
            .launchIn(viewModelScope)
    }

    private fun initTokenAndExchangeObservers() {
        // Token observation
        tokenCoordinator.observeSelectedTokenMint()
            .flatMapLatest { mint ->
                tokenCoordinator.tokenBalances.map { tokens ->
                    tokens.find { it.token.address == mint }
                }
            }
            .filterNotNull()
            .onEach { tokenWithBalance ->
                dispatchEvent(Event.TokenUpdated(tokenWithBalance.token))
            }.launchIn(viewModelScope)

        exchange.observePreferredRate()
            .onEach { rate ->
                val currency = exchange.getCurrency(rate.currency.name)
                if (currency != null) {
                    amountDelegate.onCurrencyChanged(currency)
                    dispatchEvent(Event.OnCurrencySymbolUpdated(currency.symbol.ifEmpty { "$" }))
                }
            }.launchIn(viewModelScope)

        // The same floor the amount entry enforces, said out loud on the button that has to charge
        // it. Formatted here rather than in the composable so the button has no currency logic.
        minAmountFlow
            .onEach { dispatchEvent(Event.OnChatInitFeeUpdated(it?.formatted())) }
            .launchIn(viewModelScope)

        combine(
            stateFlow.map { it.participant }.distinctUntilChanged(),
            chatExistence,
            minAmountFlow,
        ) { participant, exists, fee -> isSendCashReady(participant, exists, fee) }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnSendCashReadinessChanged(it)) }
            .launchIn(viewModelScope)

        transactionController.limits
            .onEach { dispatchEvent(Event.LimitsChanged(it)) }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun initTypingHandlers() {
        // Dispatch typing notifications based on text changes.
        // transformLatest auto-cancels the previous block on each new emission,
        // replacing manual Job tracking for idle timeout and heartbeats.
        snapshotFlow { stateFlow.value.chatInputState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .transformLatest { text ->
                if (!stateFlow.value.typingConstraints.enabled) return@transformLatest

                if (text.isEmpty()) {
                    if (stateFlow.value.isSelfTyping) {
                        emit(Event.OnSelfTypingStopped)
                    }
                    return@transformLatest
                }

                if (!stateFlow.value.isSelfTyping) {
                    emit(Event.OnSelfTypingStarted)
                }

                val constraints = stateFlow.value.typingConstraints
                var elapsed = Duration.ZERO
                while (elapsed < constraints.timeout) {
                    val wait = minOf(constraints.interval, constraints.timeout - elapsed)
                    delay(wait)
                    elapsed += wait
                    if (elapsed < constraints.timeout) {
                        emit(Event.OnSelfTypingStill)
                    }
                }
                emit(Event.OnSelfTypingStopped)
            }
            .onEach { dispatchEvent(it) }
            .launchIn(viewModelScope)

        // Send STOPPED_TYPING when keyboard is dismissed
        eventFlow.filterIsInstance<Event.OnStopMessageInput>()
            .onEach {
                if (stateFlow.value.isSelfTyping) {
                    dispatchEvent(Event.OnSelfTypingStopped)
                }
            }
            .launchIn(viewModelScope)

        // Notify server of typing state changes (fire-and-forget to avoid
        // blocking SharedFlow emission when the gRPC call hangs offline)
        eventFlow.filterIsInstance<Event.OnSelfTypingStarted>()
            .mapNotNull { stateFlow.value.chatId }
            .onEach { viewModelScope.launch { chatCoordinator.notifyTyping(it, TypingState.STARTED_TYPING) } }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnSelfTypingStill>()
            .mapNotNull { stateFlow.value.chatId }
            .onEach { viewModelScope.launch { chatCoordinator.notifyTyping(it, TypingState.STILL_TYPING) } }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnSelfTypingStopped>()
            .mapNotNull { stateFlow.value.chatId }
            .onEach { viewModelScope.launch { chatCoordinator.notifyTyping(it, TypingState.STOPPED_TYPING) } }
            .launchIn(viewModelScope)

        // Observe typing indicators once chatId is known
        stateFlow.mapNotNull { it.chatId }
            .flatMapLatest { chatId -> chatCoordinator.observeTypingIndicators(chatId) }
            .onEach { typists -> dispatchEvent(Event.TypistsUpdated(typists)) }
            .launchIn(viewModelScope)

        // Faces for the typing indicator, from the same profiles the transcript attributes
        // messages with. A typist the roster doesn't cover is asked for, like an unknown sender,
        // and draws the fallback until their profile lands.
        combine(
            stateFlow.map { it.typists }.distinctUntilChanged(),
            stateFlow.map { it.chatType }.distinctUntilChanged(),
            senderProfiles,
            ::Triple,
        )
            .onEach { (typists, _, profiles) ->
                profiles ?: return@onEach
                typists.filter { it.userId.hexEncodedString() !in profiles }
                    .forEach { chatCoordinator.requestSenderProfile(it.userId) }
            }
            .map { (typists, chatType, profiles) ->
                typingAvatars(typists, chatType, profiles.orEmpty())
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.TypingAvatarsUpdated(it)) }
            .launchIn(viewModelScope)

        // A DM opens its composer once a payment has been exchanged. A group has no such
        // exchange to wait for: its own rules say who may post, and [GroupAccess] has already
        // applied them — a group that reaches the composer at all is [GroupAccess.Membered], and
        // one that has not is showing the gate bar instead. Letting a group fall through to the
        // DM rule left it unable to type until someone tipped into it.
        combine(
            stateFlow.mapNotNull { it.chatId }.distinctUntilChanged(),
            stateFlow.map { it.subject is ChatSubject.Group }.distinctUntilChanged(),
            ::Pair,
        )
            .flatMapLatest { (chatId, isGroup) ->
                if (isGroup) {
                    flowOf(true)
                } else {
                    chatCoordinator.observeMessages(chatId)
                        .map { messages ->
                            messages.any { msg -> msg.content.any { it is MessageContent.Cash } }
                        }
                }
            }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.TypingEnabled(it)) }
            .launchIn(viewModelScope)
    }

    private fun initMessageActionHandlers() {
        // Read off the state rather than carried on the event, so the row that copies the link and
        // the row that shares it are handing out the one url [State.groupInviteUrl] builds.
        eventFlow.filterIsInstance<Event.CopyInviteLink>()
            .mapNotNull { stateFlow.value.groupInviteUrl }
            .onEach { url ->
                clipboardManager.setText(
                    text = url,
                    label = resources.getString(R.string.title_clipboardLabelGroupInviteLink),
                )
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CopyMessage>()
            .onEach { event ->
                clipboardManager.setText(
                    text = event.text,
                    label = resources.getString(R.string.title_clipboardLabelMessage),
                )
            }
            .launchIn(viewModelScope)

        // Pre-filling the composer writes to the live TextFieldState, so it runs on the main
        // thread for the same reason clearing it after a send does.
        eventFlow.filterIsInstance<Event.EditMessage>()
            .onEach { event -> stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd(event.text) }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.SubmitEdit>()
            .onEach {
                val editing = stateFlow.value.editing ?: return@onEach
                val chatId = stateFlow.value.chatId ?: return@onEach
                val text = stateFlow.value.chatInputState.text.toString()
                finishEditing(editing)

                // Confirming an unchanged edit is still a way out of edit mode; it just isn't a
                // request. An empty body isn't an edit either — deleting is the other action.
                if (text.isBlank() || text == editing.originalText) return@onEach

                viewModelScope.launch {
                    chatCoordinator.editMessage(chatId, editing.messageId, text)
                        .onFailure { cause ->
                            trace("failed to edit message - ${cause.localizedMessage}")
                            BottomBarManager.showError(
                                title = resources.getString(R.string.title_messageNotEdited),
                                message = resources.getString(R.string.description_messageNotEdited),
                            )
                        }
                }
            }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.CancelEdit>()
            .onEach { finishEditing(stateFlow.value.editing ?: return@onEach) }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.DeleteMessage>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.title_deleteMessage),
                    message = resources.getString(R.string.description_deleteMessage),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_deleteForEveryone),
                        ) {
                            viewModelScope.launch {
                                chatCoordinator.deleteMessage(chatId, event.messageId)
                                    .onFailure { cause ->
                                        trace("failed to delete message - ${cause.localizedMessage}")
                                        BottomBarManager.showError(
                                            title = resources.getString(R.string.title_messageNotDeleted),
                                            message = resources.getString(R.string.description_messageNotDeleted),
                                        )
                                    }
                            }
                        },
                    ),
                    showCancel = true,
                    // Closing the sheet ends the selection either way. Cancelling would otherwise
                    // leave the message alone behind the backdrop with a bar the user just backed
                    // out of, which reads as a second confirmation still pending.
                    onDismiss = { dispatchEvent(Event.ClearMessageSelection) },
                )
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.JoinChat>()
            .onEach {
                val chatId = stateFlow.value.chatId ?: run {
                    dispatchEvent(Event.JoinStateUpdated())
                    return@onEach
                }
                // No optimistic flip. `join` caches the chat and the membership flag comes back
                // through observeMetadata, which is the same path a join from another device takes —
                // one source for the gate rather than two that can disagree.
                chatCoordinator.join(chatId)
                    .onSuccess {
                        // The gate holds its own confirmation rather than waiting to be replaced:
                        // membership comes back through the roster, which can land on the next frame
                        // and swap the checkmark away before it has been drawn. Clearing the success
                        // after the hold is what releases the gate, so the blur fades and the
                        // composer arrives when the button is done rather than when the roster is.
                        dispatchSuccessThen(Event.JoinStateUpdated(success = true)) {
                            dispatchEvent(Event.JoinStateUpdated())
                        }
                    }
                    .onFailure { cause ->
                        trace("failed to join chat - ${cause.localizedMessage}")
                        // A refusal is the only thing that tells the user anything: the gate itself
                        // does not change, because the rules and the balance it reads are what the
                        // server just disagreed with. Saying which refusal it was is the difference
                        // between "buy more" and "this link is dead".
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_failedToJoin),
                            message = resources.getString(
                                when (cause) {
                                    is JoinChatError.RulesNotSatisfied ->
                                        R.string.error_description_joinChat_rulesNotSatisfied
                                    is JoinChatError.Denied ->
                                        R.string.error_description_joinChat_denied
                                    is JoinChatError.NotFound ->
                                        R.string.error_description_joinChat_notFound
                                    else -> R.string.error_description_failedToJoin
                                }
                            ),
                        )
                        dispatchEvent(Event.JoinStateUpdated())
                    }
            }
            .launchIn(viewModelScope)


        eventFlow.filterIsInstance<Event.LeaveChat>()
            .mapNotNull { stateFlow.value.subject as? ChatSubject.Group }
            .onEach { group ->
                BottomBarManager.showAlert(
                    title = resources.getString(
                        R.string.prompt_title_leaveChat,
                        group.title,
                    ),
                    message = resources.getString(R.string.prompt_description_leaveChat),
                    actions = listOf(
                        BottomBarAction(text = resources.getString(R.string.action_leaveChat)) {
                            dispatchEvent(Event.LeaveConfirmed)
                        }
                    ),
                    showCancel = true,
                )
            }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.LeaveConfirmed>()
            .onEach {
                val chatId = stateFlow.value.chatId ?: return@onEach
                // `leave` clears the membership locally before the call, so the gate is back in
                // place by the time the profile closes — the same single source the join reads.
                chatCoordinator.leave(chatId)
                    .onSuccess { dispatchEvent(Event.LeftChat) }
                    .onFailure {
                        trace("failed to leave chat - ${it.localizedMessage}")
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_failedToLeave),
                            message = resources.getString(R.string.error_description_failedToLeave),
                        )
                    }
            }
            .launchIn(viewModelScope)

        // A bubble is what the UI has; a citation is what the composer needs, and building one
        // reads the stored message. A message this device never stored drops the request rather
        // than opening a strip with nothing in it.
        eventFlow.filterIsInstance<Event.ReplyRequested>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                val stored = chatCoordinator.getMessage(chatId, event.bubble.messageId)
                    ?: return@onEach
                dispatchEvent(Event.ReplyToMessage(stored.toQuote()))
            }
            .launchIn(viewModelScope)

        // A distance the device cannot measure is a message it never stored, and no walk would
        // reach it. Resolving here rather than in the list keeps the read off the composition and
        // gives the walk a bound before it starts.
        eventFlow.filterIsInstance<Event.JumpToMessage>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                val distance = chatCoordinator.distanceFromNewest(chatId, event.messageId)
                    ?: return@onEach
                dispatchEvent(Event.JumpResolved(event.messageId, distance))
            }
            .launchIn(viewModelScope)

        // Read once per chat id. The list holds read reporting until this lands, so the pointer
        // it reads is the one the viewer arrived with.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .onEach { chatId ->
                val boundary = chatCoordinator.resolveUnreadBoundary(chatId)
                val budget = (boundary as? UnreadBoundary.At)
                    ?.let { chatCoordinator.countMessagesAfter(chatId, it.readThrough) }
                    ?: 0
                dispatchEvent(Event.UnreadBoundaryResolved(boundary, budget))
            }
            .launchIn(viewModelScope)
    }

    /** Leaves edit mode, restoring the draft the edit interrupted. */
    private fun finishEditing(editing: EditingMessage) {
        stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd(editing.stashedDraft)
        dispatchEvent(Event.EditingEnded)
    }

    private fun initSendHandlers() {
        // Send text message
        eventFlow.filterIsInstance<Event.SendMessage>()
            .onEach {
                val textToSend = stateFlow.value.chatInputState.text.toString()
                val chatId = stateFlow.value.chatId ?: return@onEach
                if (textToSend.isBlank()) return@onEach
                val chatType = stateFlow.value.chatType
                // Read here, not in the reducer: the reply strip comes down with the draft, and
                // both are the composer emptying itself once the message is on its way.
                val replyToMessageId = stateFlow.value.replyingTo?.messageId

                stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd("")
                if (replyToMessageId != null) dispatchEvent(Event.CancelReply)
                // Now, not on delivery: the text is in the pending message from here on, and a
                // send that fails leaves a "Not sent" bubble with a retry, which is the
                // transcript's own record of it. Restoring a draft as well would duplicate it.
                chatDraftStore.saveInBackground(chatId, ChatDraftSnapshot.Empty)

                viewModelScope.launch {
                    chatCoordinator.sendMessage(chatId, textToSend, replyToMessageId)
                        .onSuccess {
                            trace("message sent successfully")
                            analytics.track(ChatEvents.sentMessage(chatType.analytics, null))
                        }
                        .onFailure { cause ->
                            trace("message failed to send - ${cause.localizedMessage}")
                            analytics.track(ChatEvents.sentMessage(chatType.analytics, cause.analytics))
                        }
                }
            }
            .flowOn(Dispatchers.Main.immediate)
            .launchIn(viewModelScope)

        // Retry a failed message
        eventFlow.filterIsInstance<Event.RetryMessage>()
            .onEach { (pendingClientIdHex, content) ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                val pendingId = pendingClientIdHex ?: return@onEach

                BottomBarManager.showInfo(
                    title = resources.getString(R.string.title_messageNotSent),
                    message = resources.getString(R.string.description_messageNotSent),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_retry),
                        ) {
                            viewModelScope.launch {
                                chatCoordinator.retryMessage(chatId, pendingId, listOf(content))
                                    .onSuccess { trace("retry message sent successfully") }
                                    .onFailure { trace("retry message failed - ${it.localizedMessage}") }
                            }
                        },
                    ),
                    showCancel = true,
                )
            }
            .launchIn(viewModelScope)

        // confirmation of amount and checks
        eventFlow.filterIsInstance<Event.OnConfirmRequested>()
            .onEach { onConfirmRequested() }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnInitPaymentConfirmed>()
            .onEach { onConfirmRequested(fixedAmount = minAmountFlow.value) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnSendCash>()
            // Both contact DMs and tip DMs can send cash; the recipient is whichever participant
            // backs the chat. The final send branches on that type (see Event.OnSendRequested).
            .filter { stateFlow.value.participant != null }
            .onEach {
                if (!tokenCoordinator.hasGiveableBalance()) {
                    if (!tokenCoordinator.hasBalance()) {
                        presentAddMoney()
                    } else {
                        presentDiscoverCurrencies()
                    }
                    return@onEach
                }
                // The payment that opens a tip DM costs exactly the recipient's fee, so there is
                // nothing to enter — send it straight to the sheet that states the fee. Every
                // other send, including the moment before the fee resolves, keeps the keypad.
                val fee = minAmountFlow.value
                if (fee != null) {
                    dispatchEvent(Event.NavigateToInitPayment)
                } else {
                    amountDelegate.reset()
                    dispatchEvent(Event.NavigateToAmountEntry)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .onEach {
                analytics.track(AddMoneyEvents.opened(AddMoneySource.CHAT))
                purchaseMethodController.presentDepositOptions()?.let { route ->
                    dispatchEvent(Event.OpenScreen(route))
                }
            }.launchIn(viewModelScope)

        // Send cash. The transfer itself is delegated by chat type: a contact DM pays a phone
        // number (contact metadata), a tip DM pays a user id (tip metadata). Both delegates resolve
        // the recipient, transfer, debit the local balance, and sync the feed; this handler owns the
        // shared amount verification, send state, analytics, and error UI.
        eventFlow.filterIsInstance<Event.OnSendRequested>()
            .onEach { (amount, token) ->
                viewModelScope.launch {
                    val owner = userManager.accountCluster ?: return@launch
                    val rate = exchange.preferredRate

                    dispatchEvent(Event.SendStateUpdated(loading = true))

                    val source = owner.withTimelockForToken(token)

                    val balance = tokenCoordinator.balanceForToken(token)

                    val minimum = minAmountFlow.value
                    if (minimum != null && amount.valueLessThan(minimum)) {
                        dispatchEvent(Event.SendStateUpdated())
                        // Info, not alert: nothing has failed and nothing is being destroyed —
                        // the entry is just under the recipient's floor and needs raising.
                        BottomBarManager.showInfo(
                            title = resources.getString(R.string.error_title_tipMinimum, minimum.formatted()),
                            message = resources.getString(R.string.error_description_tipMinimum),
                        )
                        return@launch
                    }

                    val verifiedFiat = verifiedFiatCalculator.compute(
                        amount = amount,
                        token = token,
                        balance = balance,
                        rate = rate,
                    ).getOrElse { error ->
                        dispatchEvent(Event.SendStateUpdated())
                        val (title, message) = when (error) {
                            is ComputeVerifiedFiatError.AmountBelowMinimum -> {
                                R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
                            }
                            else -> {
                                R.string.error_title_staleRates to R.string.error_description_staleRates
                            }
                        }
                        BottomBarManager.showAlert(
                            title = resources.getString(title),
                            message = resources.getString(message),
                        )
                        return@launch
                    }

                    val chatId = stateFlow.value.chatId

                    // A tip DM's first payment comes from the "Send Tip" call to action, which is
                    // the whole bottom bar until that payment unlocks typing. It says tip, so it
                    // sends one. Every later send comes from the money button beside a composer
                    // that only exists once the thread is unlocked, and stays a plain send.
                    //
                    // This is the one place that decides it, and both `TipDmPayment.action` (the
                    // verb the server renders — "Tipped" vs "Sent") and the analytics event below
                    // are read off this single value rather than each re-deriving "is this a tip."
                    // `origin`/`location` keeps being computed off the same condition as before —
                    // this change doesn't touch what byte it sends. The server reads it in exactly
                    // one place, as the fallback when `action` is `DEFAULT`, so leaving it alone is
                    // what keeps a server that predates `action` resolving the same verb as one
                    // that reads it.
                    val tipAction = if (
                        stateFlow.value.chatType == ChatType.TIP_DM &&
                        !stateFlow.value.typingConstraints.enabled
                    ) {
                        TipAction.TIP
                    } else {
                        TipAction.SEND
                    }

                    val result = when (val participant = stateFlow.value.participant) {
                        is ChatParticipant.Contact -> contactPaymentDelegate.send(
                            contact = participant.contact,
                            chatId = chatId,
                            verifiedFiat = verifiedFiat,
                            token = token,
                            source = source,
                        )
                        is ChatParticipant.TipUser -> tipPaymentDelegate.send(
                            userId = participant.userId,
                            verifiedFiat = verifiedFiat,
                            token = token,
                            source = source,
                            origin = if (tipAction == TipAction.TIP) TipOrigin.TIPCARD else TipOrigin.CHAT,
                            action = tipAction,
                        )
                        null -> {
                            dispatchEvent(Event.SendStateUpdated())
                            return@launch
                        }
                    }

                    // Report what was sent — the tip call to action above is a tip, and every
                    // other send from this screen (contact DM or unlocked tip DM) is a plain cash
                    // send. Same `tipAction` the wire `action` above was set from, not a second
                    // "is this a tip" check that could drift from it.
                    val isTip = tipAction == TipAction.TIP

                    result.onSuccess {
                        dispatchEvent(Event.SendStateUpdated(success = true))
                        delay(400.milliseconds)
                        analytics.track(
                            if (isTip) {
                                TransferEvents.sentTip(AnalyticsState.SUCCESS, verifiedFiat.localFiat.analytics, null)
                            } else {
                                TransferEvents.sentCash(AnalyticsState.SUCCESS, verifiedFiat.localFiat.analytics, null)
                            }
                        )
                        dispatchEvent(
                            Dispatchers.Main,
                            Event.SendComplete(verifiedFiat.localFiat.nativeAmount)
                        )
                    }.onFailure { cause ->
                        dispatchEvent(Event.SendStateUpdated())
                        analytics.track(
                            if (isTip) {
                                TransferEvents.sentTip(AnalyticsState.FAILURE, verifiedFiat.localFiat.analytics, cause.analytics)
                            } else {
                                TransferEvents.sentCash(AnalyticsState.FAILURE, verifiedFiat.localFiat.analytics, cause.analytics)
                            }
                        )
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_cashFailedToSend),
                            message = resources.getString(R.string.error_description_cashFailedToSend),
                        )
                    }
                }
            }.launchIn(viewModelScope)
    }

    /**
     * What the composer would leave behind if this screen went away right now.
     *
     * An in-progress edit contributes the new-message draft it displaced rather than the edit
     * body, so a chat left mid-edit reopens in new-message mode with the user's own words — the
     * same place cancelling the edit would have put them.
     */
    private fun draftSnapshot(): ChatDraftSnapshot {
        val state = stateFlow.value
        return chatDraftOf(
            composerText = state.chatInputState.text.toString(),
            replyTarget = state.replyingTo?.toDraftReply(),
            editStash = state.editing?.stashedDraft,
        )
    }

    /** Writes off [viewModelScope], which is already cancelled by the time [onCleared] runs. */
    private fun flushDraft() {
        val chatId = stateFlow.value.chatId ?: return
        chatDraftStore.saveInBackground(chatId, draftSnapshot())
    }

    @OptIn(FlowPreview::class)
    private fun initDraftHandlers() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(draftFlushObserver)

        // The composer text lives in a TextFieldState, so it never re-emits through stateFlow and
        // needs its own snapshot subscription; the reply strip and the edit stash do come from
        // state. Debounced to keep a database write off the keystroke path — the flushes are what
        // cover the cases a debounce would lose.
        combine(
            snapshotFlow { stateFlow.value.chatInputState.text.toString() },
            stateFlow.map { it.replyingTo }.distinctUntilChanged(),
            stateFlow.map { it.editing }.distinctUntilChanged(),
        ) { _, _, _ -> }
            .drop(1) // the empty composer the screen opens with, before a restore can have run
            .debounce(DRAFT_WRITE_DEBOUNCE)
            .onEach {
                val chatId = stateFlow.value.chatId ?: return@onEach
                chatDraftStore.save(chatId, draftSnapshot())
            }
            .launchIn(viewModelScope)
    }

    /**
     * Puts back what was left in this chat. Silent by design: the text and the reply strip return,
     * focus and the keyboard do not.
     */
    private suspend fun restoreDraft(chatId: ChatId) {
        val draft = chatDraftStore.load(chatId) ?: return
        // Pre-filling the composer writes to the live TextFieldState, so it runs on the main thread.
        withContext(Dispatchers.Main.immediate) {
            stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd(draft.text)
        }
        draft.replyTarget?.let { dispatchEvent(Event.ReplyToMessage(it.toChatQuote())) }
    }

    override fun onCleared() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(draftFlushObserver)
        flushDraft()
        // This chat's claim only, not whatever is active now: opening a chat directly from
        // another one disposes this entry after the incoming one has claimed the chat, so an
        // unconditional clear would silence the chat the user just opened.
        chatCoordinator.clearActiveChat(stateFlow.value.chatId)
        linkCardResolver.dispose()
    }

    private fun checkBalanceLimit(amount: Fiat): Boolean {
        val token = stateFlow.value.token ?: return false
        val rate = exchange.preferredRate
        val balance = tokenCoordinator.balanceForToken(token)
        val balanceInLocal = balance.convertingTo(rate)
        val isOverBalance = amount.valueGreaterThan(balanceInLocal)
        if (isOverBalance) {
            presentInsufficientBalance()
        }
        return isOverBalance
    }

    private fun checkSendLimit(amount: Double): Boolean {
        val currency = amountDelegate.state.value.currency
        val sendLimit =
            currency.code?.let { stateFlow.value.limits?.sendLimitFor(it) } ?: SendLimit.Zero
        val isOverLimit = amount > sendLimit.nextTransaction
        if (isOverLimit) {
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_sendLimitReached),
                resources.getString(R.string.error_description_sendLimitReached),
            )
        }
        return isOverLimit
    }

    /**
     * @param fixedAmount the amount when the flow priced the payment rather than the user typing
     * it — the fee that opens a tip DM. Null reads the keypad entry.
     */
    private fun onConfirmRequested(fixedAmount: Fiat? = null) {
        val enteredAmount = fixedAmount?.toDouble() ?: amountDelegate.state.value.enteredAmount
        val amount = fixedAmount ?: Fiat(enteredAmount, exchange.preferredRate.currency)

        if (checkBalanceLimit(amount) || checkSendLimit(enteredAmount)) return

        if (enteredAmount <= 0) return

        val token = stateFlow.value.token ?: return

        if (stateFlow.value.resolveState is ResolveState.Resolved) {
            dispatchEvent(Event.OnSendRequested(
                amount = amount,
                token = token,
            ))
        }
    }

    /**
     * Over balance, with something in the account: the same prompt the tip card raises, offering
     * the way out of it. [presentAddMoney] covers the empty account, which has nothing to enter a
     * smaller amount than.
     */
    private fun presentInsufficientBalance() {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_insufficientBalance),
            message = resources.getString(R.string.description_insufficientBalanceToUse),
            actions = listOf(
                BottomBarAction(
                    text = resources.getString(R.string.action_addMoney)
                ) {
                    dispatchEvent(Event.PresentDepositOptions)
                },
            ),
            showCancel = true,
        )
    }

    private fun presentAddMoney() {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noBalanceYet),
            message = resources.getString(R.string.description_noBalanceYetToSend),
            actions = listOf(
                BottomBarAction(
                    text = resources.getString(R.string.action_addMoney)
                ) {
                    dispatchEvent(Event.PresentDepositOptions)
                },
            ),
            showCancel = true,
        )
    }

    private fun presentDiscoverCurrencies() {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noCommunityCurrenciesYet),
            message = resources.getString(R.string.description_noCommunityCurrenciesYet),
            actions = listOf(
                BottomBarAction(
                    text = resources.getString(R.string.action_discoverCurrencies)
                ) {
                    dispatchEvent(Event.OpenScreen(AppRoute.Token.Discovery, asSheet = true))
                },
            ),
            showCancel = true,
        )
    }

    companion object {
        /**
         * How often a visible claimable voucher is re-asked about.
         *
         * Picked against what the reader is doing rather than against load: they sent cash in a
         * chat and are waiting to see it collected, so a card that stays stale for the better part
         * of a minute reads as broken. Only a transcript actually showing an unclaimed voucher
         * queries at all — see [refreshLinkCards].
         */
        private val CLAIM_REFRESH_INTERVAL = 15.seconds

        /** Long enough to coalesce a burst of typing, short enough to survive a fast exit. */
        private val DRAFT_WRITE_DEBOUNCE = 300.milliseconds

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            stateReducer(event, UNREAD_DIVIDER_LIFETIME)
        }

        /** [updateStateForEvent] under an explicit [lifetime], so tests can cover both policies. */
        internal fun stateReducer(event: Event, lifetime: UnreadDividerLifetime): (State) -> State =
            when (event) {
                is Event.OnChatOpened -> { state ->
                    when (val id = event.identifier) {
                        is ChatIdentifier.ByContact ->
                            state.copy(
                                subject = ChatSubject.Contact(ChatParticipant.Contact(id.contact)),
                                chatType = ChatType.CONTACT_DM,
                            )
                        is ChatIdentifier.ByChatId -> state
                        // The counterparty is known up front, so the header card and the send gate
                        // resolve on the first frame. Nothing else can supply them here: a chat
                        // reached by username may not exist yet, and a chat with no members has no
                        // profile to observe.
                        is ChatIdentifier.ByUser ->
                            state.copy(
                                subject = ChatSubject.TipUser(
                                    ChatParticipant.TipUser(id.userId, id.profile)
                                ),
                                chatType = ChatType.TIP_DM,
                                resolveState = ResolveState.Resolved,
                            )
                    }
                }
                is Event.OnContactFound -> { state ->
                    state.copy(
                        subject = ChatSubject.Contact(ChatParticipant.Contact(event.contact)),
                        chatType = ChatType.CONTACT_DM,
                    )
                }
                // "No device contact" is also true of every group, so a group that has already
                // resolved keeps its type.
                Event.OnTipDmDetected -> { state ->
                    if (state.subject is ChatSubject.Group) state
                    else state.copy(chatType = ChatType.TIP_DM)
                }
                is Event.OnGroupResolved -> { state ->
                    val metadata = event.membership.metadata
                    val previous = state.subject as? ChatSubject.Group
                    // The access was decided for the old membership and rules. Kept across a change
                    // to either, a stale Eligible would unblur a group whose rules just tightened
                    // until the balance flow caught up, so it goes back to unknown instead.
                    val accessStale = previous == null ||
                        previous.isMember != event.membership.isMember ||
                        previous.rules != metadata.rules
                    state.copy(
                        groupAccess = if (accessStale) null else state.groupAccess,
                        subject = ChatSubject.Group(
                            chatId = metadata.chatId,
                            groupTitle = metadata.title,
                            picture = metadata.picture,
                            memberCount = metadata.rosterSummary.memberCount,
                            rules = metadata.rules,
                            isMember = event.membership.isMember,
                        ),
                        chatType = ChatType.GROUP,
                        resolveState = ResolveState.Resolved,
                    )
                }
                is Event.OnRuleCurrencyResolved ->
                    { state -> state.copy(ruleCurrency = event.currency) }
                is Event.OnGroupAccessResolved -> { state -> state.copy(groupAccess = event.access) }
                Event.JoinChat -> { state ->
                    state.copy(joinProgress = LoadingSuccessState(loading = true))
                }
                is Event.JoinStateUpdated -> { state ->
                    state.copy(
                        joinProgress = LoadingSuccessState(
                            event.loading,
                            event.success,
                        )
                    )
                }
                is Event.OnViewerStateResolved -> { state ->
                    state.copy(viewerState = event.viewerState)
                }
                Event.CopyInviteLink,
                Event.LeaveChat,
                Event.LeaveConfirmed,
                Event.LeftChat -> { state -> state }
                is Event.OnTipUserResolved -> { state ->
                    // A device contact, once matched, wins over the server profile (it carries the
                    // phone number and the user's own naming). Otherwise this is a tip DM: adopt the
                    // profile identity and mark the recipient resolved so the send can proceed (the
                    // tip user is known to exist; the tip send resolves their address at send time).
                    // A group has no counterparty: the member this names is just someone in the room.
                    if (state.subject is ChatSubject.Contact || state.subject is ChatSubject.Group) state
                    else state.copy(
                        subject = ChatSubject.TipUser(
                            ChatParticipant.TipUser(event.userId, event.profile)
                        ),
                        chatType = ChatType.TIP_DM,
                        resolveState = ResolveState.Resolved,
                    )
                }
                is Event.OnCurrencySymbolUpdated -> { state -> state.copy(cashSymbol = event.symbol) }
                is Event.OnChatInitFeeUpdated -> { state -> state.copy(chatInitFee = event.formatted) }
                is Event.OnSendCashReadinessChanged -> { state -> state.copy(sendCashReady = event.ready) }
                is Event.RefreshContact -> { state -> state }
                is Event.ChatFound -> { state -> state.copy(chatId = event.chatId) }
                Event.OnSendCash -> { state -> state }
                Event.OnStartMessageInput -> { state -> state.copy(messageInputRequested = true) }
                Event.OnStopMessageInput -> { state -> state }
                Event.OnMessageInputConsumed -> { state -> state.copy(messageInputRequested = false) }
                is Event.TypistsUpdated -> { state -> state.copy(typists = event.typists) }
                is Event.TypingAvatarsUpdated -> { state -> state.copy(typingAvatars = event.avatars) }
                Event.ResolveCompleted -> { state ->
                    state.copy(resolveState = ResolveState.Resolved)
                }
                is Event.ResolveFailed -> { state ->
                    state.copy(resolveState = ResolveState.Failed)
                }
                is Event.SendMessage -> { state ->
                    val clears = lifetime == UnreadDividerLifetime.UntilSend &&
                        state.unreadBoundary is UnreadBoundary.At
                    if (clears) state.copy(unreadBoundary = UnreadBoundary.None) else state
                }
                is Event.RetryMessage -> { state -> state }
                Event.NavigateToAmountEntry -> { state -> state.copy(sendProgress = LoadingSuccessState()) }
                Event.NavigateToInitPayment -> { state -> state.copy(sendProgress = LoadingSuccessState()) }
                is Event.PresentDepositOptions -> { state -> state }
                is Event.OpenScreen -> { state -> state }
                is Event.OnConfirmRequested -> { state -> state }
                is Event.OnInitPaymentConfirmed -> { state -> state }
                is Event.OnSendRequested -> { state -> state }
                is Event.SendStateUpdated -> { state ->
                    state.copy(
                        sendProgress = LoadingSuccessState(
                            event.loading,
                            event.success,
                        )
                    )
                }
                is Event.SendComplete -> { state -> state }
                Event.OnSelfTypingStarted -> { state -> state.copy(isSelfTyping = true) }
                Event.OnSelfTypingStill -> { state -> state }
                Event.OnSelfTypingStopped -> { state -> state.copy(isSelfTyping = false) }
                is Event.TypingEnabled -> { state ->
                    state.copy(
                        typingConstraints = state.typingConstraints.copy(
                            enabled = event.enabled,
                            resolved = true,
                        )
                    )
                }
                is Event.TokenUpdated -> { state -> state.copy(token = event.token) }
                is Event.LimitsChanged -> { state -> state.copy(limits = event.limits) }
                is Event.AdvanceReadPointer -> { state -> state }
                is Event.ChatDeactivated -> { state -> state.copy(isAnonymous = event.isReadOnly) }
                is Event.MessagePolicyChanged -> { state -> state.copy(messagePolicy = event.policy) }
                is Event.ToggleMessageSelection -> { state ->
                    // By message rather than by row: a message split around its card is one
                    // selection whichever of its rows was pressed.
                    val alreadySelected = state.selection?.messageKey == event.bubble.messageKey
                    // The transcript resolved this bubble when it was mapped, which may have been
                    // well inside a window that has since closed. Narrow it again here so the bar
                    // offers what is open now rather than what was open when the row was built.
                    // The same goes for membership: a viewer who has left since keeps only what a
                    // reader outside the group may do.
                    val selected = event.bubble.takeUnless { alreadySelected }?.let { bubble ->
                        val open = bubble.capabilities
                            .withinWindows(bubble.timestamp, state.messagePolicy)
                        bubble.copy(
                            capabilities = if (state.isOutsideGroup) open.readOnly() else open,
                        )
                    }
                    state.copy(
                        selection = selected,
                        confirmingDelete = false,
                    )
                }
                Event.ClearMessageSelection -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                is Event.CopyMessage -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                Event.ReportRequested -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                is Event.EditMessage -> { state ->
                    state.copy(
                        selection = null,
                        confirmingDelete = false,
                        replyingTo = null,
                        editing = EditingMessage(
                            messageId = event.messageId,
                            originalText = event.text,
                            // Starting a second edit before the first ends must not stash the
                            // first edit's text as if it were the user's draft.
                            stashedDraft = state.editing?.stashedDraft
                                ?: state.chatInputState.text.toString(),
                        ),
                    )
                }
                // Selection survives the confirmation sheet, but the focus does not: the sheet is
                // modal, so the transcript behind it goes uniformly dim until the sheet closes.
                is Event.DeleteMessage -> { state -> state.copy(confirmingDelete = true) }
                Event.SubmitEdit -> { state -> state }
                Event.CancelEdit -> { state -> state }
                Event.EditingEnded -> { state -> state.copy(editing = null) }
                // The strip opens on ReplyToMessage, once the citation resolves; all this does is
                // take the selection bar down so the transcript is legible while that read runs.
                is Event.ReplyRequested -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                is Event.ReplyToMessage -> { state ->
                    state.copy(
                        replyingTo = event.quote,
                        selection = null,
                        confirmingDelete = false,
                        // Reply and edit both own the composer, so opening one closes the other.
                        editing = null,
                    )
                }
                Event.CancelReply -> { state -> state.copy(replyingTo = null) }
                // Nothing on screen moves when a voucher is tapped -- the link leaves, the card
                // keeps saying what it said, and the claim comes back as its own signal.
                is Event.CashLinkOpened -> { state -> state }
                Event.CashLinkRefused -> { state -> state }
                // The request itself changes nothing: the target is only worth holding once the
                // walk's bound resolves, and that read is what decides whether it can be reached.
                is Event.JumpToMessage -> { state -> state }
                is Event.JumpResolved -> { state ->
                    state.copy(jumpTarget = event.messageId, jumpBudget = event.budget)
                }
                Event.JumpConsumed -> { state -> state.copy(jumpTarget = null, jumpBudget = null) }
                is Event.UnreadBoundaryResolved -> { state ->
                    state.copy(unreadBoundary = event.boundary, unreadWalkBudget = event.walkBudget)
                }
            }
    }
}
