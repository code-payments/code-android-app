package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.messenger.internal.swipepay.SwipePayActions
import com.flipcash.app.tokens.CollectionFundingPlan
import com.flipcash.app.tokens.ResourceCollectionFundingStrings
import com.getcode.solana.keys.base58
import com.flipcash.app.core.tokens.brandedName
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.request.CollectionFundingError
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.LoadingSuccessState
import com.getcode.view.holdPaymentSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Paying the viewer's share of one payment request: refetch for the intent id, the swipe sheet,
 * the post-swipe balance and limit check against the token picked in the sheet, and every outcome
 * that follows. Shared by Request details and the chat, which each own an instance on their own
 * scope and draw the sheet from [state].
 *
 * The share's payment intent id lives in [ready] only, for as long as the sheet is up. It is never
 * put in [State], so it can't reach a log through the state's toString.
 *
 * The sheet draws from [State.amount] and [State.creator], not the live request: once the payment
 * lands, the request reads paid (or closes, or leaves the deck) during the success hold, and the
 * sheet has to keep its amount through the checkmark and the dismiss.
 */
internal class RequestPayFlow(
    private val scope: CoroutineScope,
    private val payments: PaymentRequestPaymentDelegate,
    private val payer: StartChattingPayer,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionController: TransactionController,
    private val exchange: Exchange,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
    /** Where "Add Money" goes, once the sheet has closed. */
    private val openScreen: (AppRoute) -> Unit,
    /** The server has no payment intent for the viewer on this request any more. */
    private val onNothingToPay: () -> Unit = {},
    /** The viewer can no longer reach the request (a collection's DENIED or NOT_FOUND). */
    private val onUnavailable: () -> Unit = {},
    /** The payment landed, before the refetched request may say so. */
    private val onPaid: () -> Unit = {},
) {
    data class State(
        val requestId: ID? = null,
        /** Refetching before the sheet opens: the caller's Pay button spins. */
        val preparing: Boolean = false,
        /** The swipe sheet is up. */
        val open: Boolean = false,
        val progress: LoadingSuccessState = LoadingSuccessState(),
        /** What the open sheet pays, as fetched when it opened: a split's share or a collection's amount. */
        val amount: Fiat? = null,
        /** Who it is paid to. */
        val creator: ID? = null,
        /**
         * Group Money is on and the open sheet pays a collection: a short balance in [token] is
         * covered by converting the viewer's other balances first, and [token] is the collection's
         * own currency, not the wallet's selected one (group-collections spec, "Funding a payment").
         */
        val funding: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The currency picked in the open collection sheet; null until the viewer picks one. */
    private val collectionPick = MutableStateFlow<Mint?>(null)

    /**
     * What the sheet pays from. A split pays from the wallet's selected token. A collection being
     * funded pays in the currency picked in its sheet, else the one the viewer last paid a
     * collection in, else Dollars, and shows it whatever its balance.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val token: StateFlow<Token?> = _state.map { it.funding }.distinctUntilChanged()
        .flatMapLatest { funding -> if (funding) collectionToken() else payer.observeSelectedToken() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private fun collectionToken(): Flow<Token> =
        combine(collectionPick, payments.lastPaidCollectionCurrency(), tokenCoordinator.tokens) { pick, lastPaid, tokens ->
            val known = tokens.associateBy { it.address }
            listOfNotNull(pick, lastPaid).firstNotNullOfOrNull { known[it] } ?: known[Mint.usdf] ?: Token.usdf
        }

    /**
     * How the open collection sheet's amount would be paid in [token] from the balances as they
     * stand, for its fee line and Payment Details. Null unless [State.funding]. Planning moves no
     * money: picking another currency only replans.
     */
    val plan: StateFlow<CollectionFundingPlan?> = combine(
        _state,
        token,
        tokenCoordinator.tokenBalances.map { }.onStart { emit(Unit) },
    ) { state, token, _ ->
        val amount = state.amount
        if (!state.funding || amount == null || token == null) null else payments.planCollection(amount, token)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The open collection sheet's "Select Payment Currency" rows (frame 6.3), valued in the
     * request's currency for their order. Empty unless [State.funding].
     */
    val collectionCurrencies: StateFlow<List<TokenWithLocalizedBalance>> = combine(
        _state,
        token,
        tokenCoordinator.tokenBalances,
    ) { state, token, balances ->
        val amount = state.amount
        if (!state.funding || amount == null) return@combine emptyList()
        val rate = exchange.rateFor(amount.currencyCode) ?: exchange.rateForUsd()
        collectionCurrencies(balances, token, rate)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The open collection sheet's fee line and Payment Details, from [plan]. */
    val fundingUi: StateFlow<CollectionFundingUi?> = combine(_state, plan) { state, plan ->
        val amount = state.amount
        if (plan == null || amount == null) return@combine null
        val known = tokenCoordinator.cachedTokens().associateBy { it.address.base58() } +
            (Token.usdf.address.base58() to Token.usdf)
        collectionFundingUi(
            plan = plan,
            amount = amount,
            tokenOf = known::get,
            nameOf = { it.brandedName(resources) },
            topUpSourceName = resources.getString(R.string.label_topUpSourceGooglePay),
            strings = ResourceCollectionFundingStrings(resources),
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private var ready: ShareToPay.Ready? = null

    private val viewerId: ID? get() = userManager.accountId

    /** Everything the sheet asks of this flow, in one place. */
    val actions = SwipePayActions(onConfirm = ::confirm, onDismiss = ::dismiss)

    /**
     * "Send <share>" / the deck's Pay: refetch for the intent id, then show the sheet if there is
     * still a share to pay. [fund] is the Group Money flag: with it, a collection is paid in its
     * own currency and funded by conversion when short (see [State.funding]).
     */
    fun start(requestId: ID, fund: Boolean = false) {
        val viewer = viewerId ?: return
        if (_state.value.preparing || _state.value.open) return
        _state.update { it.copy(requestId = requestId, preparing = true) }
        scope.launch {
            payments.prepare(requestId, viewer)
                .onSuccess { found ->
                    _state.update { it.copy(preparing = false) }
                    when (found) {
                        is ShareToPay.Ready -> {
                            // The same gate as Send, after the refetch so a closed request still reads
                            // "Request Closed": with nothing to give, say so before the sheet opens, since
                            // it would have no token to show or swipe with.
                            if (!payer.mayProceed(
                                    onAddMoney = { scope.launch { payer.depositRoute()?.let(openScreen) } },
                                    onDiscoverCurrencies = { openScreen(AppRoute.Token.Discovery) },
                                    noBalanceMessage = R.string.description_noBalanceYetToPayRequest,
                                )
                            ) return@onSuccess
                            // No per-share balance check here: the payer picks the token in the sheet,
                            // and the check runs against that pick when they swipe.
                            ready = found
                            collectionPick.value = null
                            _state.update {
                                it.copy(
                                    open = true,
                                    progress = LoadingSuccessState(),
                                    amount = found.amount,
                                    creator = found.request.creator,
                                    funding = fund && found.request.collection != null,
                                )
                            }
                        }
                        is ShareToPay.NothingToPay -> onNothingToPay()
                        is ShareToPay.Closed -> presentClosed()
                        is ShareToPay.Unavailable -> onUnavailable()
                    }
                }
                .onFailure {
                    _state.update { it.copy(preparing = false) }
                    presentSendFailed()
                }
        }
    }

    /** The swipe. A second swipe after a failure pays under the same intent id. */
    fun confirm() {
        val pending = ready ?: return
        val token = token.value ?: run {
            // No token to pay from means nothing to give; never a silent swipe.
            if (_state.value.progress.isIdle) present(PayBlock.OverBalance)
            return
        }
        val owner = userManager.accountCluster ?: return
        if (!_state.value.progress.isIdle) return
        val funding = _state.value.funding
        // The token or the balance can change while the sheet is up; the modal shows over it.
        payBlock(pending.amount, token, funding)?.let { blocked ->
            present(blocked)
            return
        }

        _state.update { it.copy(progress = LoadingSuccessState(loading = true)) }
        scope.launch {
            payments.pay(pending, token, owner.withTimelockForToken(token), fund = funding)
                .onSuccess {
                    onPaid()
                    holdPaymentSuccess(
                        show = { _state.update { it.copy(progress = LoadingSuccessState(success = true)) } },
                        release = {
                            analytics.track(sentCash(AnalyticsState.SUCCESS, pending, null))
                            dismiss()
                        },
                    )
                }
                .onFailure { cause ->
                    if (cause is CollectionFundingError.Short) {
                        // Conversions can't cover it, so nothing was paid: the same short state as
                        // the check before the swipe. Adding money is a later step (S5).
                        _state.update { it.copy(progress = LoadingSuccessState()) }
                        present(PayBlock.OverBalance)
                    } else {
                        afterFailedPayment(pending, cause)
                    }
                }
        }
    }

    /**
     * The currency pill's pick in an open collection sheet. It replans [plan] and buys nothing:
     * the wallet's selected token and the last paid currency stay as they were.
     */
    fun pickCollectionCurrency(mint: Mint) {
        if (!_state.value.funding) return
        collectionPick.value = mint
    }

    /**
     * The outcome is unknown, so refetch before anything else: a share that landed after all is
     * paid, and a request closed meanwhile is "Request Closed".
     */
    private suspend fun afterFailedPayment(pending: ShareToPay.Ready, cause: Throwable) {
        analytics.track(sentCash(AnalyticsState.FAILURE, pending, cause.analytics))
        _state.update { it.copy(progress = LoadingSuccessState()) }
        val viewer = viewerId ?: return
        when (val found = payments.prepare(pending.request.id, viewer).getOrNull()) {
            is ShareToPay.Closed -> {
                dismiss()
                presentClosed()
            }
            is ShareToPay.NothingToPay -> dismiss()
            is ShareToPay.Unavailable -> {
                dismiss()
                onUnavailable()
            }
            is ShareToPay.Ready -> {
                ready = found
                _state.update { it.copy(amount = found.amount, creator = found.request.creator) }
                presentPaymentError(cause)
            }
            null -> presentPaymentError(cause)
        }
    }

    private fun sentCash(state: AnalyticsState, pending: ShareToPay.Ready, error: String?) =
        TransferEvents.sentCash(state, pending.amount.analytics, error, SendContext.PAYMENT_REQUEST)

    fun dismiss() {
        if (!_state.value.progress.isIdle && !_state.value.progress.success) return
        ready = null
        collectionPick.value = null
        _state.update {
            it.copy(open = false, progress = LoadingSuccessState(), amount = null, creator = null, funding = false)
        }
    }

    /** "Add Money" from a modal: the sheet closes, then the deposit screen opens. */
    fun presentDeposit() {
        dismiss()
        scope.launch { payer.depositRoute()?.let(openScreen) }
    }

    private enum class PayBlock { OverBalance, OverSendLimit }

    /**
     * Why [amount] can't be paid from [token] right now, or null when it can. Presents nothing.
     * With [funding], a short [token] balance is fine when converting other balances covers it.
     */
    private fun payBlock(amount: Fiat, token: Token, funding: Boolean): PayBlock? {
        if (funding) {
            val plan = payments.planCollection(amount, token)
            if (plan != null && !plan.payableWithoutTopUp) return PayBlock.OverBalance
        } else {
            exchange.rateFor(amount.currencyCode)?.let { rate ->
                val balance = tokenCoordinator.balanceForToken(token).convertingTo(rate)
                if (amount.valueGreaterThan(balance)) return PayBlock.OverBalance
            }
        }
        val limit = transactionController.limits.value?.sendLimitFor(amount.currencyCode)
        if (limit != null && amount.toDouble() > limit.nextTransaction) return PayBlock.OverSendLimit
        return null
    }

    private fun present(block: PayBlock) = when (block) {
        PayBlock.OverBalance -> payer.presentInsufficientBalance(
            onAddMoney = ::presentDeposit,
            // A share is a fixed amount, so the way out is another currency, not a smaller amount.
            message = R.string.description_insufficientBalanceToPayRequest,
        )
        PayBlock.OverSendLimit -> BottomBarManager.showAlert(
            resources.getString(R.string.error_title_sendLimitReached),
            resources.getString(R.string.error_description_sendLimitReached),
        )
    }

    private fun presentClosed() {
        BottomBarManager.showAlert(
            resources.getString(R.string.error_title_requestClosed),
            resources.getString(R.string.error_description_requestClosed),
        )
    }

    private fun presentSendFailed() {
        BottomBarManager.showError(
            title = resources.getString(R.string.error_title_cashFailedToSend),
            message = resources.getString(R.string.error_description_cashFailedToSend),
        )
    }

    private fun presentPaymentError(cause: Throwable) {
        val (title, message) = when (cause) {
            is CollectionFundingError.ConversionPending ->
                R.string.error_title_collectionConversionPending to R.string.error_description_collectionConversionPending
            is ComputeVerifiedFiatError.AmountBelowMinimum ->
                R.string.error_title_amountTooSmall to R.string.error_description_amountTooSmall
            is ComputeVerifiedFiatError ->
                R.string.error_title_staleRates to R.string.error_description_staleRates
            else ->
                R.string.error_title_cashFailedToSend to R.string.error_description_cashFailedToSend
        }
        BottomBarManager.showError(title = resources.getString(title), message = resources.getString(message))
    }
}
