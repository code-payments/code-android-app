package com.flipcash.app.messenger.internal

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
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.DeliveryStatus
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.TypingState
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
    private val featureFlags: FeatureFlagController,
    private val analytics: FlipcashAnalyticsService,
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
        val separatorConfig: SeparatorConfig= SeparatorConfig.Continuous(),
        val chatId: ChatId? = null,
        val participant: ChatParticipant? = null,
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
    )

    sealed interface Event {
        data class OnChatOpened(val identifier: ChatIdentifier) : Event
        data class OnContactFound(val contact: DeviceContact): Event
        data class OnTipUserResolved(val userId: ID, val profile: UserProfile): Event
        data class OnCurrencySymbolUpdated(val symbol: String): Event
        data object RefreshContact : Event
        data class ChatFound(val chatId: ChatId) : Event
        data object OnSendCash: Event
        data object OnStartMessageInput: Event
        data object OnStopMessageInput: Event
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
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messageStream = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeMessagesPaged(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val otherReadPointer = stateFlow.mapNotNull { it.chatId }
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeOtherReadPointer(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ChatListItem>> = messageStream
        .map { pagingData ->
            pagingData.flatMap { message ->
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
                    )
                }
            }.insertSeparators { before: ChatListItem.ContentBubble?, after: ChatListItem.ContentBubble? ->
                if (before == null || after == null) return@insertSeparators null
                if (stateFlow.value.separatorConfig.shouldSeparate(before.timestamp, after.timestamp)) {
                    ChatListItem.DateSeparator(before.timestamp)
                } else null
            }
        }.cachedIn(viewModelScope)

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

    // The amount entry adapts to the chat type: a tip DM swipes to *tip* and enforces the minimum
    // tip (from the tip payment delegate); a contact DM swipes to *send* with no minimum.
    private fun amountStyle(isTip: Boolean) = AmountEntryStyle(
        actionLabel = AmountEntryLabel.Plain(
            resources.getString(if (isTip) R.string.action_swipeToTip else R.string.action_swipeToSend)
        ),
        actionStyle = ConfirmationStyle.Slide,
        infoHint = { resources.getString(R.string.subtitle_sendHint, it) },
        overMaxHint = { resources.getString(R.string.subtitle_sendHintLimitExceeded, it) },
        belowMinHint = if (isTip) {
            { min -> resources.getString(R.string.subtitle_tipHintMinimum, min) }
        } else null,
    )

    private val isTipFlow = stateFlow
        .map { it.participant is ChatParticipant.TipUser }
        .distinctUntilChanged()

    /** The [ChatType] backing this conversation, derived from the resolved participant. */
    private val ChatParticipant?.chatType: ChatType
        get() = when (this) {
            is ChatParticipant.TipUser -> ChatType.TIP_DM
            is ChatParticipant.Contact -> ChatType.CONTACT_DM
            null -> ChatType.UNKNOWN
        }

    private val amountStyleFlow by lazy {
        isTipFlow
            .map { amountStyle(isTip = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), amountStyle(isTip = false))
    }

    private val minAmountFlow by lazy {
        combine(isTipFlow, tipPaymentDelegate.minTipAmount) { isTip, tipMin ->
            if (isTip) tipMin else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
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
                            // No device contact backs this chat — it's a tip DM. Warm the member
                            // store (fetch + persist if nothing is cached) so the reactive
                            // tip-identity collector can resolve the counterparty from their server
                            // profile. Identity is set reactively (see initChatHandlers), not here,
                            // so it can't be missed by a fast tap on "Send $".
                            viewModelScope.launch { chatCoordinator.getOtherMember(identifier.chatId) }
                        }
                    }
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
        // their phone), mark the chat as read-only.
        stateFlow.mapNotNull { it.chatId }
            .distinctUntilChanged()
            .flatMapLatest { chatCoordinator.observeMembers(it) }
            .map { members ->
                val selfId = userManager.accountId
                val other = members.firstOrNull { it.userId != selfId }
                if (other != null) {
                    val profile = other.userProfile
                    val hasIdentity = !profile.displayName.isNullOrBlank() ||
                        !profile.verifiedPhoneNumber.isNullOrBlank()
                    !hasIdentity
                } else false
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

    private fun initSendHandlers() {
        // Send text message
        eventFlow.filterIsInstance<Event.SendMessage>()
            .onEach {
                val textToSend = stateFlow.value.chatInputState.text.toString()
                val chatId = stateFlow.value.chatId ?: return@onEach
                if (textToSend.isBlank()) return@onEach
                val chatType = stateFlow.value.participant.chatType

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
                val addMoney = featureFlags.get(FeatureFlag.AddMoneyUX)
                if (!tokenCoordinator.hasGiveableBalance()) {
                    if (!tokenCoordinator.hasBalance()) {
                        presentAddMoney(addMoney)
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
                        )
                        null -> {
                            dispatchEvent(Event.SendStateUpdated())
                            return@launch
                        }
                    }

                    // A payment into a tip DM is a tip; a contact DM is a plain cash send.
                    val transferEvent = if (stateFlow.value.participant is ChatParticipant.TipUser) {
                        Analytics.Transfer.SentTip
                    } else {
                        Analytics.Transfer.SentCash
                    }

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
        super.onCleared()
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
            BottomBarManager.showAlert(
                resources.getString(R.string.error_title_insufficientFunds),
                resources.getString(R.string.error_description_insufficientFunds),
            )
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

    private fun presentAddMoney(addMoneyEnabled: Boolean) {
        val message = if (addMoneyEnabled) {
            resources.getString(R.string.description_noBalanceYetToSend)
        } else {
            resources.getString(R.string.description_noBalanceYetDiscover)
        }
        val cta = if (addMoneyEnabled) {
            resources.getString(R.string.action_addMoney)
        } else {
            resources.getString(R.string.action_discover)
        }
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_noBalanceYet),
            message = message,
            actions = listOf(
                BottomBarAction(
                    text = cta
                ) {
                    if (addMoneyEnabled) {
                        dispatchEvent(Event.PresentDepositOptions)
                    } else {
                        dispatchEvent(Event.OpenScreen(AppRoute.Token.Discovery))
                    }
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
                        is ChatIdentifier.ByContact -> state.copy(participant = ChatParticipant.Contact(id.contact))
                        is ChatIdentifier.ByChatId -> state
                    }
                }
                is Event.OnContactFound -> { state ->
                    state.copy(participant = ChatParticipant.Contact(event.contact))
                }
                is Event.OnTipUserResolved -> { state ->
                    // A device contact, once matched, wins over the server profile (it carries the
                    // phone number and the user's own naming). Otherwise this is a tip DM: adopt the
                    // profile identity and mark the recipient resolved so the send can proceed (the
                    // tip user is known to exist; the tip send resolves their address at send time).
                    if (state.participant is ChatParticipant.Contact) state
                    else state.copy(
                        participant = ChatParticipant.TipUser(event.userId, event.profile),
                        resolveState = ResolveState.Resolved,
                    )
                }
                is Event.OnCurrencySymbolUpdated -> { state -> state.copy(cashSymbol = event.symbol) }
                is Event.RefreshContact -> { state -> state }
                is Event.ChatFound -> { state -> state.copy(chatId = event.chatId) }
                Event.OnSendCash -> { state -> state }
                Event.OnStartMessageInput -> { state -> state }
                Event.OnStopMessageInput -> { state -> state }
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
                        typingConstraints = state.typingConstraints.copy(enabled = event.enabled)
                    )
                }
                is Event.TokenUpdated -> { state -> state.copy(token = event.token) }
                is Event.LimitsChanged -> { state -> state.copy(limits = event.limits) }
                is Event.AdvanceReadPointer -> { state -> state }
                is Event.ChatDeactivated -> { state -> state.copy(isAnonymous = event.isReadOnly) }
            }
        }
    }
}
