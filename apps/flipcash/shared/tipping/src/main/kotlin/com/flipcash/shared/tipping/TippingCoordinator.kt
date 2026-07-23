package com.flipcash.shared.tipping

import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.tipping.TipAmount
import com.flipcash.app.core.tipping.TipEvent
import com.flipcash.app.core.tipping.TipSelectionHolder
import com.flipcash.app.core.tipping.TipSelectionState
import com.flipcash.app.currency.PreferredCurrencyController
import com.flipcash.app.payments.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.controllers.ResolverController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.buildDmPaymentMetadata
import com.flipcash.services.models.buildTipDmPaymentMetadata
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.core.UserId
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.core.errors.ComputeVerifiedFiatError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orchestrates tipping flows.
 *
 * Resolves the [UserProfile] needed to render a tip card and assembles the scannable
 * [Scannable.TipCard] itself — mirroring how the cash screen builds a [Scannable.Payable]
 * bill to present. The tip code encodes the recipient's [ID] (a 16-byte user id).
 */
@Singleton
class TippingCoordinator @Inject constructor(
    userFlags: UserFlagsCoordinator,
    private val profileController: ProfileController,
    private val userManager: UserManager,
    private val resolverController: ResolverController,
    private val exchange: Exchange,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionController: TransactionController,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val resources: ResourceHelper,
    private val chatCoordinator: ChatCoordinator,
    private val purchaseMethodController: PurchaseMethodController,

) : TipSelectionHolder {
    /** The signed-in user's id ([UserManager.accountId]), or null if unavailable. */
    val currentUserId: ID?
        get() = userManager.accountId

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _amount = MutableStateFlow<TipAmount?>(null)
    private val _sendState = MutableStateFlow(LoadingSuccessState())

    private val _userId = MutableStateFlow<ID?>(null)

    // Whether the viewer can afford at least the minimum tip, evaluated when a tip
    // card is resolved. Surfaced through [selection] so the scanner can hide the tip
    // modal and prompt to add money without re-checking balance itself.
    private val _canTip = MutableStateFlow(false)

    private val _events = MutableSharedFlow<TipEvent>()
    override val events: Flow<TipEvent> = _events

    /**
     * The token to tip with — the app-global selected token from [TokenCoordinator], resolved from
     * the persisted selected mint. Emits null until the token metadata is known.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedToken: Flow<Token?> = tokenCoordinator.observeSelectedTokenMint()
        .flatMapLatest { mint ->
            tokenCoordinator.tokens.map { tokens -> tokens.find { it.address == mint } }
        }

    /**
     * The user's suggested tip amounts, derived from the server-provided per-region
     * [com.flipcash.services.models.TipPresets] (via [UserFlagsCoordinator]) and the user's current
     * preferred currency. Selects the presets whose region matches that currency (region is an ISO
     * 4217 currency code), falls back to the USD presets, and expresses each tier — low / medium /
     * high — as a [Fiat] in the matched region's currency. When the server provides no presets at
     * all, falls back to the built-in [DEFAULT_USD_PRESETS], localized to the preferred currency.
     */
    private val tipPresets: Flow<List<Fiat>> = combine(
        userFlags.resolvedFlags,
        // Drive off the preferred rate (a StateFlow that always emits its current value) rather than
        // observePreferredCurrency() — which can be an empty flow, and combine()'d with the hot
        // resolvedFlags that never completes would hang firstOrNull() forever (blocking card resolve).
        // Re-derives when the region changes so the tip modal's presets follow it.
        exchange.observePreferredRate(),
    ) { flags, rate ->
        val presets = flags.tipPresets.effectiveValue
        val preferred = rate.currency
        val (currency, matched) =
            presets.firstOrNull { it.region.equals(preferred.name, ignoreCase = true) }
                ?.let { preferred to it }
                ?: presets.firstOrNull {
                    it.region.equals(
                        CurrencyCode.USD.name,
                        ignoreCase = true
                    )
                }
                    ?.let { CurrencyCode.USD to it }
                ?: return@combine defaultPresets(preferred)

        listOf(matched.low, matched.medium, matched.high)
            .map { amount -> Fiat(fiat = amount, currencyCode = currency) }
    }

    /** The combined tip selection (amount chosen in the modal + app-global token + send state). */
    override val selection: StateFlow<TipSelectionState> =
        combine(
            combine(_amount, selectedToken, _sendState, _userId, _canTip) { amount, token, sendState, userId, canTip ->
                TipSelectionState(
                    amount = amount,
                    token = token,
                    sendState = sendState,
                    userId = userId,
                    canTip = canTip,
                )
            },
            tipPresets,
        ) { state, presets -> state.copy(presets = presets) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), TipSelectionState())

    /**
     * The largest tippable amount, in the user's preferred currency: the smaller of the
     * per-transaction send limit and the selected token's balance — mirroring the give/cash/send
     * amount entries. Null until limits/balance/rate are known. The amount-entry sheet surfaces it
     * as an "enter up to" hint and blocks amounts above it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val maxTipAmount: StateFlow<Fiat?> =
        combine(
            transactionController.limits,
            tokenCoordinator.observeSelectedTokenMint()
                .flatMapLatest { mint -> tokenCoordinator.balanceForToken(mint) },
            exchange.observePreferredRate(),
        ) { limits, balance, rate ->
            val balanceInLocal = balance.convertingTo(rate)
            val sendLimit = limits?.sendLimitFor(rate.currency) ?: SendLimit.Zero
            Fiat(min(sendLimit.nextTransaction, balanceInLocal.toDouble()), rate.currency)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The smallest tippable amount — the lowest preset tier in the user's preferred currency. The
     * amount entry surfaces it as a "minimum tip" hint and blocks custom amounts below it. Null until
     * presets resolve.
     */
    val minTipAmount: StateFlow<Fiat?> =
        tipPresets.map { presets -> presets.minOrNull() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Whether [amount] exceeds the per-transaction send limit for its currency — the tip amount
     * entry gates on this before committing. Balance is enforced separately at send time
     * ([confirmTip]); false when no limit is known for the currency.
     */
    fun exceedsSendLimit(amount: Fiat): Boolean {
        val limit = transactionController.limits.value?.sendLimitFor(amount.currencyCode) ?: return false
        return amount.toDouble() > limit.nextTransaction
    }

    override fun selectAmount(amount: TipAmount?) {
        _amount.value = amount
    }

    override fun confirmTip() {
        val amount = _amount.value ?: return
        val userForTip = _userId.value ?: return
        if (!_sendState.value.isIdle) return
        val owner = userManager.accountCluster ?: return
        val rate = exchange.preferredRate

        scope.launch {
            setSendState(LoadingSuccessState(loading = true))
            val token = selectedToken.firstOrNull() ?: return@launch

            // Fast-fail before the loading state if the tip exceeds the token balance:
            // prompt to add money (or enter a smaller amount) instead of attempting a send.
            val balanceInLocal = tokenCoordinator.balanceForToken(token).convertingTo(rate)
            if (amount.value.valueGreaterThan(balanceInLocal)) {
                setSendState(LoadingSuccessState())
                promptInsufficientBalance()
                return@launch
            }


            val source = owner.withTimelockForToken(token)

            val balance = tokenCoordinator.balanceForToken(token)
            val verifiedFiat = verifiedFiatCalculator.compute(
                amount = amount.value,
                token = token,
                balance = balance,
                rate = rate,
            ).getOrElse { error ->
                setSendState(LoadingSuccessState())
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

            val canonicalChatId = chatCoordinator.generateChatId(userId = userForTip).getOrNull()

            val appMetadataBytes = buildTipDmPaymentMetadata(
                chatId = canonicalChatId,
            )

            resolverController.resolve(userId = userForTip)
                .fold(
                    onSuccess = { destination ->
                        transactionController.directTransfer(
                            amount = verifiedFiat,
                            token = token,
                            source = source,
                            destinationOwner = destination,
                            appMetadata = appMetadataBytes,
                        )
                    },
                    onFailure = {
                        Result.failure(it)
                    }
                ).fold(
                    onSuccess = {
                        tokenCoordinator.subtract(token, verifiedFiat.localFiat)
                        Result.success(verifiedFiat)
                    },
                    onFailure = { Result.failure(it) }
                ).onSuccess { amount ->
                    setSendState(LoadingSuccessState(success = true))
                    if (canonicalChatId != null) {
                        chatCoordinator.loadMessages(canonicalChatId)
                    } else {
                        // New conversation — server just created the DM chat.
                        // Sync the feed so it appears in the contact list.
                        chatCoordinator.refreshFeed()
                    }
                    delay(400.milliseconds)
                    setSendState(LoadingSuccessState())
//                    analytics.transfer(
//                        event = Analytics.Transfer.SentCash,
//                        amount = verifiedFiat.localFiat,
//                        successful = true,
//                    )

                    // Hand off to the tipped user's chat via the tips flow, so backing out of the
                    // chat lands on the tips list.
                    canonicalChatId?.let {
                        _events.emit(TipEvent.LaunchChat(ChatIdentifier.ByChatId(it)))
                    }

                }.onFailure { cause ->
                    setSendState(LoadingSuccessState())
//                    analytics.transfer(
//                        event = Analytics.Transfer.SentCash,
//                        amount = verifiedFiat.localFiat,
//                        error = cause,
//                    )
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_cashFailedToSend),
                        message = resources.getString(R.string.error_description_cashFailedToSend),
                    )
                }
        }
    }

    /**
     * Shows the insufficient-balance prompt with an "Add Money" action, mirroring the
     * give/currency-creator flows. Choosing "Add Money" resolves a deposit route via
     * [PurchaseMethodController] and emits it as a [TipEvent.OpenRoute] for the tip UI to open.
     * Shared by the send path ([confirmTip]) and the amount-entry over-balance gate.
     */
    fun promptInsufficientBalance() {
        BottomBarManager.showInfo(
            title = resources.getString(R.string.title_insufficientBalance),
            message = resources.getString(R.string.description_insufficientBalanceToUse),
            actions = listOf(
                BottomBarAction(text = resources.getString(R.string.action_addMoney)) {
                    scope.launch {
                        purchaseMethodController.presentDepositOptions(popToRoot = true)
                            ?.let { _events.tryEmit(TipEvent.OpenRoute(it)) }
                    }
                },
            ),
            showCancel = true,
        )
    }

    /**
     * Resolves the [UserProfile] for [userId] — e.g. a tip counterparty identified by a
     * scanned code — so a tip card can be rendered for them. Delegates to the server-backed
     * profile fetch and propagates its [Result].
     */
    suspend fun resolveProfile(userId: ID): Result<UserProfile> =
        profileController.getProfileForUser(userId)

    /**
     * The current user's profile, used to build their own tip card. Prefers the cached
     * [UserManager.profile] and falls back to a server refresh when it isn't available yet.
     */
    suspend fun currentUserProfile(): Result<UserProfile> =
        userManager.profile?.let { Result.success(it) }
            ?: profileController.updateUserProfile()

    /**
     * Builds the current user's own scannable tip card — their [currentUserId] encoded into the
     * tip code, plus their profile for rendering — the way the cash screen builds a bill to
     * present. Fails if there's no signed-in user or their profile can't be resolved.
     */
    suspend fun resolveTipCard(): Result<Scannable.TipCard> {
        val userId = currentUserId
            ?: return Result.failure(IllegalStateException("No signed-in user to build a tip card for"))
        return currentUserProfile().map { tipCard(userId, it) }
    }

    /**
     * Resolves [userId]'s profile and builds their scannable tip card — for generating a card
     * for another user (e.g. a scanned counterparty).
     */
    suspend fun resolveTipCard(userId: ID): Result<Scannable.TipCard> =
        resolveProfile(userId)
            .onSuccess {
                // Dual gating, like the send / currency-creator flows: the presentation gate only
                // asks "is there any giveable balance?" (no amount threshold, so it stays currency-
                // agnostic). The minimum-tip and per-amount affordability are enforced downstream —
                // the amount entry's below-min / over-balance gates and confirmTip.
                _canTip.value = tokenCoordinator.hasGiveableBalance()
                _userId.value = userId
            }
            .map { tipCard(userId, it) }

    /** Updates the tip submission's processing state; used by the send path (see [confirmTip]). */
    private fun setSendState(state: LoadingSuccessState) {
        _sendState.value = state
    }

    /**
     * The built-in fallback tip presets ([DEFAULT_USD_PRESETS], in USD), localized to [preferred]
     * via the current exchange rate when the user isn't on USD. Leaves the amounts in USD if no
     * rate is available for the preferred currency.
     */
    private fun defaultPresets(preferred: CurrencyCode): List<Fiat> {
        val usd = DEFAULT_USD_PRESETS.map { Fiat(fiat = it, currencyCode = CurrencyCode.USD) }
        if (preferred == CurrencyCode.USD) return usd

        val rate = exchange.rateFor(preferred) ?: return usd
        return usd.map { it.convertingTo(rate).rounded() }
    }

    /**
     * Assembles the scannable [Scannable.TipCard]: the tip [OpenCodePayload] encoding [userId]
     * as the scannable code data, plus [profile] for rendering. Tip presets are surfaced reactively
     * through [selection] (so they follow the region), not baked into the card.
     */
    private fun tipCard(userId: ID, profile: UserProfile): Scannable.TipCard {
        val payload = OpenCodePayload(kind = PayloadKind.Tip, value = UserId(userId))
        return Scannable.TipCard(
            data = payload.codeData.toList(),
            user = profile,
        )
    }

    companion object {
        /** Fallback tip amounts (USD) used when the server provides no presets. */
        private val DEFAULT_USD_PRESETS = listOf(5.0, 10.0, 20.0)
    }
}
