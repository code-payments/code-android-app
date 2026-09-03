package com.flipcash.app.messenger.internal

import android.content.ClipboardManager
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.MessagePolicy
import com.flipcash.shared.chat.applying
import com.flipcash.shared.chat.resolveCapabilities
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.DeliveryStatus
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.models.chat.isDmAddressable
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ActiveTypist
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.opencode.model.core.ID
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    userFlags: UserFlagsCoordinator,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalyticsService,
    private val clipboardManager: ClipboardManager,
) : BaseViewModel<ChatViewModel.State, ChatViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
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
        val participant: ChatParticipant? = null,
        // The kind of DM this conversation is, resolved from the fast local contact lookup ahead of
        // the participant's server profile (which resolves over the network for tip DMs). Starts
        // UNKNOWN and settles to CONTACT_DM / TIP_DM as soon as the chat opens; the send button and
        // bottom bar read it to render the correct (condensed vs expanded) presentation immediately
        // instead of flashing the expanded white pill while a tip profile loads.
        val chatType: ChatType = ChatType.UNKNOWN,
        val chatInputState: TextFieldState = TextFieldState(),
        val typists: Set<ActiveTypist> = emptySet(),
        val resolveState: ResolveState = ResolveState.Pending,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
        val isSelfTyping: Boolean = false,
        val typingConstraints: TypingConstraints = TypingConstraints(),
        val token: Token? = null,
        val limits: Limits? = null,
        val isAnonymous: Boolean = false,
        val cashSymbol: String = "$",
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
         * True while the delete confirmation is up.
         *
         * The sheet is modal, so nothing behind it should still read as the focus: the selected
         * message falls back behind the backdrop with the rest of the transcript until the sheet
         * closes, rather than sitting sharp and half-clipped at the sheet's own edge.
         */
        val confirmingDelete: Boolean = false,
    ) {
        // Opening the participant's profile (the entry point to blocking) is only available for tip DMs.
        val canViewProfile: Boolean
            get() = chatType == ChatType.TIP_DM

        /** What the selection bar may offer, straight from what the transcript already resolved. */
        val selectionCapabilities: Set<MessageCapability>
            get() = selection?.capabilities.orEmpty()
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
        data class OnCurrencySymbolUpdated(val symbol: String): Event
        data object RefreshContact : Event
        data class ChatFound(val chatId: ChatId) : Event
        data object OnSendCash: Event
        data object OnStartMessageInput: Event
        data object OnStopMessageInput: Event
        data object OnMessageInputConsumed: Event
        data class TypistsUpdated(val typists: Set<ActiveTypist>) : Event
        data object ResolveCompleted : Event
        data object ResolveFailed : Event

        data object SendMessage : Event
        data class RetryMessage(val pendingId: String?, val content: MessageContent) : Event

        data object NavigateToAmountEntry : Event
        data object PresentDepositOptions : Event
        data class OpenScreen(val route: AppRoute, val asSheet: Boolean = false): Event
        data object OnConfirmRequested : Event
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

        /** Selects [bubble], or leaves selection mode if it is already the selected one. */
        data class ToggleMessageSelection(val bubble: ChatListItem.ContentBubble) : Event
        data object ClearMessageSelection : Event

        // The message actions carry what they act on rather than reading it back off the selection:
        // the reducer runs before the handlers do, so an action that dismisses the selection bar
        // would otherwise have cleared its own subject before the handler saw it.
        data class CopyMessage(val text: String) : Event
        data class EditMessage(val messageId: Long, val text: String) : Event
        data class DeleteMessage(val messageId: Long) : Event

        data object SubmitEdit : Event
        data object CancelEdit : Event
        data object EditingEnded : Event
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
     * The edit/delete windows the transcript gates on.
     *
     * `resolvedFlags` is a `StateFlow` seeded with `UserFlags.Default`, and falls back to it
     * whenever the server flags are absent — a failed fetch, or the window before the first one
     * lands. `UserFlags.Default` carries `null` for both windows, so the failed-fetch case arrives
     * here as the same `null` an explicitly-unset server field would, and [MessagePolicy.from]
     * substitutes the fallback window for both without a second branch.
     */
    private val messagePolicy = userFlags.resolvedFlags
        .map {
            MessagePolicy.from(
                editWindow = it.messageEditWindow.effectiveValue,
                deleteWindow = it.messageDeleteWindow.effectiveValue,
            )
        }
        .distinctUntilChanged()

    /**
     * Drives re-resolution of capabilities so a row loses Edit and Delete when its window closes.
     *
     * Capabilities are resolved once per mapping pass, so without this a message resolved at send
     * time keeps Edit forever: nothing upstream re-emits when a window merely lapses. With a
     * 15-minute default edit window that is an ordinary session, not a corner case — leave a chat
     * open, scroll back, and the menu offers an edit the server will reject.
     *
     * A poll rather than a timer armed at each message's expiry: the transcript is paged, so the
     * set of loaded messages (and therefore the next expiry) changes as the user scrolls, and
     * tracking that is more machinery than the problem is worth. The cost of the poll is bounded —
     * it re-runs the mapping, not the fetch, because [messageStream] is cached above it, and the
     * token metadata the mapping enriches with is memory-cached. The cost of the interval is up to
     * [CapabilityRefreshInterval] of staleness at each boundary, during which a lapsed row still
     * offers its action and the server answers `CANNOT_EDIT` / `CANNOT_DELETE`. That is the same
     * race the gating cannot close anyway: a menu resolved a moment before expiry is stale by the
     * time it is tapped whatever the interval.
     *
     * This covers the transcript, not an already-open selection bar — `State.selection` holds the
     * bubble captured at long-press, and keeps the capabilities it was captured with. Selection is
     * a few seconds of user attention rather than a row parked on screen, so it is left to the
     * server error.
     */
    private val capabilityClock = flow {
        while (true) {
            emit(Clock.System.now())
            delay(CapabilityRefreshInterval)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ChatListItem>> =
        combine(
            messageStream,
            pendingMutations,
            messagePolicy,
            capabilityClock,
        ) { pagingData, mutations, policy, now ->
            pagingData.flatMap { stored ->
                val message = stored.applying(mutations[stored.messageId])
                message.content.mapIndexed { index, content ->
                    val enriched = if (content is MessageContent.Cash && content.tokenName.isBlank()) {
                        val token = tokenCoordinator.getTokenMetadata(content.mint).getOrNull()?.token
                        if (token != null) {
                            content.copy(tokenName = token.name, tokenImageUrl = token.imageUrl)
                        } else content
                    } else content

                    val receiptStatus = if (message.isFromSelf) {
                        when (message.deliveryStatus) {
                            DeliveryStatus.SENDING -> ReceiptStatus.SENDING
                            DeliveryStatus.FAILED -> ReceiptStatus.FAILED
                            DeliveryStatus.SENT -> ReceiptStatus.SENT
                        }
                    } else null

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
                        // Resolved here, so no menu re-derives it: a later group-role taxonomy
                        // becomes another input to the resolver rather than a branch at each
                        // action site. Re-resolved on every pass rather than once per message,
                        // because `policy` and `now` both move — see `capabilityClock`.
                        capabilities = resolveCapabilities(message, policy, now),
                    )
                }
            }.insertSeparators { before: ChatListItem.ContentBubble?, after: ChatListItem.ContentBubble? ->
                if (before == null || after == null) return@insertSeparators null
                if (stateFlow.value.separatorConfig.shouldSeparate(before.timestamp, after.timestamp)) {
                    ChatListItem.DateSeparator(before.timestamp)
                } else null
            }
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
        actionLabel = AmountEntryLabel.Plain(
            resources.getString(
                if (isTip) R.string.action_swipeToTip else R.string.action_swipeToSend
            )
        ),
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
     * created has a member row, one derived from a user id alone does not.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val isChatInitialized by lazy {
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMembers(it) }
            .map { it.isNotEmpty() }
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

    init {
        // Essential — needed immediately for chat display
        initChatHandlers()

        viewModelScope.launch {
            // Yield to let the first frame render before setting up remaining collectors
            initTokenAndExchangeObservers()
            initTypingHandlers()
            initSendHandlers()
            initMessageActionHandlers()
        }
    }

    private fun initChatHandlers() {
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
                    chatCoordinator.setActiveChatId(chatId)
                    viewModelScope.launch { chatCoordinator.loadMessages(chatId) }
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
                        if (contact != null) {
                            dispatchEvent(Event.OnContactFound(contact))
                        } else {
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
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMembers(it) }
            .mapNotNull { members -> members.firstOrNull { it.userId != userManager.accountId } }
            .distinctUntilChanged()
            .onEach { member -> dispatchEvent(Event.OnTipUserResolved(member.userId, member.userProfile)) }
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

        // Advance read pointer when user scrolls to messages
        eventFlow
            .filterIsInstance<Event.AdvanceReadPointer>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
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

        // Enable typing notifications once a payment has been exchanged
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatId ->
                chatCoordinator.observeMessages(chatId)
                    .map { messages ->
                        messages.any { msg -> msg.content.any { it is MessageContent.Cash } }
                    }
                    .distinctUntilChanged()
            }
            .onEach { dispatchEvent(Event.TypingEnabled(it)) }
            .launchIn(viewModelScope)
    }

    private fun initMessageActionHandlers() {
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

                stateFlow.value.chatInputState.setTextAndPlaceCursorAtEnd("")

                viewModelScope.launch {
                    chatCoordinator.sendMessage(chatId, textToSend)
                        .onSuccess {
                            trace("message sent successfully")
                            analytics.messageSentInChat(type = chatType)
                        }
                        .onFailure { cause ->
                            trace("message failed to send - ${cause.localizedMessage}")
                            analytics.messageSentInChat(type = chatType, error = cause)
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
                amountDelegate.reset()
                dispatchEvent(Event.NavigateToAmountEntry)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .onEach {
                analytics.addMoneyOpened(Analytics.AddMoneySource.Chat)
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
                    // `TIPCARD` is how a tip is asked for: `TipDmPayment.Location` has two values,
                    // and the server reads them as the verb ("Tipped" vs "Sent") rather than as a
                    // place. Sending `CHAT` here would title the payment "Sent" in the recipient's
                    // activity feed, under a button that promised a tip.
                    val isTip = stateFlow.value.chatType == ChatType.TIP_DM &&
                        !stateFlow.value.typingConstraints.enabled

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
                            origin = if (isTip) TipOrigin.TIPCARD else TipOrigin.CHAT,
                        )
                        null -> {
                            dispatchEvent(Event.SendStateUpdated())
                            return@launch
                        }
                    }

                    // Report what was sent, on the same line `TipDmPayment.Location` draws: the
                    // tip call to action above is a tip, and every other send from this screen —
                    // contact DM or unlocked tip DM — is a plain cash send.
                    val transferEvent =
                        if (isTip) Analytics.Transfer.SentTip else Analytics.Transfer.SentCash

                    result.onSuccess {
                        dispatchEvent(Event.SendStateUpdated(success = true))
                        delay(400.milliseconds)
                        analytics.transfer(
                            event = transferEvent,
                            amount = verifiedFiat.localFiat,
                            successful = true,
                        )
                        dispatchEvent(
                            Dispatchers.Main,
                            Event.SendComplete(verifiedFiat.localFiat.nativeAmount)
                        )
                    }.onFailure { cause ->
                        dispatchEvent(Event.SendStateUpdated())
                        analytics.transfer(
                            event = transferEvent,
                            amount = verifiedFiat.localFiat,
                            error = cause,
                        )
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_cashFailedToSend),
                            message = resources.getString(R.string.error_description_cashFailedToSend),
                        )
                    }
                }
            }.launchIn(viewModelScope)
    }

    override fun onCleared() {
        chatCoordinator.setActiveChatId(null)
    }

    private fun checkBalanceLimit(): Boolean {
        val amount = amountDelegate.state.value.enteredAmount
        val token = stateFlow.value.token ?: return false
        val rate = exchange.preferredRate
        val entered = Fiat(amount, rate.currency)
        val balance = tokenCoordinator.balanceForToken(token)
        val balanceInLocal = balance.convertingTo(rate)
        val isOverBalance = entered.valueGreaterThan(balanceInLocal)
        if (isOverBalance) {
            presentInsufficientBalance()
        }
        return isOverBalance
    }

    private fun checkSendLimit(): Boolean {
        val amount = amountDelegate.state.value.enteredAmount
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

    private fun onConfirmRequested() {
        if (checkBalanceLimit() || checkSendLimit()) return

        val enteredAmount = amountDelegate.state.value.enteredAmount
        if (enteredAmount <= 0) return

        val token = stateFlow.value.token ?: return
        val rate = exchange.preferredRate
        val amount = Fiat(enteredAmount, rate.currency)

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
         * How often the transcript re-resolves capabilities so lapsed windows drop their actions.
         *
         * Coarse on purpose. It bounds how long a lapsed row keeps offering an action, and the
         * shortest window it has to bound is the 15-minute edit default, so seconds of slack cost
         * nothing a user can act on faster than the server can answer.
         */
        private val CapabilityRefreshInterval = 30.seconds

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnChatOpened -> { state ->
                    when (val id = event.identifier) {
                        is ChatIdentifier.ByContact ->
                            state.copy(
                                participant = ChatParticipant.Contact(id.contact),
                                chatType = ChatType.CONTACT_DM,
                            )
                        is ChatIdentifier.ByChatId -> state
                        // The counterparty is known up front, so the header card and the send gate
                        // resolve on the first frame. Nothing else can supply them here: a chat
                        // reached by username may not exist yet, and a chat with no members has no
                        // profile to observe.
                        is ChatIdentifier.ByUser ->
                            state.copy(
                                participant = ChatParticipant.TipUser(id.userId, id.profile),
                                chatType = ChatType.TIP_DM,
                                resolveState = ResolveState.Resolved,
                            )
                    }
                }
                is Event.OnContactFound -> { state ->
                    state.copy(
                        participant = ChatParticipant.Contact(event.contact),
                        chatType = ChatType.CONTACT_DM,
                    )
                }
                Event.OnTipDmDetected -> { state -> state.copy(chatType = ChatType.TIP_DM) }
                is Event.OnTipUserResolved -> { state ->
                    // A device contact, once matched, wins over the server profile (it carries the
                    // phone number and the user's own naming). Otherwise this is a tip DM: adopt the
                    // profile identity and mark the recipient resolved so the send can proceed (the
                    // tip user is known to exist; the tip send resolves their address at send time).
                    if (state.participant is ChatParticipant.Contact) state
                    else state.copy(
                        participant = ChatParticipant.TipUser(event.userId, event.profile),
                        chatType = ChatType.TIP_DM,
                        resolveState = ResolveState.Resolved,
                    )
                }
                is Event.OnCurrencySymbolUpdated -> { state -> state.copy(cashSymbol = event.symbol) }
                is Event.RefreshContact -> { state -> state }
                is Event.ChatFound -> { state -> state.copy(chatId = event.chatId) }
                Event.OnSendCash -> { state -> state }
                Event.OnStartMessageInput -> { state -> state.copy(messageInputRequested = true) }
                Event.OnStopMessageInput -> { state -> state }
                Event.OnMessageInputConsumed -> { state -> state.copy(messageInputRequested = false) }
                is Event.TypistsUpdated -> { state -> state.copy(typists = event.typists) }
                Event.ResolveCompleted -> { state ->
                    state.copy(resolveState = ResolveState.Resolved)
                }
                is Event.ResolveFailed -> { state ->
                    state.copy(resolveState = ResolveState.Failed)
                }
                is Event.SendMessage -> { state -> state }
                is Event.RetryMessage -> { state -> state }
                Event.NavigateToAmountEntry -> { state -> state.copy(sendProgress = LoadingSuccessState()) }
                is Event.PresentDepositOptions -> { state -> state }
                is Event.OpenScreen -> { state -> state }
                is Event.OnConfirmRequested -> { state -> state }
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
                is Event.ToggleMessageSelection -> { state ->
                    val alreadySelected = state.selection?.itemKey == event.bubble.itemKey
                    state.copy(
                        selection = event.bubble.takeUnless { alreadySelected },
                        confirmingDelete = false,
                    )
                }
                Event.ClearMessageSelection -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                is Event.CopyMessage -> { state ->
                    state.copy(selection = null, confirmingDelete = false)
                }
                is Event.EditMessage -> { state ->
                    state.copy(
                        selection = null,
                        confirmingDelete = false,
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
            }
        }
    }
}
