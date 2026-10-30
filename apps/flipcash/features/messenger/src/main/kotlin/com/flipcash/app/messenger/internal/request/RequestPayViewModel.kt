package com.flipcash.app.messenger.internal.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.GroupRosters
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.flipcash.app.analytics.FlipcashAnalytics
import javax.inject.Inject

/**
 * The chat's side of paying a request: the deck's Pay opens the swipe sheet over the conversation,
 * without going through Request details. The logic is [RequestPayFlow], the same one details uses.
 */
@HiltViewModel
internal class RequestPayViewModel @Inject constructor(
    private val rosters: GroupRosters,
    payments: PaymentRequestPaymentDelegate,
    payer: StartChattingPayer,
    tokenCoordinator: TokenCoordinator,
    transactionController: TransactionController,
    exchange: Exchange,
    userManager: UserManager,
    resources: ResourceHelper,
    analytics: FlipcashAnalytics,
) : ViewModel() {

    private val effects = Channel<AppRoute>(Channel.BUFFERED)

    /** Routes to open once the sheet has closed, i.e. Add Money. */
    val routes: Flow<AppRoute> = effects.receiveAsFlow()

    private val flow = RequestPayFlow(
        scope = viewModelScope,
        payments = payments,
        payer = payer,
        tokenCoordinator = tokenCoordinator,
        transactionController = transactionController,
        exchange = exchange,
        userManager = userManager,
        resources = resources,
        analytics = analytics,
        openScreen = { route -> effects.trySend(route) },
    )

    private val chatId = MutableStateFlow<ChatId?>(null)

    val state: StateFlow<RequestPayFlow.State> = flow.state
    val token: StateFlow<Token?> get() = flow.token
    val actions: SwipePayActions = flow.actions

    /** The chat's members by id, for the creator's name and avatar on the sheet. */
    val members: StateFlow<Map<ID, ChatMember>> = combine(chatId, rosters.state) { id, all ->
        all[id].orEmpty().associateBy { it.userId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun pay(chatId: ChatId, requestId: ID) {
        this.chatId.value = chatId
        if (rosters.state.value[chatId] == null) viewModelScope.launch { rosters.load(chatId) }
        flow.start(requestId)
    }
}
