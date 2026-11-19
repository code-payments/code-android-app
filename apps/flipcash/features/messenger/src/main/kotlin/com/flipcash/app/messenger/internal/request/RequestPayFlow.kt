package com.flipcash.app.messenger.internal.request

import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
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
import com.flipcash.shared.payments.request.CollectionTopUp
import com.flipcash.shared.payments.request.PaymentRequestPaymentDelegate
import com.flipcash.shared.payments.request.ShareToPay
import com.flipcash.shared.payments.request.TopUpBlock
import com.flipcash.shared.payments.request.TopUpResult
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Fiat.FormattingRule
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    /** Adds the Dollars a collection payment is short by, through Google Pay. */
    private val topUp: CollectionTopUp,
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
        /**
         * "Finishing Payment" in place of the sheet: a top-up has been charged and its Dollars are
         * landing, then the payment runs (frames 1.3, 4.4, 7.5, 8.4).
         */
        val finishing: Boolean = false,
        /**
         * The Request screen's own button is the top-up, with no sheet (sections 1 and 2): no
         * balance funds the collection, or a kept top-up is resuming. The button spins while
         * Google Pay is up, and [finishing] covers the screen (frame 1.3).
         */
        val direct: Boolean = false,
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

    private fun collectionToken(pick: Flow<Mint?> = collectionPick): Flow<Token> =
        combine(pick, payments.lastPaidCollectionCurrency(), tokenCoordinator.tokens) { pick, lastPaid, tokens ->
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
        fundingUi(plan, amount)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The Request screen's Pay for a collection of [amount], before anything opens: non-null when no
     * balance funds it (sections 1 and 2), so the button is the top-up and [start] with `direct`
     * opens Google Pay without the sheet. Null when a balance funds part of it and Pay opens the
     * sheet. Planned as [start] plans it, in the collection's default currency.
     */
    fun directPay(amount: Flow<Fiat?>): Flow<CollectionDirectPayUi?> = combine(
        amount,
        collectionToken(pick = flowOf(null)),
        tokenCoordinator.tokenBalances.map { }.onStart { emit(Unit) },
    ) { amount, token, _ ->
        if (amount == null) return@combine null
        val plan = payments.planCollection(amount, token)?.takeIf { it.fundedByTopUpAlone } ?: return@combine null
        val short = fundingUi(plan, amount).short ?: return@combine null
        if (short.topUpIsAmount) CollectionDirectPayUi() else CollectionDirectPayUi(short.addButton, short.footnote)
    }

    private fun fundingUi(plan: CollectionFundingPlan, amount: Fiat): CollectionFundingUi {
        val known = tokenCoordinator.cachedTokens().associateBy { it.address.base58() } +
            (Token.usdf.address.base58() to Token.usdf)
        return collectionFundingUi(
            plan = plan,
            amount = amount,
            tokenOf = known::get,
            nameOf = { it.brandedName(resources) },
            topUpSourceName = resources.getString(R.string.label_topUpSourceGooglePay),
            topUpFootnote = resources.getString(
                R.string.label_collectionTopUpMinimum,
                resources.getString(R.string.label_topUpSourceGooglePay),
                payments.collectionTopUpMinimum.formatted(FormattingRule.Truncated),
            ),
            strings = ResourceCollectionFundingStrings(resources),
        )
    }

    private var ready: ShareToPay.Ready? = null

    private val viewerId: ID? get() = userManager.accountId

    /** Everything the sheet asks of this flow, in one place. */
    val actions = SwipePayActions(onConfirm = ::confirm, onDismiss = ::dismiss)

    /** The short state's "Add $5.00 and Pay $11.00" (frames 4.2, 7.2, 8.2). */
    val onAddAndPay: () -> Unit = { addAndPay(checkGates = true) }

    /**
     * "Send <share>" / the deck's Pay: refetch for the intent id, then show the sheet if there is
     * still a share to pay. [fund] is the Group Money flag: with it, a collection is paid in its
     * own currency and funded by conversion when short (see [State.funding]). [direct] is the
     * Request screen's button, which is the top-up itself when no balance funds the collection
     * (see [State.direct]); the chat's deck always opens the sheet.
     */
    fun start(requestId: ID, fund: Boolean = false, direct: Boolean = false) {
        val viewer = viewerId ?: return
        val now = _state.value
        // One payment at a time: a refetch, the sheet, or the Request screen's top-up.
        if (now.preparing || now.open || (now.direct && !now.progress.isIdle)) return
        _state.update { it.copy(requestId = requestId, preparing = true) }
        scope.launch {
            payments.prepare(requestId, viewer)
                .onSuccess { found ->
                    when (found) {
                        is ShareToPay.Ready -> begin(found, fund, direct)
                        is ShareToPay.NothingToPay -> {
                            _state.update { it.copy(preparing = false) }
                            onNothingToPay()
                        }
                        is ShareToPay.Closed -> {
                            _state.update { it.copy(preparing = false) }
                            presentClosed()
                        }
                        is ShareToPay.Unavailable -> {
                            _state.update { it.copy(preparing = false) }
                            onUnavailable()
                        }
                    }
                }
                .onFailure {
                    _state.update { it.copy(preparing = false) }
                    presentSendFailed()
                }
        }
    }

    /** The refetch found a share to pay: open the sheet, or with [direct], maybe top up without it. */
    private suspend fun begin(found: ShareToPay.Ready, fund: Boolean, direct: Boolean) {
        val funding = fund && found.request.collection != null
        if (!funding) {
            _state.update { it.copy(preparing = false) }
            // The same gate as Send, after the refetch so a closed request still reads "Request
            // Closed": with nothing to give, say so before the sheet opens, since it would have no
            // token to show or swipe with.
            if (!payer.mayProceed(
                    onAddMoney = { scope.launch { payer.depositRoute()?.let(openScreen) } },
                    onDiscoverCurrencies = { openScreen(AppRoute.Token.Discovery) },
                    noBalanceMessage = R.string.description_noBalanceYetToPayRequest,
                )
            ) return
            // No per-share balance check here: the payer picks the token in the sheet, and the
            // check runs against that pick when they swipe.
            ready = found
            collectionPick.value = null
            _state.update {
                it.copy(
                    open = true,
                    progress = LoadingSuccessState(),
                    amount = found.amount,
                    creator = found.request.creator,
                    funding = false,
                    direct = false,
                )
            }
            return
        }
        // A collection being funded always has a token, Dollars, and an empty wallet reaches its
        // top-up. Its default currency is read here: [token] can still hold the wallet's selected
        // one until it sees [State.funding].
        val token = collectionToken(pick = flowOf(null)).first()
        // A top-up kept from before a relaunch or a failed attempt is waited on and then paid;
        // nothing new is ordered.
        val kept = topUp.pending(found.request.id)
        val withoutSheet = direct &&
            (kept || payments.planCollection(found.amount, token)?.fundedByTopUpAlone == true)
        ready = found
        collectionPick.value = null
        _state.update {
            it.copy(
                preparing = false,
                open = !withoutSheet,
                direct = withoutSheet,
                progress = LoadingSuccessState(),
                amount = found.amount,
                creator = found.request.creator,
                funding = true,
            )
        }
        when {
            kept -> resumeTopUp(found, token)
            withoutSheet -> addAndPay(checkGates = true, token = token)
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
            if (funding) {
                // One payment per request at a time, so it can't run beside a top-up's.
                topUp.exclusive(pending.request.id) { pay(pending, token, owner) }
            } else {
                pay(pending, token, owner)
            }
        }
    }

    private suspend fun pay(pending: ShareToPay.Ready, token: Token, owner: AccountCluster) {
        _state.update { it.copy(progress = LoadingSuccessState(loading = true)) }
        payments.pay(pending, token, owner.withTimelockForToken(token), fund = _state.value.funding)
            .onSuccess {
                onPaid()
                // Outside the caller's lock, so the hold doesn't keep another screen's Pay waiting.
                scope.launch {
                    holdPaymentSuccess(
                        show = {
                            _state.update { it.copy(progress = LoadingSuccessState(success = true), finishing = false) }
                        },
                        release = {
                            analytics.track(sentCash(AnalyticsState.SUCCESS, pending, null))
                            dismiss()
                        },
                    )
                }
            }
            .onFailure { cause ->
                if (cause is CollectionFundingError.Short) {
                    // Conversions can't cover it, so nothing was paid. Fresh balances replan the
                    // sheet into the short state, which asks before adding anything.
                    showShortState()
                    tokenCoordinator.update()
                } else {
                    afterFailedPayment(pending, cause)
                }
            }
    }

    /**
     * Adds the Dollars the open collection sheet is short by, then pays once they land, with no
     * second confirmation (group-collections spec, "Adding money and recovery"). Inside the
     * request's lock, a top-up already kept is waited on and nothing new is ordered; otherwise the
     * share is refetched (stopping if it's paid or closed) and replanned, so a second tap after the
     * Dollars landed pays instead of ordering. The send limit is checked before any order, so no
     * Dollars are bought that couldn't then be sent. [checkGates] false is "Continue Anyway".
     * [token] is the currency to pay in, when the caller has it; otherwise the sheet's.
     */
    private fun addAndPay(checkGates: Boolean, token: Token? = null) {
        val pending = ready ?: return
        val owner = userManager.accountCluster ?: return
        if (!_state.value.funding || !_state.value.progress.isIdle) return
        if (overSendLimit(pending.amount)) {
            present(PayBlock.OverSendLimit)
            return
        }
        _state.update { it.copy(progress = LoadingSuccessState(loading = true)) }
        scope.launch {
            val token = token ?: this@RequestPayFlow.token.filterNotNull().first()
            topUp.exclusive(pending.request.id) {
                val kept = topUp.resume(pending.request.id, owner)
                if (kept != null) {
                    afterTopUp(pending, token, owner, kept)
                    return@exclusive
                }
                val found = refetch(pending) ?: return@exclusive
                val plan = payments.planCollection(found.amount, token)
                if (plan == null || plan.payableWithoutTopUp) {
                    pay(found, token, owner)
                    return@exclusive
                }
                val cents = payments.collectionTopUpCents(plan, found.amount) ?: run {
                    stopTopUp()
                    presentTopUpFailed()
                    return@exclusive
                }
                val result = topUp.topUp(
                    requestId = found.request.id,
                    usdCents = cents,
                    owner = owner,
                    checkGates = checkGates,
                    onCharged = { _state.update { it.copy(finishing = true) } },
                )
                afterTopUp(found, token, owner, result)
            }
        }
    }

    /** Pay opened on a request with a top-up kept: "Finishing Payment" until it lands and pays. */
    private fun resumeTopUp(pending: ShareToPay.Ready, token: Token) {
        val owner = userManager.accountCluster ?: return
        _state.update { it.copy(progress = LoadingSuccessState(loading = true), finishing = true) }
        scope.launch {
            topUp.exclusive(pending.request.id) {
                val kept = topUp.resume(pending.request.id, owner)
                if (kept != null) {
                    afterTopUp(pending, token, owner, kept)
                } else {
                    // Another screen's top-up settled while this one waited; it may have paid.
                    refetch(pending)?.let { stopTopUp() }
                }
            }
        }
    }

    /**
     * Once the Dollars land, refetch (another member may have paid meanwhile, or the request
     * closed) and replan from the balances as observed: pay if they cover it, else draw the short
     * state again so the user confirms any further top-up. The Dollars stay either way.
     */
    private suspend fun afterTopUp(pending: ShareToPay.Ready, token: Token, owner: AccountCluster, result: TopUpResult) {
        when (result) {
            TopUpResult.Landed -> {
                val found = refetch(pending) ?: return
                val plan = payments.planCollection(found.amount, token)
                if (plan == null || plan.payableWithoutTopUp) {
                    _state.update { it.copy(finishing = true) }
                    pay(found, token, owner)
                } else {
                    showShortState()
                }
            }
            TopUpResult.NotCharged -> stopTopUp()
            TopUpResult.Failed -> {
                stopTopUp()
                presentTopUpFailed()
            }
            TopUpResult.StillPending -> {
                stopTopUp()
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_collectionTopUpPending),
                    message = resources.getString(R.string.error_description_collectionTopUpPending),
                )
            }
            is TopUpResult.Blocked -> {
                stopTopUp()
                present(result.block)
            }
        }
    }

    /**
     * GetPaymentIntentId again before a top-up and before paying after one: null, with the outcome
     * shown, when there is no longer a share to pay or the refetch failed.
     */
    private suspend fun refetch(pending: ShareToPay.Ready): ShareToPay.Ready? {
        val viewer = viewerId ?: return null
        val found = payments.prepare(pending.request.id, viewer).getOrElse {
            stopTopUp()
            presentSendFailed()
            return null
        }
        when (found) {
            is ShareToPay.Ready -> {
                ready = found
                _state.update { it.copy(amount = found.amount, creator = found.request.creator) }
                return found
            }
            is ShareToPay.NothingToPay -> {
                close()
                onNothingToPay()
            }
            is ShareToPay.Closed -> {
                close()
                presentClosed()
            }
            is ShareToPay.Unavailable -> {
                close()
                onUnavailable()
            }
        }
        return null
    }

    /**
     * Back to the sheet as the balances now plan it: the short state, or the swipe. With no sheet
     * ([State.direct]), back to the Request screen's button, which plans again when tapped.
     */
    private fun stopTopUp() {
        _state.update { it.copy(progress = LoadingSuccessState(), finishing = false) }
    }

    /**
     * Still short after money landed: the short state again, so the user confirms any further
     * top-up (spec, "Adding money and recovery" item 3). The Request screen's button has no short
     * state of its own, so a payment it started opens the sheet for it.
     */
    private fun showShortState() {
        _state.update { it.copy(progress = LoadingSuccessState(), finishing = false, open = true, direct = false) }
    }

    /** Closes the sheet mid top-up, where [dismiss] would wait for the spinner. */
    private fun close() {
        stopTopUp()
        dismiss()
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
        _state.update { it.copy(progress = LoadingSuccessState(), finishing = false) }
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
            it.copy(
                open = false,
                progress = LoadingSuccessState(),
                amount = null,
                creator = null,
                funding = false,
                finishing = false,
                direct = false,
            )
        }
    }

    /** "Add Money" from a modal: the sheet closes, then the deposit screen opens. */
    fun presentDeposit() {
        dismiss()
        scope.launch { payer.depositRoute()?.let(openScreen) }
    }

    private enum class PayBlock {
        OverBalance,
        OverSendLimit,

        /** A collection the balances can't cover: the sheet draws the short state, not a modal. */
        Short,
    }

    /**
     * Why [amount] can't be paid from [token] right now, or null when it can. Presents nothing.
     * With [funding], a short [token] balance is fine when converting other balances covers it.
     */
    private fun payBlock(amount: Fiat, token: Token, funding: Boolean): PayBlock? {
        if (funding) {
            val plan = payments.planCollection(amount, token)
            if (plan != null && !plan.payableWithoutTopUp) return PayBlock.Short
        } else {
            exchange.rateFor(amount.currencyCode)?.let { rate ->
                val balance = tokenCoordinator.balanceForToken(token).convertingTo(rate)
                if (amount.valueGreaterThan(balance)) return PayBlock.OverBalance
            }
        }
        if (overSendLimit(amount)) return PayBlock.OverSendLimit
        return null
    }

    private fun overSendLimit(amount: Fiat): Boolean {
        val limit = transactionController.limits.value?.sendLimitFor(amount.currencyCode) ?: return false
        return amount.toDouble() > limit.nextTransaction
    }

    private fun present(block: PayBlock) = when (block) {
        PayBlock.Short -> Unit
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

    /** Why a top-up couldn't start, as the Add Cash screen says it (SwapViewModel's purchase gates). */
    private fun present(block: TopUpBlock) {
        when (block) {
            TopUpBlock.GooglePayNotSupported -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampGooglePayNotSupported),
                message = resources.getString(R.string.error_description_onrampGooglePayNotSupported),
            )
            TopUpBlock.GooglePayNoPaymentMethod -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampGooglePayNotReady),
                message = resources.getString(R.string.error_description_onrampGooglePayNotReady),
            )
            is TopUpBlock.WebViewWarning -> BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampNonStableWebView),
                message = resources.getString(R.string.error_description_onrampNonStableWebView, block.channel),
                actions = listOf(
                    BottomBarAction(
                        text = resources.getString(R.string.action_cancel),
                        style = BottomBarManager.BottomBarButtonStyle.Filled,
                    ) {},
                    BottomBarAction(
                        text = resources.getString(R.string.action_continueAnyway),
                        style = BottomBarManager.BottomBarButtonStyle.Text,
                    ) { addAndPay(checkGates = false) },
                ),
            )
            // Coinbase needs a verified phone or email first. The sheet closes for it; Pay again
            // after verifying starts the top-up over.
            is TopUpBlock.Verification -> {
                close()
                openScreen(
                    AppRoute.Verification(
                        origin = AppRoute.Token.Swap(SwapPurpose.Buy(Mint.usdf, FundingSource.Coinbase)),
                        includePhone = block.phone,
                        includeEmail = block.email,
                    )
                )
            }
            is TopUpBlock.OrderFailed -> presentTopUpFailed()
        }
    }

    private fun presentTopUpFailed() {
        BottomBarManager.showError(
            title = resources.getString(R.string.error_title_buySellFailed),
            message = resources.getString(R.string.error_description_buySellFailed),
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

/**
 * The Request screen's Pay for a collection no balance funds. [button] and [footnote] are null when
 * the top-up is the amount and the button reads "Pay $10.00" as it always has (frame 1.1);
 * otherwise "Add $5.00 and Pay $2.00" over the minimum Google Pay takes (2.1).
 */
internal data class CollectionDirectPayUi(
    val button: String? = null,
    val footnote: String? = null,
)
