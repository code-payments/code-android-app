package com.flipcash.app.messenger.internal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.flatMap
import androidx.paging.insertSeparators
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.AppRoute.ChatIdentifier
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.app.messenger.internal.screens.components.ChatListItem
import com.flipcash.app.messenger.internal.screens.components.ReceiptStatus
import com.flipcash.app.messenger.internal.screens.components.SeparatorConfig
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.buildDmPaymentMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.DeliveryStatus
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.chat.ActiveTypist
import com.flipcash.shared.chat.ChatCoordinator
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
import com.getcode.solana.keys.PublicKey
import com.getcode.util.DateUtils
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Instant

@HiltViewModel
internal class ChatViewModel @Inject constructor(
    private val chatCoordinator: ChatCoordinator,
    private val contactCoordinator: ContactCoordinator,
    private val transactionController: TransactionController,
    private val tokenCoordinator: TokenCoordinator,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val purchaseMethodController: PurchaseMethodController,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) : BaseViewModel<ChatViewModel.State, ChatViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    sealed interface ResolveState {
        data object Pending : ResolveState
        data class Resolved(val authority: PublicKey) : ResolveState
        data object Failed : ResolveState
    }

    sealed interface UserState {
        data object Reading: UserState
        data object Typing: UserState
    }

    data class State(
        val chatId: ChatId? = null,
        val chattingWith: DeviceContact? = null,
        val userState: UserState = UserState.Reading,
        val chatInputState: TextFieldState = TextFieldState(),
        val typists: Set<ActiveTypist> = emptySet(),
        val resolveState: ResolveState = ResolveState.Pending,
        val sendProgress: LoadingSuccessState = LoadingSuccessState(),
        val token: Token? = null,
        val limits: Limits? = null,
        val hasPayment: Boolean = false,
    )

    sealed interface Event {
        data class OnChatOpened(val identifier: ChatIdentifier) : Event
        data class OnContactFound(val contact: DeviceContact): Event
        data class ChatFound(val chatId: ChatId) : Event
        data object OnSendCash: Event
        data object OnStartMessageInput: Event
        data object OnStopMessageInput: Event
        data class TypistsUpdated(val typists: Set<ActiveTypist>) : Event
        data class ResolveCompleted(val authority: PublicKey) : Event
        data object ResolveFailed : Event

        data object SendMessage : Event

        data class NavigateToAmountEntry(val contact: DeviceContact) : Event
        data object PresentDepositOptions : Event
        data class NavigateToUsdfDepositOption(val route: AppRoute): Event

        data object OnConfirmRequested : Event
        data class OnSendRequested(
            val amount: Fiat,
            val token: Token,
            val destinationOwner: PublicKey,
        ) : Event
        data class SendStateUpdated(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event
        data class SendComplete(val amount: Fiat) : Event

        data class TokenUpdated(val token: Token) : Event
        data class LimitsChanged(val limits: Limits?) : Event
        data class HasPaymentBeenMade(val hasPayment: Boolean) : Event
        data class AdvanceReadPointer(val messageId: Long) : Event
    }

    private val separatorConfig = SeparatorConfig.TimeGap()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messageStream = stateFlow.mapNotNull { it.chatId }.distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeMessagesPaged(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val otherReadPointer = stateFlow
        .map { it.chatId }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { chatCoordinator.observeOtherReadPointer(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

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
                    )
                }
            }.insertSeparators { before: ChatListItem.ContentBubble?, after: ChatListItem.ContentBubble? ->
                if (before == null) return@insertSeparators null
                if (after == null || separatorConfig.shouldSeparate(before.timestamp, after.timestamp)) {
                    ChatListItem.DateSeparator(formatDateLabel(before.timestamp))
                } else null
            }
        }.cachedIn(viewModelScope)

    private val maxAmountFlow = combine(
        transactionController.limits,
        tokenCoordinator.observeSelectedTokenMint()
            .flatMapLatest { mint -> tokenCoordinator.balanceForToken(mint) },
        exchange.observePreferredRate(),
    ) { limits, balance, rate ->
        val balanceInLocal = balance.convertingTo(rate)
        val sendLimit = limits?.sendLimitFor(rate.currency) ?: SendLimit.Zero
        Fiat(min(sendLimit.nextTransaction, balanceInLocal.toDouble()), rate.currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = AmountEntryStyle(
            actionLabel = resources.getString(R.string.action_swipeToSend),
            actionStyle = ConfirmationStyle.Slide,
            infoHint = { resources.getString(R.string.subtitle_sendHint, it) },
            overMaxHint = { resources.getString(R.string.subtitle_sendHintLimitExceeded, it) },
        ),
        loadingState = stateFlow.map { it.sendProgress }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState()),
        maxAmount = maxAmountFlow,
    )

    init {
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
                }
            }.launchIn(viewModelScope)

        transactionController.limits
            .onEach { dispatchEvent(Event.LimitsChanged(it)) }
            .launchIn(viewModelScope)

        // Unified chat open handler — resolves chatId and contact from the identifier
        eventFlow
            .filterIsInstance<Event.OnChatOpened>()
            .onEach { event ->
                val identifier = event.identifier

                // 1. Resolve chatId
                val chatId = when (identifier) {
                    is ChatIdentifier.ByContact -> identifier.chatId
                        ?: chatCoordinator.getChatId(
                            DeviceContact.unknownContact(identifier.e164)
                        ).getOrNull()
                    is ChatIdentifier.ByChatId -> identifier.chatId
                }

                if (chatId != null) {
                    dispatchEvent(Event.ChatFound(chatId))
                }

                // 2. Resolve contact
                val contact = when (identifier) {
                    is ChatIdentifier.ByContact -> {
                        contactCoordinator.lookupContact(identifier.e164).getOrElse {
                            DeviceContact.unknownContact(
                                e164 = identifier.e164,
                                displayName = identifier.displayName.takeIf { it.isNotBlank() },
                            )
                        }
                    }
                    is ChatIdentifier.ByChatId -> {
                        val selfId = userManager.accountId
                        val otherMember = chatCoordinator.state.value.feed
                            .firstOrNull { it.chatId == identifier.chatId }
                            ?.members
                            ?.firstOrNull { it.userId != selfId }

                        val otherPhone = otherMember?.userProfile?.verifiedPhoneNumber
                        if (otherPhone != null) {
                            contactCoordinator.lookupContact(otherPhone).getOrElse {
                                DeviceContact.unknownContact(otherPhone)
                            }
                        } else {
                            val displayName = otherMember?.userProfile?.displayName
                                ?.takeIf { it.isNotBlank() }
                            DeviceContact.unknownContact(displayName = displayName)
                        }
                    }
                }
                dispatchEvent(Event.OnContactFound(contact))
            }
            .launchIn(viewModelScope)

        // Resolve owner authority for sending cash
        eventFlow
            .filterIsInstance<Event.OnContactFound>()
            .map { it.contact }
            .map {
                contactCoordinator.resolve(it.e164)
            }.onResult(
                onSuccess = {
                    dispatchEvent(Event.ResolveCompleted(it))
                },
                onError = {
                    dispatchEvent(Event.ResolveFailed)
                }
            ).launchIn(viewModelScope)

        // trigger message update fetch
        stateFlow.map { it.chatId }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { chatCoordinator.loadMessages(it) }
            .launchIn(viewModelScope)

        // Advance read pointer when user scrolls to messages
        eventFlow
            .filterIsInstance<Event.AdvanceReadPointer>()
            .onEach { event ->
                val chatId = stateFlow.value.chatId ?: return@onEach
                chatCoordinator.advanceReadPointer(chatId, event.messageId)
            }
            .launchIn(viewModelScope)

        // Observe typing indicators once chatId is known
        stateFlow.map { it.chatId }
            .filterNotNull()
            .flatMapLatest { chatId -> chatCoordinator.observeTypingIndicators(chatId) }
            .onEach { typists -> dispatchEvent(Event.TypistsUpdated(typists)) }
            .launchIn(viewModelScope)

        // Track whether any payment has been exchanged in this chat
        stateFlow.map { it.chatId }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { chatId ->
                chatCoordinator.observeMessages(chatId)
                    .map { messages ->
                        messages.any { msg -> msg.content.any { it is MessageContent.Cash } }
                    }
                    .distinctUntilChanged()
            }
            .onEach { dispatchEvent(Event.HasPaymentBeenMade(it)) }
            .launchIn(viewModelScope)

        // Send text message
        eventFlow.filterIsInstance<Event.SendMessage>()
            .map { stateFlow.value.chatInputState }
            .mapNotNull { textInput ->
                val textToSend = textInput.text.toString()
                val chatId = stateFlow.value.chatId ?: return@mapNotNull null
                stateFlow.value.chatInputState.clearText()
                chatCoordinator.sendMessage(chatId, textToSend)
            }.onResult(
                onSuccess = {
                    trace("message sent successfully")
                },
                onError = {
                    trace("message failed to send - ${it.localizedMessage}")
                }
            )
            .launchIn(viewModelScope)

        // confirmation of amount and checks
        eventFlow.filterIsInstance<Event.OnConfirmRequested>()
            .onEach { onConfirmRequested() }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnSendCash>()
            .mapNotNull { stateFlow.value.chattingWith }
            .onEach { contact ->
                if (!tokenCoordinator.hasGiveableBalance()) {
                    BottomBarManager.showInfo(
                        title = resources.getString(R.string.title_noBalanceYet),
                        message = resources.getString(R.string.description_noBalanceYet),
                        actions = listOf(
                            BottomBarAction(
                                text = resources.getString(R.string.action_depositFunds)
                            ) {
                                dispatchEvent(Event.PresentDepositOptions)
                            },
                        ),
                        showCancel = true,
                    )
                    return@onEach
                }
                amountDelegate.reset()
                dispatchEvent(Event.NavigateToAmountEntry(contact))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .onEach {
                purchaseMethodController.presentDepositOptions()?.let { route ->
                    dispatchEvent(Event.NavigateToUsdfDepositOption(route))
                }
            }.launchIn(viewModelScope)

        // Send cash
        eventFlow.filterIsInstance<Event.OnSendRequested>()
            .onEach { (amount, token, destination) ->
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

                    val appMetadataBytes = buildDmPaymentMetadata(
                        chatId = stateFlow.value.chatId,
                        sourcePhone = contactCoordinator.selfPhone,
                        destinationPhone = stateFlow.value.chattingWith?.e164,
                    )

                    transactionController.directTransfer(
                        amount = verifiedFiat,
                        token = token,
                        source = source,
                        destinationOwner = destination,
                        appMetadata = appMetadataBytes,
                    ).fold(
                        onSuccess = {
                            tokenCoordinator.subtract(token, verifiedFiat.localFiat)
                            Result.success(verifiedFiat)
                        },
                        onFailure = { Result.failure(it) }
                    ).onSuccess { amount ->
                        dispatchEvent(Event.SendStateUpdated(success = true))
                        stateFlow.value.chatId?.let { chatCoordinator.loadMessages(it) }
                        delay(400)
                        dispatchEvent(
                            Dispatchers.Main,
                            Event.SendComplete(amount.localFiat.nativeAmount)
                        )
                    }.onFailure {
                        dispatchEvent(Event.SendStateUpdated())
                        BottomBarManager.showError(
                            title = resources.getString(R.string.error_title_cashFailedToSend),
                            message = resources.getString(R.string.error_description_cashFailedToSend),
                        )
                    }
                }
            }.launchIn(viewModelScope)
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

        val resolve = stateFlow.value.resolveState
        if (resolve is ResolveState.Resolved) {
            dispatchEvent(Event.OnSendRequested(
                amount = amount,
                token = token,
                destinationOwner = resolve.authority,
            ))
        }
    }

    companion object {
        private fun formatDateLabel(instant: Instant): String {
            return DateUtils.getDateWithToday(instant.toEpochMilliseconds())
        }

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnChatOpened -> { state -> state }
                is Event.OnContactFound -> { state ->
                    state.copy(
                        chattingWith = event.contact
                    )
                }
                is Event.ChatFound -> { state -> state.copy(chatId = event.chatId) }
                Event.OnSendCash -> { state -> state }
                Event.OnStartMessageInput -> { state -> state.copy(userState = UserState.Typing) }
                Event.OnStopMessageInput -> { state -> state.copy(userState = UserState.Reading) }
                is Event.TypistsUpdated -> { state -> state.copy(typists = event.typists) }
                is Event.ResolveCompleted -> { state ->
                    state.copy(resolveState = ResolveState.Resolved(event.authority))
                }
                is Event.ResolveFailed -> { state ->
                    state.copy(resolveState = ResolveState.Failed)
                }
                is Event.SendMessage -> { state -> state }
                is Event.NavigateToAmountEntry -> { state -> state.copy(sendProgress = LoadingSuccessState()) }
                is Event.PresentDepositOptions -> { state -> state }
                is Event.NavigateToUsdfDepositOption -> { state -> state }
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
                is Event.TokenUpdated -> { state -> state.copy(token = event.token) }
                is Event.LimitsChanged -> { state -> state.copy(limits = event.limits) }
                is Event.HasPaymentBeenMade -> { state -> state.copy(hasPayment = event.hasPayment) }
                is Event.AdvanceReadPointer -> { state -> state }
            }
        }
    }
}
