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
import com.flipcash.shared.chat.withinWindows
import com.flipcash.shared.chat.applying
import com.flipcash.shared.chat.resolveCapabilities
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.shared.chat.ui.detectUrls
import com.flipcash.shared.chat.ui.linkableText
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.messenger.internal.link.LinkCardClassifier
import com.flipcash.app.messenger.internal.link.LinkCardResolver
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.TipAction
import com.flipcash.services.models.TipOrigin
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
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
import com.flipcash.shared.chat.ChatMembership
import com.flipcash.shared.payments.ContactPaymentDelegate
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.solana.keys.Mint
import com.getcode.utils.hexEncodedString
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
import com.getcode.ui.utils.generateComplementaryColorPalette
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
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
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalyticsService,
    private val clipboardManager: ClipboardManager,
    private val userFlags: UserFlagsCoordinator,
    private val linkCardClassifier: LinkCardClassifier,
    private val linkCardResolver: LinkCardResolver,
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
        val subject: ChatSubject? = null,
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
        /**
         * The recipient's fee to open a DM with them, already formatted, or null when there is no
         * such fee to name — a contact DM, a tip DM that already exists, or the moment before the
         * profile has resolved. The call-to-action pill renders on the chat's first frame and this
         * arrives over the network, so "no fee yet" and "no fee at all" are deliberately the same
         * value: both mean the pill falls back to its unpriced label.
         */
        val chatInitFee: String? = null,
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
         * The symbol of the token a group's balance requirement names — "BadBoys", not its mint.
         * Null until the token cache has it, and null for any chat without such a rule.
         */
        val ruleTicker: String? = null,
        /**
         * What this viewer may do here. Null for anything that is not a group — a DM has no gate,
         * and rendering one from a default would blur every contact conversation in the app.
         */
        val groupAccess: GroupAccess? = null,
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

        /** The token cache learned the symbol behind the group's balance requirement. */
        data class OnRuleTickerResolved(val ticker: String?) : Event

        /** The gate re-decided, because membership, the rules, or the balance moved. */
        data class OnGroupAccessResolved(val access: GroupAccess) : Event

        /** The gate's "Join Chat" button. */
        data object JoinChat : Event

        /** The group profile's "Leave Chat" row, which puts the confirmation up. */
        data object LeaveChat : Event

        /** The user confirmed the leave. */
        data object LeaveConfirmed : Event

        /** The leave went through, so whatever is showing the group's profile should close. */
        data object LeftChat : Event
        data class OnCurrencySymbolUpdated(val symbol: String): Event
        data class OnChatInitFeeUpdated(val formatted: String?) : Event
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

        /** Asks the transcript to scroll to [messageId] — a tap on a quote. */
        data class JumpToMessage(val messageId: Long) : Event

        /** The same request, once the walk's bound is known. */
        data class JumpResolved(val messageId: Long, val budget: Int) : Event
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ChatListItem>> =
        combine(
            messageStream,
            pendingMutations,
            messagePolicy,
            senderProfiles,
        ) { pagingData, mutations, policy, profiles ->
            pagingData.flatMap { stored ->
                val message = stored.applying(mutations[stored.messageId])
                message.content.mapIndexed { index, content ->
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
                    // Resolution is memoized per entropy: the first pass over a message pays the
                    // query, every re-map after it is free, and a message whose query has not
                    // returned renders unresolved and picks the amount up on the next pass.
                    val linkCard = enriched.linkableText()
                        ?.let { text -> linkCardClassifier.firstCard(detectUrls(text)) }
                        ?.let { card -> linkCardResolver.resolve(card) }

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
                        capabilities = resolveCapabilities(message, policy),
                        quote = quote,
                        sender = sender,
                        // Independent of the profile lookup above: the runs have to break by
                        // author from the first frame, and the map that names the authors lands
                        // after the first page does.
                        senderId = message.senderId?.takeIf { !message.isFromSelf },
                        linkCard = linkCard,
                    )
                }
            }.insertSeparators { before: ChatListItem.ContentBubble?, after: ChatListItem.ContentBubble? ->
                if (before == null || after == null) return@insertSeparators null
                if (stateFlow.value.separatorConfig.shouldSeparate(before.timestamp, after.timestamp)) {
                    ChatListItem.DateSeparator(before.timestamp)
                } else null
            }
        }

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
        // Ahead of the transcript, so the first mapping already has the real windows rather than
        // the defaults, which leave both edit and delete open.
        userFlags.resolvedFlags
            .map {
                MessagePolicy(
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

        // The cache starts empty and fills in, so this is observed rather than read once — a
        // requirement resolved against an empty cache would render its amount with no token beside
        // it and never correct itself.
        combine(
            stateFlow.map { (it.subject as? ChatSubject.Group)?.rules.balanceRequirement() }
                .distinctUntilChanged(),
            tokenCoordinator.observeTokenCache(),
        ) { requirement, tokens ->
            requirement?.mints?.firstOrNull()?.let { tokens[Mint(it.bytes)]?.symbol }
        }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnRuleTickerResolved(it)) }
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
                    tokenCoordinator.groupAccess(isMember = group.isMember, rules = group.rules)
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

        // The same floor the amount entry enforces, said out loud on the button that has to charge
        // it. Formatted here rather than in the composable so the button has no currency logic.
        minAmountFlow
            .onEach { dispatchEvent(Event.OnChatInitFeeUpdated(it?.formatted())) }
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
                val chatId = stateFlow.value.chatId ?: return@onEach
                // No optimistic flip. `join` caches the chat and the membership flag comes back
                // through observeMetadata, which is the same path a join from another device takes —
                // one source for the gate rather than two that can disagree.
                chatCoordinator.join(chatId)
                    .onFailure { trace("failed to join chat - ${it.localizedMessage}") }
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

                viewModelScope.launch {
                    chatCoordinator.sendMessage(chatId, textToSend, replyToMessageId)
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
                    val transferEvent =
                        if (tipAction == TipAction.TIP) Analytics.Transfer.SentTip else Analytics.Transfer.SentCash

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
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
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
                Event.OnTipDmDetected -> { state -> state.copy(chatType = ChatType.TIP_DM) }
                is Event.OnGroupResolved -> { state ->
                    val metadata = event.membership.metadata
                    state.copy(
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
                is Event.OnRuleTickerResolved -> { state -> state.copy(ruleTicker = event.ticker) }
                is Event.OnGroupAccessResolved -> { state -> state.copy(groupAccess = event.access) }
                Event.JoinChat,
                Event.LeaveChat,
                Event.LeaveConfirmed,
                Event.LeftChat -> { state -> state }
                is Event.OnTipUserResolved -> { state ->
                    // A device contact, once matched, wins over the server profile (it carries the
                    // phone number and the user's own naming). Otherwise this is a tip DM: adopt the
                    // profile identity and mark the recipient resolved so the send can proceed (the
                    // tip user is known to exist; the tip send resolves their address at send time).
                    if (state.subject is ChatSubject.Contact) state
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
                    val alreadySelected = state.selection?.itemKey == event.bubble.itemKey
                    // The transcript resolved this bubble when it was mapped, which may have been
                    // well inside a window that has since closed. Narrow it again here so the bar
                    // offers what is open now rather than what was open when the row was built.
                    val selected = event.bubble.takeUnless { alreadySelected }?.let { bubble ->
                        bubble.copy(
                            capabilities = bubble.capabilities
                                .withinWindows(bubble.timestamp, state.messagePolicy),
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
                // The request itself changes nothing: the target is only worth holding once the
                // walk's bound resolves, and that read is what decides whether it can be reached.
                is Event.JumpToMessage -> { state -> state }
                is Event.JumpResolved -> { state ->
                    state.copy(jumpTarget = event.messageId, jumpBudget = event.budget)
                }
                Event.JumpConsumed -> { state -> state.copy(jumpTarget = null, jumpBudget = null) }
            }
        }
    }
}
