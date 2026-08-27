package com.flipcash.app.tokens.ui

import androidx.lifecycle.viewModelScope
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.Button
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.extensions.to
import com.flipcash.app.core.onramp.ui.buildPhantomButtonLabel
import com.flipcash.app.core.tokens.FundingSource
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.onramp.CoinbaseOnRampController
import com.flipcash.app.onramp.CoinbaseOnRampState
import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.onramp.OnRampAuthError
import com.flipcash.app.onramp.OrderDeliveryResult
import com.flipcash.app.onramp.PhantomSwapResult
import com.flipcash.app.onramp.PhantomWalletController
import com.flipcash.app.onramp.PurchaseGate
import com.flipcash.app.onramp.isAlert
import com.flipcash.app.onramp.isNetworkCause
import com.flipcash.app.onramp.messaging
import com.flipcash.app.funding.PurchaseMethod
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.funding.PurchaseMethodMetadata
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.UsdcDepositSweep
import com.flipcash.app.tokens.entryAffordableAfterFee
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.tokens.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.core.errors.SwapError
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HOUSE_SELL_FEE_BPS
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.SendLimit
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.financial.grossingUpLaunchpadSellFee
import com.getcode.opencode.model.financial.launchpadSellFee
import com.getcode.opencode.model.financial.max
import com.getcode.opencode.model.financial.min
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.sellFeeBpsOrHouseRate
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.transactions.SwapState
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.orEmpty

data class AmountEntryState(
    val limits: Limits? = null,
    val maxToAdd: Pair<Double, CurrencyCode>? = null,
    val selectedAmount: VerifiedFiat = VerifiedFiat(LocalFiat.Zero, null),
)

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val userManager: UserManager,
    private val accountController: AccountController,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    transactionController: TransactionOperations,
    private val resources: ResourceHelper,
    private val tokenCoordinator: TokenCoordinator,
    feedCoordinator: ActivityFeedCoordinator,
    private val analytics: FlipcashAnalyticsService,
    private val purchaseMethodController: PurchaseMethodController,
    private val coinbaseOnRampController: CoinbaseOnRampController,
    private val phantomWalletController: PhantomWalletController,
    private val userFlags: UserFlagsCoordinator,
    private val usdcDepositSweep: UsdcDepositSweep,
    dispatchers: DispatcherProvider,
) : BaseViewModel<SwapViewModel.State, SwapViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    private val styleFlow: StateFlow<AmountEntryStyle> = stateFlow.map { vmState ->
        val isBuy = vmState.purpose is SwapPurpose.Buy
        val isAddingMoney = vmState.isAddingMoney
        val isAddingMoneyViaPhantom = isAddingMoney && vmState.addingMoneyFrom == FundingSource.Phantom
        val isConverting = vmState.purpose is SwapPurpose.Convert
        // A v2 Get spends a currency the user already holds, so its ceiling reads like Convert's
        // ("$X available") rather than v1's daily-limit sentence.
        val statesBalanceAsCeiling = isConverting || vmState.isGet
        AmountEntryStyle(
            actionLabel = when {
                isAddingMoneyViaPhantom -> {
                    val confirmIn = resources.getString(R.string.label_confirmIn)
                    val phantom = resources.getString(R.string.label_phantom)
                    AmountEntryLabel.Annotated(text = "$confirmIn $phantom") { enabled ->
                        buildPhantomButtonLabel(prefix = confirmIn, isEnabled = enabled)
                    }
                }

                isAddingMoney -> {
                    AmountEntryLabel.Plain(resources.getString(R.string.action_addMoney))
                }

                isBuy -> {
                    AmountEntryLabel.Plain(resources.getString(R.string.action_next))
                }

                else -> {
                    AmountEntryLabel.Plain(resources.getString(R.string.action_next))
                }
            },
            canChangeCurrency = (vmState.purpose as? SwapPurpose.Buy)?.fundingSource != FundingSource.Phantom,
            // Convert's v2 header states the ceiling as a plain "$X available" line that simply
            // turns red once exceeded, rather than swapping in a separate over-limit sentence.
            infoHint = {
                if (statesBalanceAsCeiling) resources.getString(R.string.subtitle_amountAvailable, it)
                else resources.getString(R.string.subtitle_buySellCashHint, it)
            },
            overMaxHint = {
                when {
                    statesBalanceAsCeiling -> resources.getString(R.string.subtitle_amountAvailable, it)
                    vmState.purpose is SwapPurpose.BalanceIncrease ->
                        resources.getString(R.string.subtitle_buyHintLimitExceeded, it)
                    else -> resources.getString(R.string.subtitle_sellHintLimitExceeded, it)
                }
            },
            belowMinHint = { resources.getString(R.string.subtitle_buyHintBelowMinimum, it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AmountEntryStyle(actionLabel = AmountEntryLabel.Plain("")))

    /** The slice of [State] the entry ceiling depends on, so the ceiling recomputes when it moves. */
    private data class MaxAmountInputs(
        val purpose: SwapPurpose?,
        val maxToAdd: Pair<Double, CurrencyCode>?,
        val tokenBalance: Fiat,
        val isAddingMoney: Boolean,
        val fundingMint: Mint?,
        val isGet: Boolean,
    )

    private val maxAmountFlow: StateFlow<Fiat?> = combine(
        stateFlow.map {
            MaxAmountInputs(
                purpose = it.purpose,
                maxToAdd = it.amountEntryState.maxToAdd,
                tokenBalance = it.tokenBalance,
                isAddingMoney = it.isAddingMoney,
                fundingMint = it.fundingMint,
                isGet = it.isGet,
            )
        }.distinctUntilChanged(),
        tokenCoordinator.tokenBalances.distinctUntilChanged(),
        exchange.observePreferredRate(),
    ) { inputs, tokenBalances, rate ->
        when (inputs.purpose) {
            is SwapPurpose.Buy -> {
                val limit = inputs.maxToAdd?.let { Fiat(it.first, it.second) }
                if (!inputs.isAddingMoney) {
                    // tokenBalances are USD-denominated; convert to the user's preferred
                    // currency so the "Enter up to X" hint is localized and the over-max
                    // comparison (which relabels the entered amount with max.currencyCode)
                    // happens in the same currency the user is typing in. `limit` is already
                    // in the selected currency, so both sides of min() now agree.
                    //
                    // A v2 Get picks its payment source *before* the amount, so the ceiling is
                    // that one balance. v1 defers the choice to a later step, so it can only cap
                    // at the largest balance the user holds.
                    val spendable = if (inputs.isGet && inputs.fundingMint != null) {
                        tokenBalances.firstOrNull { it.token.address == inputs.fundingMint }
                            ?.balance ?: Fiat.Zero
                    } else {
                        tokenBalances.maxOf { it.balance }
                    }
                    val maxTokenBalance = spendable.convertingTo(rate)
                    return@combine limit?.let { min(maxTokenBalance, it) } ?: maxTokenBalance
                }

                limit
            }
            is SwapPurpose.Sell -> inputs.tokenBalance
            // Convert spends the *source* currency, so the ceiling is that balance — same as Sell.
            is SwapPurpose.Convert -> inputs.tokenBalance
            null -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val minimumAmountFlow: StateFlow<Fiat?> = stateFlow
        .map { it.minimumBuyAmount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val combinedLoadingState: StateFlow<LoadingSuccessState> = stateFlow
        .map { vmState ->
            LoadingSuccessState(
                loading = vmState.buyProgress.loading || vmState.sellProgress.loading || vmState.processingProgress.loading,
                success = vmState.buyProgress.success || vmState.sellProgress.success || vmState.processingProgress.success,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState())

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = styleFlow,
        loadingState = combinedLoadingState,
        maxAmount = maxAmountFlow,
        minimumAmount = minimumAmountFlow,
    )

    data class State(
        val loading: Boolean = false,
        val purpose: SwapPurpose? = null,
        val tokenWithBalance: TokenWithBalance? = null,
        val reservesWithBalance: TokenWithBalance? = null,
        val swapId: SwapId? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
        val buyProgress: LoadingSuccessState = LoadingSuccessState(),
        val sellProgress: LoadingSuccessState = LoadingSuccessState(),
        val processingProgress: LoadingSuccessState = LoadingSuccessState(),
        val confirmedNetTransferAmount: Fiat? = null,
        val confirmedEnteredAmount: Fiat? = null,
        val confirmedFeeAmount: Fiat? = null,
        val minimumBuyAmount: Fiat? = null,
        val pendingInitialAmount: Fiat? = null,
        val fundingTokenWithBalance: TokenWithBalance? = null,
        // Convert only: the currency the conversion lands in. `tokenWithBalance` is the source.
        val destinationTokenWithBalance: TokenWithBalance? = null,
        /** Whether the account already holds a token account for the target mint. See [isBuyingMore]. */
        val hasTokenAccount: Boolean = false,
    ) {
        val sellFee: Double?
            get() {
                val feeBps = tokenWithBalance?.token?.launchpadMetadata?.sellFeeBps ?: return null
                val fee = feeBps / 100.0 // basis points to whole percent
                return fee
            }

        val tokenName: String
            get() = tokenWithBalance?.displayName.orEmpty()

        val destinationTokenName: String
            get() = destinationTokenWithBalance?.displayName.orEmpty()

        /**
         * Converting *out of* Dollars is the one direction with no launchpad sale to skim the fee
         * from, so the fee is charged on top of the entered amount instead of out of it.
         */
        val isConvertingFromDollars: Boolean
            get() = (purpose as? SwapPurpose.Convert)?.mint == Mint.usdf

        /** Whether a conversion lands in Dollars, which the reserve's own name never has to spell out. */
        val isConvertingToDollars: Boolean
            get() = (purpose as? SwapPurpose.Convert)?.destinationMint == Mint.usdf

        val canTransact: Boolean
            get() = buyProgress.isIdle && sellProgress.isIdle && processingProgress.isIdle

        val tokenBalance: Fiat
            get() = tokenWithBalance?.balance ?: Fiat.Zero

        val reservesBalance: Fiat
            get() = reservesWithBalance?.balance ?: Fiat.Zero

        val enteredAmount: Fiat
            get() = confirmedEnteredAmount ?: Fiat.Zero

        val feeAmount: Fiat
            get() = confirmedFeeAmount ?: Fiat.Zero

        val netTransferAmount: Fiat
            get() = confirmedNetTransferAmount ?: Fiat.Zero

        val isAddingMoney: Boolean
            get() = purpose is SwapPurpose.Buy && purpose.fundingSource != FundingSource.Flexible
        val addingMoneyFrom: FundingSource?
            get() = (purpose as? SwapPurpose.Buy)?.fundingSource

        /**
         * The "Get" flow: a direct buy with the payment source picked inline on the amount screen
         * rather than on a pushed step afterwards. Adding money from an external source
         * (Coinbase/Phantom) keeps its own flow.
         */
        val isGet: Boolean
            get() = purpose is SwapPurpose.Buy && !isAddingMoney

        /**
         * Whether a Get is adding to a position the account already has, which titles its screens
         * "Buy More" rather than "Buy In".
         *
         * Held is the same test the currency-info tile row uses — an existing token account, or a
         * positive balance — so the tile the user tapped and the screen it opens always agree.
         */
        val isBuyingMore: Boolean
            get() = isGet && (hasTokenAccount || tokenBalance.isPositive)

        /** The currency a Get is paid from. Null until the default is seeded or one is picked. */
        val fundingMint: Mint?
            get() = fundingTokenWithBalance?.token?.address

        /**
         * Whether the buy collects an explicit fee on top of the entered amount.
         *
         * Paying with a currency always did — its pool's sell fee is grossed up into the debit. A
         * Get paid from Dollars has no pool to skim, so v2 charges the flat house rate on top; the
         * v1 reserves buy stays free.
         */
        val chargesBuyFee: Boolean
            get() = when (fundingMint) {
                null -> false
                Mint.usdf -> isGet
                else -> purpose is SwapPurpose.Buy
            }
    }

    sealed interface Event {
        data class OnPurposeChanged(val purpose: SwapPurpose) : Event
        data class OnSelectedTokenChanged(val token: TokenWithBalance) : Event
        data class OnTokenAccountKnown(val exists: Boolean) : Event
        data class OnReservesUpdated(val reserves: TokenWithBalance) : Event

        data class OnLimitsChanged(val limits: Limits?) : Event

        // region amount entry events
        data class OnMaxDetermined(val max: Double, val currencyCode: CurrencyCode) : Event

        data class OnCurrencyChanged(val currency: Currency) : Event

        data object OtherWalletSelected: Event
        data object CoinbaseSelected : Event
        data object PhantomSelected : Event
        data object ConfirmPhantomTransaction : Event
        data object OnAmountConfirmed : Event

        data class SelectFundingToken(val amount: Fiat): Event
        data class OnFundingTokenSelected(val mint: Mint): Event
        data class OnFundingTokenResolved(val token: TokenWithBalance): Event

        data object OnBuyConfirmed: Event
        data object OnSellConfirmed : Event

        data class UpdateBuyState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data class UpdateSellState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        data class OnSwapIdChanged(val swapId: SwapId) : Event

        data object StartPhantomCeremony : Event
        data object PhantomConnected : Event
        data class PhantomNavigateToProcessing(val swapId: SwapId? = null) : Event
        data object PhantomCeremonyFailed : Event
        // orderId is the Coinbase order to poll for on-chain delivery before sweeping.
        // Null for Phantom deposits, which are already on-chain and can sweep immediately.
        data class DepositSubmitted(val orderId: String? = null) : Event

        data class CreateAndSendTransactionToWallet(val token: Token, val amount: VerifiedFiat) :
            Event

        data class OnAmountAccepted(
            val amount: VerifiedFiat,
            val netTransferAmount: Fiat,
            val enteredAmount: Fiat = Fiat.Zero,
            val feeAmount: Fiat = Fiat.Zero,
        ) : Event

        data class ProceedWithPurchase(val amount: VerifiedFiat) : Event
        data class ProceedWithSale(val amount: VerifiedFiat) : Event

        data object ShowSellReceipt : Event

        // region v2 Get — the payment source is chosen inline, before the amount is confirmed.
        /** Opens the "Buy with" picker. */
        data object SelectBuyFundingSource : Event
        /** A payment source was picked (or defaulted); the token still needs resolving. */
        data class OnFundingSourceSelected(val mint: Mint) : Event
        /**
         * The picked payment source resolved to a token. Distinct from [OnFundingTokenResolved],
         * which prices the buy and advances to the receipt — this only re-points the entry cap.
         */
        data class OnFundingSourceResolved(val token: TokenWithBalance) : Event
        // endregion

        // region convert
        data object SelectConvertDestination : Event
        data class OnDestinationSelected(val mint: Mint) : Event
        data class OnDestinationTokenResolved(val token: TokenWithBalance) : Event
        data object ShowConvertReceipt : Event
        data object OnConvertConfirmed : Event
        data class ProceedWithConversion(val amount: VerifiedFiat) : Event
        data class OnConvertSubmitted(val token: Token, val swapId: SwapId) : Event
        // endregion

        data class OnPurchaseSubmitted(val token: Token, val swapId: SwapId) : Event
        data class OnSellSubmitted(val token: Token, val swapId: SwapId) : Event

        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
            val error: Boolean = false,
        ) : Event

        data object OnTransactionSuccessful : Event

        data class OnInitialAmountProvided(val amount: Fiat) : Event
        data object OnInitialAmountEntered : Event
        data class OnVerificationNeeded(
            val phone: Boolean,
            val email: Boolean,
            val skipEmailVerification: Boolean = false,
        ) : Event
        data object Exit : Event
        data object PresentDepositOptions : Event
        data class OpenScreen(val screen: AppRoute) : Event
    }

    // "Add Money" funnel events only apply when acquiring USDF via an external
    // funding source; token buys funded from reserves or launchpad purchases are
    // tracked separately via buy()/sell().
    private val addMoneyMethod: Analytics.AddMoneyMethod?
        get() {
            val purpose = stateFlow.value.purpose as? SwapPurpose.Buy ?: return null
            if (purpose.mint != Mint.usdf) return null
            return when (purpose.fundingSource) {
                FundingSource.Coinbase -> Analytics.AddMoneyMethod.Coinbase
                FundingSource.Phantom -> Analytics.AddMoneyMethod.Phantom
                FundingSource.Flexible -> null
            }
        }

    private val enteredAmount: Fiat
        get() {
            val delegateState = amountDelegate.state.value
            return Fiat(
                fiat = delegateState.enteredAmount,
                currencyCode = stateFlow.value.tokenBalance.currencyCode,
            )
        }

    /**
     * Basis points skimmed by a conversion. Converting *from* Dollars has no launchpad sale to take
     * a fee from, so it pays the flat house rate; every other direction pays the source pool's own
     * sell fee (falling back to the house rate when the pool doesn't declare one).
     */
    private val convertFeeBps: Int
        get() {
            val purpose = stateFlow.value.purpose as? SwapPurpose.Convert ?: return 0
            if (purpose.mint == Mint.usdf) return DEFAULT_CONVERT_FEE_BPS
            return stateFlow.value.tokenWithBalance?.token?.launchpadMetadata
                .sellFeeBpsOrHouseRate
        }

    private val feeAmount: Fiat
        get() {
            if (stateFlow.value.purpose is SwapPurpose.Convert) {
                return enteredAmount.launchpadSellFee(convertFeeBps)
            }
            val bps = stateFlow.value.tokenWithBalance?.token
                ?.launchpadMetadata?.sellFeeBps ?: return Fiat.Zero
            return enteredAmount.launchpadSellFee(bps)
        }

    /**
     * What the user actually receives: entered minus the fee, unless the fee rides on top.
     *
     * Always recomputed from the live entry rather than read back from
     * [State.confirmedNetTransferAmount], because this getter is what *produces* that snapshot --
     * [enteredAmount] and [feeAmount] feed the same event and recompute the same way. Deferring to
     * the previous confirmation made a second trip through the entry screen re-confirm the first
     * trip's total: enter $1, go back, enter $0.50, and the receipt paired a $0.50 debit and a
     * $0.005 fee with a $0.99 "You Receive". Readers that need the snapshot after the entry screen
     * is gone use [State.netTransferAmount], which is where the caching belongs.
     */
    private val netTransferAmount: Fiat
        get() = when {
            stateFlow.value.purpose is SwapPurpose.BalanceIncrease -> enteredAmount
            stateFlow.value.isConvertingFromDollars -> enteredAmount
            else -> Fiat(
                fiat = enteredAmount.decimalValue - feeAmount.decimalValue,
                currencyCode = enteredAmount.currencyCode,
            )
        }

    /** What leaves the source balance: entered, plus the fee when it rides on top. */
    private val totalDebitAmount: Fiat
        get() = if (stateFlow.value.isConvertingFromDollars) {
            Fiat(
                fiat = enteredAmount.decimalValue + feeAmount.decimalValue,
                currencyCode = enteredAmount.currencyCode,
            )
        } else {
            enteredAmount
        }

    /**
     * Drops the entry to the most the funding balance can cover once its fee is applied, in place.
     *
     * Entry is capped at the raw balance, so entering the maximum always overruns by exactly the
     * fee — whether it rides on top of the amount (Dollars, which has no pool to skim) or is
     * grossed up out of a launchpad sale. Rather than pricing a number the balance can't fund and
     * asking the user to confirm a correction, the entry itself is set to the true maximum, so the
     * amount screen and everything priced from it agree. Entries with room to spare are untouched.
     *
     * [balance] must be in the same currency the amount is entered in — the localized balance from
     * [State.tokenWithBalance], or a USD wallet balance converted through the preferred rate.
     */
    private fun correctEntryToAffordable(
        balance: Fiat,
        feeBps: Int,
        feeChargedOnTop: Boolean,
    ) {
        val corrected = entryAffordableAfterFee(
            entered = amountDelegate.state.value.enteredAmount,
            balance = balance,
            feeBps = feeBps,
            feeChargedOnTop = feeChargedOnTop,
        ) ?: return

        amountDelegate.setAmount(corrected.decimalValue)
    }

    /**
     * The fee a buy funded by [fundingToken] charges, as bps and whether it rides on top of the
     * entered amount. A v2 Get from Dollars pays the flat house rate on top (no pool to skim);
     * every other currency has its pool's sell fee grossed up into the debit (falling back to the
     * house rate when the pool doesn't declare one, as [convertFeeBps] does); v1's reserves buy is
     * free.
     */
    private fun buyFeeFor(fundingToken: Token): Pair<Int, Boolean> = when {
        fundingToken.address != Mint.usdf ->
            fundingToken.launchpadMetadata.sellFeeBpsOrHouseRate to false
        stateFlow.value.isGet -> DEFAULT_CONVERT_FEE_BPS to true
        else -> 0 to false
    }

    private suspend fun transactionLimit(): Fiat {
        return when (stateFlow.value.purpose) {
            is SwapPurpose.Buy -> {
                val sendLimit = enteredAmount.currencyCode.let {
                    stateFlow.value.amountEntryState.limits?.sendLimitFor(it)
                } ?: SendLimit.Zero
                val maxSendPerDay = sendLimit.maxPerDay.toFiat(enteredAmount.currencyCode)
                if (!stateFlow.value.isAddingMoney) {
                    val held = tokenCoordinator.tokenBalances.firstOrNull().orEmpty()
                    // Mirrors maxAmountFlow: a v2 Get already knows which balance it's spending,
                    // so the limit is that one; v1 can only bound by the largest balance held.
                    val fundingMint = stateFlow.value.fundingMint
                    val spendable = if (stateFlow.value.isGet && fundingMint != null) {
                        held.firstOrNull { it.token.address == fundingMint }?.balance ?: Fiat.Zero
                    } else {
                        held.map { it.balance }.maxOrNull() ?: Fiat.Zero
                    }
                    min(spendable, maxSendPerDay)
                } else {
                    maxSendPerDay
                }
            }
            is SwapPurpose.Sell -> stateFlow.value.tokenBalance
            is SwapPurpose.Convert -> stateFlow.value.tokenBalance
            null -> Fiat.Zero
        }
    }

    val checkBalanceLimit: () -> Boolean = {
        val amount = amountDelegate.state.value.enteredAmount
        val currencyCode = amountDelegate.state.value.currency.code ?: CurrencyCode.USD
        val conversionRate = exchange.rateToUsd(currencyCode) ?: Rate.ignore
        val enteredInUsdf = Fiat(
            fiat = amount,
            currencyCode = currencyCode,
        ).convertingTo(conversionRate)
        val tokenBalance = stateFlow.value.tokenBalance
        val reservesBalance = stateFlow.value.reservesBalance

        when (stateFlow.value.purpose) {
            is SwapPurpose.BalanceIncrease -> {
                val isOverBalance = enteredInUsdf > reservesBalance.rounded()
                if (isOverBalance || conversionRate == Rate.ignore) {
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds)
                    )
                }
                isOverBalance
            }

            is SwapPurpose.BalanceDecrease -> {
                val isOverBalance = enteredInUsdf > tokenBalance.rounded()
                if (isOverBalance || conversionRate == Rate.ignore) {
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds)
                    )
                }
                isOverBalance
            }

            null -> true
        }
    }

    val checkFundingAmount: suspend () -> Boolean = {
        val limit = transactionLimit()
        val isOverLimit = enteredAmount.valueGreaterThan(limit)
        val isAddingMoney = stateFlow.value.isAddingMoney

        if (isOverLimit) {
            if (!isAddingMoney) {
                BottomBarManager.showInfo(
                    title = resources.getString(R.string.title_insufficientBalance),
                    message = resources.getString(R.string.description_insufficientBalanceToUse),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_addMoreMoney)
                        ) {
                            dispatchEvent(Event.PresentDepositOptions)
                        }
                    ),
                    showCancel = true,
                )
            } else {
                BottomBarManager.showAlert(
                    resources.getString(R.string.error_title_insufficientFunds),
                    resources.getString(R.string.error_description_insufficientFunds)
                )
            }
        }
        isOverLimit
    }

    init {
        // Get seeds a payment source up front so the amount screen can cap entry and price the fee
        // before anything is confirmed. Dollars is the house default; failing that, whichever held
        // currency goes furthest.
        eventFlow.filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .filterIsInstance<SwapPurpose.Buy>()
            .filter { it.fundingSource == FundingSource.Flexible }
            .onEach { purpose ->
                val spendable = tokenCoordinator.tokenBalances
                    .first { it.isNotEmpty() }
                    .filter { it.token.address != purpose.mint && it.balance.hasDisplayableValue }

                val default = spendable.firstOrNull { it.token.address == Mint.usdf }
                    ?: spendable.maxByOrNull { it.balance }
                    ?: return@onEach

                dispatchEvent(Event.OnFundingSourceSelected(default.token.address))
            }
            .launchIn(viewModelScope)

        // Resolving a picked source only re-points the entry cap — it must not price the buy or
        // advance to the receipt, which is what OnFundingTokenSelected does at confirm time.
        eventFlow.filterIsInstance<Event.OnFundingSourceSelected>()
            .map { it.mint }
            .distinctUntilChanged()
            .flatMapLatest { mint ->
                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(mint),
                ) { tokens, balance ->
                    val token = tokens.find { it.address == mint } ?: return@combine null
                    TokenWithBalance(token = token, balance = balance)
                }
            }
            .filterNotNull()
            .onEach { dispatchEvent(Event.OnFundingSourceResolved(it)) }
            .launchIn(viewModelScope)

        // A token account outlives a balance that has gone to zero, so it — not the balance
        // alone — is what tells a Get whether it is a first buy or a top-up.
        eventFlow.filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose.mint }
            .distinctUntilChanged()
            .flatMapLatest { accountController.observeHasAccountFor(it) }
            .onEach { dispatchEvent(Event.OnTokenAccountKnown(it)) }
            .launchIn(viewModelScope)

        eventFlow.filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                val mint = purpose.mint

                combine(
                    tokenCoordinator.tokenBalances,
                    exchange.observePreferredRate(),
                ) { tokens, rate ->
                    var token = tokens.find { it.token.address == mint }
                    if (token == null) {
                        val tokenRef = tokenCoordinator.getTokenMetadata(mint).getOrNull()
                        if (tokenRef != null) {
                            token = TokenWithBalance(
                                token = tokenRef.token,
                                balance = Fiat.Zero,
                            )
                        }
                    }

                    if (token == null) {
                        trace(
                            tag = "BuySellSwap",
                            message = "Unable to find token for mint ${mint.base58()}"
                        )
                        dispatchEvent(Event.Exit)
                        return@combine null
                    }

                    val balance = LocalFiat(
                        usdf = token.balance,
                        nativeAmount = token.balance.convertingTo(rate),
                    )

                    TokenWithBalance(token.token, balance.nativeAmount)
                }
            }
            .filterNotNull()
            .onEach { token -> dispatchEvent(Event.OnSelectedTokenChanged(token)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnPurposeChanged>()
            .map { it.purpose }
            .flatMapLatest { purpose ->
                val tokenAddress = when (purpose) {
                    is SwapPurpose.Buy -> Mint.usdf
                    is SwapPurpose.Sell -> purpose.mint
                    is SwapPurpose.Convert -> purpose.mint
                }

                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(tokenAddress),
                    exchange.observePreferredRate(),
                ) { tokens, balance, rate ->
                    val token = tokens.find { it.address == tokenAddress } ?: return@combine null
                    TokenWithLocalizedBalance(
                        token = token,
                        balance = LocalFiat(
                            usdf = balance,
                            nativeAmount = balance.convertingTo(rate),
                        )
                    )
                }
            }.filterNotNull().mapNotNull { (token, balance) ->
                exchange.getCurrency(balance.rate.currency.name)
            }.onEach { currency ->
                amountDelegate.onCurrencyChanged(currency)
                dispatchEvent(Event.OnCurrencyChanged(currency))
                val pending = stateFlow.value.pendingInitialAmount
                if (pending != null) {
                    amountDelegate.prefill(pending.decimalValue)
                    dispatchEvent(Event.OnInitialAmountEntered)
                }
            }.launchIn(viewModelScope)

        combine(
            tokenCoordinator.observeReservesBalance(),
            exchange.observePreferredRate(),
        ) { balance, rate ->
            LocalFiat(
                usdf = balance,
                nativeAmount = balance.convertingTo(rate),
            )
        }.filter {
            stateFlow.value.buyProgress.isIdle && stateFlow.value.sellProgress.isIdle
        }.onEach {
            dispatchEvent(Event.OnReservesUpdated(TokenWithBalance(Token.usdf, it.nativeAmount)))
        }.launchIn(viewModelScope)

        // Convert: keep the destination token resolved as the user swaps it in the picker. The
        // route can hand us a destination that isn't usable (converting Dollars→Dollars), so pick a
        // sensible default in that case rather than dead-ending the flow.
        stateFlow.map { it.purpose }
            .filterIsInstance<SwapPurpose.Convert>()
            .map { it.mint to it.destinationMint }
            .distinctUntilChanged()
            .flatMapLatest { (source, destination) ->
                combine(
                    tokenCoordinator.tokenBalances,
                    exchange.observePreferredRate(),
                ) { balances, rate ->
                    if (destination == source) {
                        val fallback = if (source == Mint.usdf) {
                            balances.filter { it.token.address != source }
                                .maxByOrNull { it.balance }?.token?.address
                        } else {
                            Mint.usdf
                        }
                        fallback?.let { dispatchEvent(Event.OnDestinationSelected(it)) }
                        return@combine null
                    }

                    val held = balances.find { it.token.address == destination }
                    val token = held?.token
                        ?: tokenCoordinator.getTokenMetadata(destination).getOrNull()?.token
                        ?: return@combine null

                    TokenWithBalance(
                        token = token,
                        balance = (held?.balance ?: Fiat.Zero).convertingTo(rate),
                    )
                }
            }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnDestinationTokenResolved(it)) }
            .launchIn(viewModelScope)

        transactionController.limits
            .onEach { dispatchEvent(Event.OnLimitsChanged(it)) }
            .launchIn(viewModelScope)

        combine(stateFlow, amountDelegate.state) { vmState, delegateState ->
            vmState.amountEntryState.limits to delegateState.currency.code
        }
            .filter { it.first != null }
            .mapNotNull {
                val currency = it.second ?: return@mapNotNull null
                it.first to currency
            }
            .onEach { (limits, currency) ->
                val sendLimit = limits?.sendLimitFor(currency) ?: SendLimit.Zero
                val nextTransactionLimit = sendLimit.maxPerDay
                dispatchEvent(Event.OnMaxDetermined(nextTransactionLimit, currency))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountConfirmed>()
            .map { amountDelegate.state.value }
            .filterNot {
                val purpose = stateFlow.value.purpose
                if (purpose is SwapPurpose.BalanceDecrease) {
                    checkBalanceLimit()
                } else {
                    false
                }
            }.filterNot { checkFundingAmount() }
            .mapNotNull {
                val purpose = stateFlow.value.purpose ?: return@mapNotNull null
                it to purpose
            }
            .onEach { (delegateState, purpose) ->
                val isAddingMoney = stateFlow.value.isAddingMoney
                addMoneyMethod?.let { method ->
                    analytics.addMoneyAmountConfirmed(
                        method = method,
                        amount = Fiat(
                            delegateState.enteredAmount,
                            delegateState.currency.code ?: CurrencyCode.USD,
                        ),
                    )
                }
                when (purpose) {
                    is SwapPurpose.Buy -> {
                        val rate = exchange.preferredRate

                        when {
                            purpose.fundingSource == FundingSource.Phantom -> {
                                // Deposit-first "Add Money" via Phantom: the wallet is
                                // already connected and the amount was just entered, so
                                // confirming it triggers the Phantom transaction request.
                                dispatchEvent(Event.ConfirmPhantomTransaction)
                            }

                            !isAddingMoney -> {
                                val fundingMint = stateFlow.value.fundingMint
                                if (stateFlow.value.isGet && fundingMint != null) {
                                    // v2 Get: the payment source was picked on the amount screen,
                                    // so confirming prices the buy straight away and lands on the
                                    // receipt — no funding-token step in between.
                                    dispatchEvent(Event.OnFundingTokenSelected(fundingMint))
                                } else {
                                    // Direct buy — let the user choose which token funds it.
                                    // The reserves-vs-cross-currency decision is deferred to
                                    // the buyOrSwap flow, once a funding token is selected.
                                    dispatchEvent(
                                        Event.SelectFundingToken(
                                            Fiat(delegateState.enteredAmount, rate.currency)
                                        )
                                    )
                                }
                            }

                            else -> {
                                // Adding money via an external source — check purchase methods
                                val mint = purpose.mint
                                val metadata = PurchaseMethodMetadata(
                                    mint = mint,
                                    purchaseAmount = Fiat(delegateState.enteredAmount, rate.currency),
                                    canUseOtherWallets = true, // allow external USDC deposit as a "purchase" option
                                )
                                val pinnedMethod = when (purpose.fundingSource) {
                                    FundingSource.Coinbase -> PurchaseMethod.CoinbaseOnRamp
                                    FundingSource.Phantom -> PurchaseMethod.PhantomWallet
                                    FundingSource.Flexible -> null
                                }
                                if (pinnedMethod != null) {
                                    purchaseMethodController.select(pinnedMethod, metadata)
                                } else {
                                    val methods = purchaseMethodController.state.value.availableMethods
                                    if (methods.size == 1) {
                                        // Single method — skip sheet, handle directly
                                        purchaseMethodController.select(methods.first(), metadata)
                                    } else {
                                        purchaseMethodController.present(metadata)
                                    }
                                }
                            }
                        }
                    }

                    is SwapPurpose.Convert -> {
                        val rate = exchange.preferredRate
                        val sourceWithBalance = stateFlow.value.tokenWithBalance ?: return@onEach
                        // Converting out of Dollars charges the fee on top, so the whole balance
                        // can't be converted — trim the entry to the real maximum instead of
                        // pricing a debit the balance can't cover. Every other direction has its
                        // fee skimmed out of the entry, which can't overrun.
                        if (stateFlow.value.isConvertingFromDollars) {
                            correctEntryToAffordable(
                                balance = sourceWithBalance.balance,
                                feeBps = convertFeeBps,
                                feeChargedOnTop = true,
                            )
                        }
                        // The pin is taken against the total debited, which is the entered amount
                        // plus the fee when converting out of Dollars (see [totalDebitAmount]).
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = totalDebitAmount,
                            token = sourceWithBalance.token,
                            balance = sourceWithBalance.balance,
                            rate = rate,
                        ).getOrElse {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_staleRates),
                                message = resources.getString(R.string.error_description_staleRates),
                            )
                            return@onEach
                        }

                        dispatchEvent(
                            Event.OnAmountAccepted(
                                amountFiat,
                                netTransferAmount = netTransferAmount,
                                enteredAmount = enteredAmount,
                                feeAmount = feeAmount,
                            )
                        )
                        dispatchEvent(Event.ShowConvertReceipt)
                    }

                    is SwapPurpose.Sell -> {
                        val rate = exchange.preferredRate
                        val tokenWithBalance = stateFlow.value.tokenWithBalance!!
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = Fiat(delegateState.enteredAmount, rate.currency),
                            token = tokenWithBalance.token,
                            balance = tokenWithBalance.balance,
                            rate = rate,
                        ).getOrElse {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_staleRates),
                                message = resources.getString(R.string.error_description_staleRates),
                            )
                            return@onEach
                        }
                        val netAmount = netTransferAmount

                        dispatchEvent(
                            Event.OnAmountAccepted(
                                amountFiat,
                                netTransferAmount = netAmount,
                                enteredAmount = enteredAmount,
                                feeAmount = feeAmount,
                            )
                        )
                        dispatchEvent(Event.ShowSellReceipt)
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmPhantomTransaction>()
            .onEach {
                try {
                    val delegateState = amountDelegate.state.value
                    // Use the user's preferred (selected) currency rate so the entered amount is
                    // interpreted in the currency shown in the UI. Forcing rateForUsd() here labelled
                    // a non-USD amount (e.g. ₹500) as USD, so compute() skipped the FX conversion and
                    // produced an underlyingTokenAmount ~83× too large — which made checkBalances
                    // reject the deposit as InsufficientUsdc for INR (and any non-USD) users.
                    val rate = exchange.preferredRate
                    val amountFiat = verifiedFiatCalculator.compute(
                        amount = Fiat(delegateState.enteredAmount, rate.currency),
                        token = Token.usdf,
                        rate = rate,
                    ).getOrElse {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_staleRates),
                            message = resources.getString(R.string.error_description_staleRates),
                        )
                        return@onEach
                    }
                    dispatchEvent(
                        Event.OnAmountAccepted(
                            amountFiat,
                            netTransferAmount = amountFiat.localFiat.nativeAmount,
                            enteredAmount = enteredAmount,
                            feeAmount = feeAmount,
                        )
                    )
                    dispatchEvent(Event.UpdateBuyState(loading = true))
                    val token = resolveToken()
                        ?: run {
                            handlePhantomError(IllegalStateException("Token not available"))
                            return@onEach
                        }

                    signAndSendPhantomTransaction(token, amountFiat)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handlePhantomError(e)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.StartPhantomCeremony>()
            .onEach { connectPhantomWallet() }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DepositSubmitted>()
            .onEach { event ->
                val owner = userManager.accountCluster ?: return@onEach
                val baseline = tokenCoordinator.balanceForToken(Mint.usdf).first()

                // A Coinbase order only settles the on-chain send after payment, while it
                // sits in PROCESSING. Wait for that delivery before sweeping so we don't
                // poll an empty ATA and give up too early. Phantom deposits (no orderId)
                // are already on-chain, so they sweep immediately.
                val delivered = when (val orderId = event.orderId) {
                    null -> true // phantom deposit, no waiting needed
                    else -> when (coinbaseOnRampController.awaitOrderDelivered(orderId)) {
                        is OrderDeliveryResult.Delivered -> true
                        OrderDeliveryResult.Failed -> {
                            dispatchEvent(Event.UpdateProcessingState(loading = false, error = true))
                            addMoneyMethod?.let { method ->
                                analytics.addMoney(
                                    method = method,
                                    amount = netTransferAmount,
                                    successful = false,
                                    error = IllegalStateException("Order delivery failed"),
                                )
                            }
                            false
                        }
                        // The deposit may still land later; the session's foreground sweep
                        // will reconcile it. Don't claim success we can't confirm.
                        OrderDeliveryResult.TimedOut -> {
                            dispatchEvent(Event.UpdateProcessingState(loading = false, error = true))
                            addMoneyMethod?.let { method ->
                                analytics.addMoney(
                                    method = method,
                                    amount = netTransferAmount,
                                    successful = false,
                                    error = IllegalStateException("Order delivery timed out"),
                                )
                            }
                            false
                        }
                    }
                }
                if (!delivered) return@onEach

                // Funds are on-chain now: kick off the USDC→USDF sweep, then wait for the
                // resulting USDF balance bump to mark the deposit complete. UsdcDepositSweep
                // owns the actual sweep + on-chain polling; we just observe the result.
                if (event.orderId != null) {
                    usdcDepositSweep.execute(owner)
                }
                tokenCoordinator.balanceForToken(Mint.usdf).first { it > baseline }
                feedCoordinator.fetchSinceLatest()
                dispatchEvent(Event.UpdateProcessingState(loading = false, success = true))
                addMoneyMethod?.let { method ->
                    analytics.addMoney(method, netTransferAmount)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSellConfirmed>()
            .map { stateFlow.value.amountEntryState.selectedAmount }
            .onEach {
                dispatchEvent(Event.UpdateSellState(loading = true))
                dispatchEvent(Event.ProceedWithSale(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnConvertConfirmed>()
            .map { stateFlow.value.amountEntryState.selectedAmount }
            .onEach {
                dispatchEvent(Event.UpdateSellState(loading = true))
                dispatchEvent(Event.ProceedWithConversion(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ProceedWithConversion>()
            .onEach { dispatchEvent(Event.UpdateSellState(loading = true)) }
            .mapNotNull { event ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                stateFlow.value.purpose as? SwapPurpose.Convert ?: return@mapNotNull null
                val source = stateFlow.value.tokenWithBalance?.token ?: return@mapNotNull null
                val destination = stateFlow.value.destinationTokenWithBalance?.token
                    ?: return@mapNotNull null
                Triple(owner, source to destination, event.amount)
            }
            .onEach { (owner, tokens, amount) ->
                val (source, destination) = tokens
                val rate = exchange.preferredRate

                // Refresh the source balance from the network so the debit we're about to submit is
                // checked against what's actually on-chain (mirrors ProceedWithSale).
                tokenCoordinator.updateTokenAccount(source.address)
                val amountInUsd =
                    Fiat.tokenBalance(amount.localFiat.underlyingTokenAmount.quarks, source)
                val refreshedBalance = tokenCoordinator.balanceForToken(source)
                if (amountInUsd > refreshedBalance) {
                    dispatchEvent(Event.UpdateSellState(loading = false))
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds),
                    )
                    return@onEach
                }

                // Three legs, one screen: into Dollars is a plain sale, out of Dollars is a buy
                // that carries an explicit fee, and currency→currency is a cross-currency swap
                // whose pool fee is applied on-chain (so it's sent as null, server-enforced).
                val result = when {
                    destination.address == Mint.usdf -> transactionController.sell(
                        owner = owner,
                        amount = amount,
                        of = source,
                    )

                    source.address == Mint.usdf -> transactionController.buy(
                        owner = owner,
                        amount = amount,
                        feeAmount = LocalFiat.fromUsd(
                            usdf = stateFlow.value.feeAmount.convertingToUsdIfNeeded(rate),
                            rate = rate,
                        ),
                        of = destination,
                    )

                    else -> transactionController.swap(
                        owner = owner,
                        amount = amount,
                        from = source,
                        to = destination,
                    )
                }

                result.onSuccess { swapId ->
                    trackTransaction(source)
                    dispatchEvent(Event.OnSwapIdChanged(swapId))
                    dispatchEvent(Event.OnConvertSubmitted(destination, swapId))
                    dispatchEvent(Event.UpdateSellState(loading = false, success = true))
                    tokenCoordinator.subtract(source, amount.localFiat)
                }.onFailure { cause ->
                    trackTransaction(source, error = cause)
                    dispatchEvent(Event.UpdateSellState(loading = false, success = false))
                    if (cause is SwapError.Denied && cause.amountTooLowForFee) {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_sellFailedDueToBeingTooLow),
                            message = resources.getString(R.string.error_description_sellFailedDueToBeingTooLow),
                        )
                        return@onFailure
                    }
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnFundingTokenSelected>()
            .filter {  stateFlow.value.purpose is SwapPurpose.Buy }
            .map { it.mint }
            .mapNotNull {
                val token = tokenCoordinator.getTokenMetadata(it).getOrNull()?.token ?: return@mapNotNull null
                val rate = exchange.preferredRate

                // The fee comes out of the same balance that funds the buy, so entering the whole
                // balance can never cover both. Trim the entry to the real maximum before pricing
                // anything, so the receipt and the amount screen show the same number.
                val (feeBps, feeChargedOnTop) = buyFeeFor(token)
                correctEntryToAffordable(
                    balance = tokenCoordinator.balanceForToken(token).convertingTo(rate),
                    feeBps = feeBps,
                    feeChargedOnTop = feeChargedOnTop,
                )

                val delegateState = amountDelegate.state.value
                val amountFiat = verifiedFiatCalculator.compute(
                    amount = Fiat(delegateState.enteredAmount, rate.currency),
                    token = token,
                    balance = tokenCoordinator.balanceForToken(token),
                    rate = rate,
                ).getOrElse {
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_staleRates),
                        message = resources.getString(R.string.error_description_staleRates),
                    )
                    return@mapNotNull null
                }

                val nativeAmount = amountFiat.localFiat.nativeAmount

                val tokenWithBalance = TokenWithBalance(
                    token = token,
                    balance = nativeAmount
                )

                val exchangeFee = if (feeChargedOnTop) {
                    // Dollars has no launchpad sale to skim, so a v2 Get pays the flat house rate
                    // *on top* of the entered amount — the same convention Convert-from-Dollars
                    // uses.
                    nativeAmount.launchpadSellFee(feeBps)
                } else {
                    // The pool's sell fee is grossed up on top of the entered amount, so the
                    // fee is (amount / (1 - fee)) - amount. Uses the funding pool's own bps,
                    // matching the gross-up applied at buy time in OnBuyConfirmed. v1's reserves
                    // buy charges nothing, so its zero bps nets out to zero here.
                    nativeAmount.grossingUpLaunchpadSellFee(feeBps) - nativeAmount
                }

                dispatchEvent(
                    Event.OnAmountAccepted(
                        amountFiat,
                        // The success screen shows this as "amount received of {token}", so it must
                        // be the purchase amount — what lands in the target token — not the grossed-up
                        // debit. The confirmation's "you pay" total is recomputed from enteredAmount +
                        // feeAmount separately (see BuyReceipt), so it stays correct.
                        netTransferAmount = amountFiat.localFiat.nativeAmount,
                        enteredAmount = enteredAmount,
                        feeAmount = exchangeFee,
                    )
                )
                dispatchEvent(Event.OnFundingTokenResolved(tokenWithBalance))
            }.onEach {

            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBuyConfirmed>()
            .onEach { event ->
                if (stateFlow.value.purpose !is SwapPurpose.Buy) return@onEach
                // Guard: the executor force-unwraps the target token.
                stateFlow.value.tokenWithBalance?.token ?: return@onEach
                val rate = exchange.preferredRate

                val fundingToken = stateFlow.value.fundingTokenWithBalance?.token ?: return@onEach

                // The amount delegate persists across the token-select screen, so the
                // entered amount can be recomputed here rather than threaded through state.
                val enteredFiat = Fiat(amountDelegate.state.value.enteredAmount, rate.currency)
                // Non-USDF funding pays the pool's sell fee, applied implicitly on-chain during
                // the swap. Gross up the entered (net) amount by 1/(1 - fee) so the on-chain fee
                // deduction nets back down to exactly what was entered. The fee is the FUNDING
                // pool's — that's the token being sold — and is sent as zero (server-enforced) in
                // the swap request below. USDF pays no pool fee.
                // A v2 Get from Dollars is the exception: its fee is charged on top rather than
                // skimmed on-chain, so the debit is entered + fee and the fee travels explicitly
                // in the request (see ProceedWithPurchase).
                val amountFiat = verifiedFiatCalculator.compute(
                    amount = if (fundingToken.address == Mint.usdf) {
                        if (stateFlow.value.isGet) {
                            enteredFiat + enteredFiat.launchpadSellFee(DEFAULT_CONVERT_FEE_BPS)
                        } else {
                            enteredFiat
                        }
                    } else {
                        enteredFiat.grossingUpLaunchpadSellFee(
                            bps = fundingToken.launchpadMetadata.sellFeeBpsOrHouseRate,
                        )
                    },
                    token = fundingToken,
                    balance = tokenCoordinator.balanceForToken(fundingToken).convertingToUsdIfNeeded(rate),
                    rate = rate,
                ).getOrElse {
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_staleRates),
                        message = resources.getString(R.string.error_description_staleRates),
                    )
                    return@onEach
                }

                dispatchEvent(Event.ProceedWithPurchase(amountFiat))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ProceedWithPurchase>()
            .onEach { dispatchEvent(Event.UpdateBuyState(loading = true)) }
            .mapNotNull { event ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                stateFlow.value.purpose ?: return@mapNotNull null
                val targetToken = stateFlow.value.tokenWithBalance?.token ?: return@mapNotNull null
                Triple(owner, targetToken, event)
            }
            .onEach { (owner, targetToken, event) ->
                val amount = event.amount
                // The funding token was chosen on the token-select screen; USDF means
                // buy straight from reserves, anything else is a cross-currency swap.
                val fundingToken = stateFlow.value.fundingTokenWithBalance

                if (fundingToken == null) {
                    dispatchEvent(Event.UpdateBuyState(loading = false, success = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                    return@onEach
                }

                val result = if (fundingToken.token.address == Mint.usdf) {
                    // `amount` is the gross debit — the service nets the fee back out of it. v1
                    // charges nothing, so its gross is the entered amount and the fee stays null;
                    // a v2 Get sends the house fee explicitly (mirrors ProceedWithConversion).
                    val rate = exchange.preferredRate
                    transactionController.buy(
                        owner = owner,
                        amount = amount,
                        feeAmount = if (stateFlow.value.chargesBuyFee) {
                            LocalFiat.fromUsd(
                                usdf = stateFlow.value.feeAmount.convertingToUsdIfNeeded(rate),
                                rate = rate,
                            )
                        } else {
                            null
                        },
                        of = targetToken,
                    )
                } else {
                    // `amount` is grossed up; the pool's sell fee is applied on-chain during the
                    // swap, so feeAmount is left null (the server rejects a non-zero fee here).
                    transactionController.swap(
                        owner = owner,
                        amount = amount,
                        from = fundingToken.token,
                        to = targetToken,
                    )
                }

                result.onSuccess { swapId ->
                    trackTransaction(targetToken)
                    dispatchEvent(Event.OnSwapIdChanged(swapId))
                    dispatchEvent(Event.OnPurchaseSubmitted(targetToken, swapId))
                    dispatchEvent(Event.UpdateBuyState(loading = false, success = true))
                    // buy/swap submitted, drop the spent balance from the funding token
                    tokenCoordinator.subtract(fundingToken.token, amount.localFiat)
                }.onFailure { cause ->
                    trackTransaction(targetToken, error = cause)
                    dispatchEvent(Event.UpdateBuyState(loading = false, success = false))
                    if (cause is SwapError.InvalidSwap) {
                        if (cause.insufficientBalance) {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_paymentFailedDueToInsufficientFunds),
                                message = resources.getString(R.string.error_title_paymentFailedDueToInsufficientFunds),
                            )
                        }
                        return@onFailure
                    }

                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ProceedWithSale>()
            .onEach { dispatchEvent(Event.UpdateSellState(loading = true)) }
            .map { it.amount }
            .mapNotNull { amount ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                val purpose = stateFlow.value.purpose ?: return@mapNotNull null
                owner to purpose to amount
            }
            .map { (owner, purpose, amount) -> owner to stateFlow.value.tokenWithBalance!!.token to amount }
            .onEach { (owner, token, amount) ->
                // Refresh balance from network before submitting to ensure
                // the on-chain balance matches what we're about to sell.
                tokenCoordinator.updateTokenAccount(token.address)

                // underlyingTokenAmount.quarks are token quarks, not USD —
                // convert back through the bonding curve for an apples-to-apples comparison.
                val amountInUsd =
                    Fiat.tokenBalance(amount.localFiat.underlyingTokenAmount.quarks, token)
                val refreshedBalance = tokenCoordinator.balanceForToken(token)
                if (amountInUsd > refreshedBalance) {
                    dispatchEvent(Event.UpdateSellState(loading = false))
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds),
                    )
                    return@onEach
                }

                transactionController.sell(
                    owner = owner,
                    amount = amount,
                    of = token,
                ).onSuccess { swapId ->
                    trackTransaction(token)
                    dispatchEvent(Event.OnSwapIdChanged(swapId))
                    dispatchEvent(Event.OnSellSubmitted(token, swapId))
                    dispatchEvent(Event.UpdateSellState(loading = false, success = true))
                    // sell submitted, drop from balance
                    tokenCoordinator.subtract(token, amount.localFiat)
                }.onFailure { cause ->
                    trackTransaction(token, error = cause)
                    dispatchEvent(Event.UpdateSellState(loading = false, success = false))
                    if (cause is SwapError.Denied) {
                        if (cause.amountTooLowForFee) {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_sellFailedDueToBeingTooLow),
                                message = resources.getString(R.string.error_description_sellFailedDueToBeingTooLow),
                            )
                            return@onFailure
                        }
                    }
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnSwapIdChanged>()
            .mapNotNull { event ->
                val owner = userManager.accountCluster ?: return@mapNotNull null
                owner to event.swapId
            }
            .map { (owner, swapId) ->
                dispatchEvent(Event.UpdateProcessingState(loading = true))
                transactionController.pollSwapForState(
                    swapId = swapId,
                    owner = owner,
                    targetState = SwapState.FINALIZED
                )
            }.onResult(
                onSuccess = {
                    val token = stateFlow.value.tokenWithBalance!!.token
                    val isUsingReserves = stateFlow.value.purpose is SwapPurpose.Buy ||
                            stateFlow.value.purpose is SwapPurpose.Sell
                    viewModelScope.launch { tokenCoordinator.updateTokenAccount(token) }
                    if (isUsingReserves) {
                        viewModelScope.launch { tokenCoordinator.updateTokenAccount(Mint.usdf) }
                    }
                    // A conversion moves value between two accounts; refresh the one it landed in.
                    (stateFlow.value.purpose as? SwapPurpose.Convert)?.let { convert ->
                        viewModelScope.launch {
                            tokenCoordinator.updateTokenAccount(convert.destinationMint)
                        }
                    }
                    viewModelScope.launch {
                        // update activity feed to grab the tx as a result of this buy/sell
                        feedCoordinator.fetchSinceLatest()
                    }
                    dispatchEvent(Event.UpdateProcessingState(loading = false, success = true))
                },
                onError = {
                    dispatchEvent(
                        Event.UpdateProcessingState(
                            loading = false,
                            success = false,
                            error = true
                        )
                    )
                }
            ).launchIn(viewModelScope)

        // Reset buy loading state when Coinbase payment is canceled or fails.
        // CoinbaseOnRampHandler handles error display; we just clear loading.
        coinbaseOnRampController.state
            .onEach { s ->
                when (s) {
                    is CoinbaseOnRampState.Failed -> {
                        addMoneyMethod?.let { method ->
                            analytics.addMoney(method, successful = false, error = s.error)
                        }
                        dispatchEvent(Event.UpdateBuyState())
                    }

                    CoinbaseOnRampState.Idle -> dispatchEvent(Event.UpdateBuyState())

                    is CoinbaseOnRampState.Completed,
                    is CoinbaseOnRampState.Paying -> Unit
                }
            }.launchIn(viewModelScope)

        purchaseMethodController.selections
            .onEach { (method, metadata) ->
                // The add-money deposit sheet (presentDepositOptions) emits an amount-less
                // selection and owns its own navigation (it returns the route to open). Only react
                // to an actual purchase, which carries a purchaseAmount. Reacting to the amount-less
                // deposit selection would double-navigate: Coinbase would push verification twice,
                // and Phantom would send this (buy) flow to PhantomConnect *underneath* the pushed
                // add-money flow — so finishing the add-money flow strands the user on the connect
                // prompt instead of returning to the token screen.
                val amount = metadata.purchaseAmount ?: return@onEach
                when (method) {
                    PurchaseMethod.CoinbaseOnRamp -> {
                        analytics.buttonTapped(Button.TokenBuyWithCoinbase)
                        dispatchEvent(Event.CoinbaseSelected)

                        val profile = userManager.profile
                        val needsPhone = profile?.verifiedPhoneNumber == null
                        val requireVerification = userFlags.resolvedFlags.value.requireCoinbaseEmailVerification.effectiveValue
                        // Email entry is always required; verification (server round-trip)
                        // only when the flag is on. Off → any entered email counts.
                        val hasEmail = if (requireVerification) {
                            profile?.verifiedEmailAddress != null
                        } else {
                            profile?.email?.value != null
                        }
                        val needsEmail = !hasEmail
                        if (needsPhone || needsEmail) {
                            dispatchEvent(
                                Event.OnVerificationNeeded(
                                    needsPhone,
                                    needsEmail,
                                    skipEmailVerification = !requireVerification,
                                )
                            )
                            return@onEach
                        }

                        if (amount < minimumCoinbasePurchaseAmount) {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampAmountTooLow),
                                message = resources.getString(R.string.error_description_onrampAmountTooLow),
                            )
                            return@onEach
                        }

                        val rate = exchange.preferredRate
                        val amountFiat = verifiedFiatCalculator.compute(
                            amount = amount,
                            token = Token.usdf,
                            rate = rate,
                        ).getOrElse {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_staleRates),
                                message = resources.getString(R.string.error_description_staleRates),
                            )
                            return@onEach
                        }

                        dispatchEvent(
                            Event.OnAmountAccepted(
                                amountFiat,
                                netTransferAmount = amountFiat.localFiat.nativeAmount,
                                enteredAmount = enteredAmount,
                                feeAmount = feeAmount,
                            )
                        )
                        dispatchEvent(Event.UpdateBuyState(loading = true))
                        val token = resolveToken() ?: return@onEach
                        executeCoinbasePurchase(amountFiat, token)
                    }

                    is PurchaseMethod.CashReserves -> {
                        dispatchEvent(Event.OnAmountConfirmed)
                    }

                    PurchaseMethod.PhantomWallet -> {
                        analytics.buttonTapped(Button.TokenBuyWithPhantom)
                        dispatchEvent(Event.PhantomSelected)
                    }

                    PurchaseMethod.OtherWallet -> {
                        analytics.buttonTapped(Button.TokenBuyWithOtherWallet)
                        dispatchEvent(Event.OtherWalletSelected)
                    }
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PresentDepositOptions>()
            .mapNotNull {
                analytics.addMoneyOpened(Analytics.AddMoneySource.BuyShortfall)
                // present the add-money/deposit sheet; navigate to whatever the user picks.
                // popToRoot = false: the chosen add-money route replaces this buy flow (see the
                // OpenScreen handler in SwapEntryScreen), so finishing it pops a single level back
                // to the token screen — no full pop-to-root needed.
                purchaseMethodController.presentDepositOptions(popToRoot = false)
            }
            .onEach { route -> dispatchEvent(Event.OpenScreen(route)) }
            .launchIn(viewModelScope)
    }

    private suspend fun resolveToken(): Token? {
        val mint = stateFlow.value.purpose?.mint
        val token = stateFlow.value.tokenWithBalance?.token
            ?: mint?.let { tokenCoordinator.getTokenMetadata(it).getOrNull()?.token }

        return token
    }

    private suspend fun executeCoinbasePurchase(
        amountFiat: VerifiedFiat,
        token: Token,
    ) {
        coinbaseOnRampController.checkPurchaseGates()
            .fold(
                onSuccess = { proceedWithCoinbasePurchase(amountFiat, token) },
                onFailure = { gate ->
                    dispatchEvent(Event.UpdateBuyState())
                    when (gate) {
                        is PurchaseGate.GooglePayNotSupported -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampGooglePayNotSupported),
                                message = resources.getString(R.string.error_description_onrampGooglePayNotSupported),
                            )
                        }

                        is PurchaseGate.GooglePayNoPaymentMethod -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampGooglePayNotReady),
                                message = resources.getString(R.string.error_description_onrampGooglePayNotReady),
                            )
                        }

                        is PurchaseGate.WebViewWarning -> {
                            BottomBarManager.showAlert(
                                title = resources.getString(R.string.error_title_onrampNonStableWebView),
                                message = resources.getString(
                                    R.string.error_description_onrampNonStableWebView,
                                    gate.channel.name,
                                ),
                                actions = listOf(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_cancel),
                                        style = BottomBarManager.BottomBarButtonStyle.Filled,
                                    ) { /* dismiss — loading already cleared */ },
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_continueAnyway),
                                        style = BottomBarManager.BottomBarButtonStyle.Text,
                                    ) {
                                        viewModelScope.launch {
                                            dispatchEvent(Event.UpdateBuyState(loading = true))
                                            proceedWithCoinbasePurchase(amountFiat, token)
                                        }
                                    },
                                ),
                            )
                        }
                    }
                },
            )
    }

    private suspend fun proceedWithCoinbasePurchase(
        amountFiat: VerifiedFiat,
        token: Token,
    ) {
        coinbaseOnRampController.placeOrderAndStartPayment(
            token = token,
            verifiedFiat = amountFiat,
        ).onSuccess {
            trackTransaction(token)
            addMoneyMethod?.let { method ->
                analytics.addMoneyPaymentInvoked(method, amountFiat.localFiat.nativeAmount)
            }
        }.onFailure { error ->
            dispatchEvent(Event.UpdateBuyState())
            when (error) {
                is OnRampAuthError.CoinbasePhoneVerificationRequired ->
                    dispatchEvent(Event.OnVerificationNeeded(phone = true, email = false))

                is OnRampAuthError.VerificationRequired ->
                    dispatchEvent(Event.OnVerificationNeeded(error.phone, error.email))

                else -> {
                    trackTransaction(token, error = error)
                    addMoneyMethod?.let { method ->
                        analytics.addMoney(
                            method = method,
                            amount = amountFiat.localFiat.nativeAmount,
                            successful = false,
                            error = error,
                        )
                    }
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_buySellFailed),
                        message = resources.getString(R.string.error_description_buySellFailed),
                    )
                }
            }
        }
    }

    private fun trackTransaction(token: Token, error: Throwable? = null) {
        val method = when (val purpose = stateFlow.value.purpose) {
            is SwapPurpose.Buy -> when (purpose.fundingSource) {
                FundingSource.Phantom -> Analytics.SwapMethod.Buy.Phantom
                FundingSource.Coinbase -> Analytics.SwapMethod.Buy.Coinbase
                else -> Analytics.SwapMethod.Buy.Reserves
            }

            else -> Analytics.SwapMethod.Sell
        }

        when (method) {
            Analytics.SwapMethod.Sell -> {
                analytics.sell(
                    amount = netTransferAmount,
                    feeAmount = feeAmount,
                    mint = token.address,
                    error = error
                )
            }

            is Analytics.SwapMethod.Buy -> {
                analytics.buy(
                    method = method.with,
                    amount = netTransferAmount,
                    mint = token.address,
                    error = error
                )
            }
        }
    }

    private fun connectPhantomWallet() {
        viewModelScope.launch {
            dispatchEvent(Event.UpdateBuyState(loading = true))
            phantomWalletController
                .connectWallet()
                .onSuccess {
                    dispatchEvent(Event.UpdateBuyState(success = true))
                    dispatchEvent(Event.PhantomConnected)
                    dispatchEvent(Event.UpdateBuyState())
                    analytics.connectWallet(OnRampProvider.Phantom)
                }.onFailure {
                    dispatchEvent(Event.UpdateBuyState())
                    handlePhantomError(it)
                }
        }
    }

    private fun signAndSendPhantomTransaction(
        token: Token,
        amount: VerifiedFiat,
    ) {
        viewModelScope.launch {
            phantomWalletController.executeSwap(
                amount = amount,
                fee = LocalFiat.Zero,
                token = token,
                onBeforeSign = {
                    analytics.amountSelectedForWalletTransfer(
                        OnRampProvider.Phantom,
                        amount.localFiat.underlyingTokenAmount
                    )
                    addMoneyMethod?.let { method ->
                        analytics.addMoneyPaymentInvoked(method, amount.localFiat.nativeAmount)
                    }
                },
            ).onSuccess { result ->
                when (result) {
                    is PhantomSwapResult.WithSwapId -> {
                        dispatchEvent(Event.UpdateProcessingState(loading = true))
                        dispatchEvent(Event.PhantomNavigateToProcessing(result.swapId))
                        dispatchEvent(Event.OnSwapIdChanged(result.swapId))
                    }
                    is PhantomSwapResult.DepositCompleted -> {
                        dispatchEvent(Event.UpdateProcessingState(loading = true))
                        dispatchEvent(Event.PhantomNavigateToProcessing())
                        dispatchEvent(Event.DepositSubmitted())
                    }
                }
            }.onFailure { error ->
                handlePhantomError(error)
            }
        }
    }

    private fun handlePhantomError(error: Throwable) {
        val deeplinkError = error as? DeeplinkOnRampError
            ?: DeeplinkOnRampError.FailedToCreateTransaction(message = error.message, cause = error)

        if (deeplinkError is DeeplinkOnRampError.WalletProvidedError && deeplinkError.code == DeeplinkError.UserRejectedRequest.code) {
            analytics.walletTransactionCancelled(OnRampProvider.Phantom)
        } else {
            if (deeplinkError is DeeplinkOnRampError.FailedToSendTransaction) {
                analytics.walletTransactionFailed(OnRampProvider.Phantom)
            }
            addMoneyMethod?.let { method ->
                analytics.addMoney(method, successful = false, error = deeplinkError)
            }
        }

        trace(
            tag = "BuySellSwap",
            message = "Something went wrong during phantom onramp",
            type = TraceType.Error,
            metadata = {
                "errorMessage" to deeplinkError.message
                "code" to deeplinkError.code
            },
            error = deeplinkError.takeUnless { it.isAlert }
        )

        val (title, message) = deeplinkError.messaging(
            resources::getString,
            resources.getString(R.string.label_phantom)
        )

        when {
            deeplinkError.isNetworkCause -> {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.error_title_noInternet),
                    message = resources.getString(R.string.error_description_noInternet),
                    onDismiss = { dispatchEvent(Event.PhantomCeremonyFailed) }
                )
            }

            deeplinkError.isAlert -> {
                BottomBarManager.showAlert(
                    title = title,
                    message = message,
                    onDismiss = { dispatchEvent(Event.PhantomCeremonyFailed) }
                )
            }

            else -> {
                BottomBarManager.showError(
                    title = title,
                    message = message,
                    onDismiss = { dispatchEvent(Event.PhantomCeremonyFailed) }
                )
            }
        }

        dispatchEvent(Event.UpdateBuyState())
    }


    private val minimumCoinbasePurchaseAmount = 5.toFiat()

    internal companion object {
        /**
         * House rate for a conversion when the source pool doesn't charge its own sell fee — 1%,
         * matching the launchpad default.
         */
        private const val DEFAULT_CONVERT_FEE_BPS = HOUSE_SELL_FEE_BPS

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnPurposeChanged -> { state ->
                    val incoming = event.purpose
                    val existing = state.purpose
                    // The entry step re-dispatches its *route* purpose every time it re-enters
                    // composition (e.g. popping back from the destination picker). For a Convert
                    // that would clobber the destination the user just picked, so keep the
                    // in-flight one whenever the source currency still matches.
                    val resolved = if (
                        incoming is SwapPurpose.Convert &&
                        existing is SwapPurpose.Convert &&
                        existing.mint == incoming.mint
                    ) {
                        existing
                    } else {
                        incoming
                    }
                    state.copy(purpose = resolved)
                }
                is Event.OnSelectedTokenChanged -> { state -> state.copy(tokenWithBalance = event.token) }
                is Event.OnTokenAccountKnown -> { state -> state.copy(hasTokenAccount = event.exists) }
                is Event.OnReservesUpdated -> { state -> state.copy(reservesWithBalance = event.reserves) }

                is Event.OnAmountAccepted -> { state ->
                    val entryState = state.amountEntryState
                    state.copy(
                        confirmedNetTransferAmount = event.netTransferAmount,
                        confirmedEnteredAmount = event.enteredAmount,
                        confirmedFeeAmount = event.feeAmount,
                        amountEntryState = entryState.copy(
                            selectedAmount = event.amount,
                        )
                    )
                }

                is Event.OnLimitsChanged -> { state ->
                    state.copy(
                        amountEntryState = state.amountEntryState.copy(
                            limits = event.limits
                        )
                    )
                }

                is Event.OnCurrencyChanged -> { state -> state }

                is Event.OnMaxDetermined -> { state ->
                    state.copy(
                        amountEntryState = state.amountEntryState.copy(
                            maxToAdd = event.max to event.currencyCode
                        ),
                    )
                }

                Event.CoinbaseSelected -> { state ->
                    val purpose = state.purpose
                    if (purpose is SwapPurpose.Buy) {
                        state.copy(purpose = purpose.copy(fundingSource = FundingSource.Coinbase))
                    } else {
                        state
                    }
                }

                Event.PhantomSelected -> { state ->
                    val purpose = state.purpose
                    if (purpose is SwapPurpose.Buy) {
                        state.copy(purpose = purpose.copy(fundingSource = FundingSource.Phantom))
                    } else {
                        state
                    }
                }

                Event.OtherWalletSelected,
                Event.ConfirmPhantomTransaction,
                Event.StartPhantomCeremony,
                Event.PhantomConnected,
                is Event.PhantomNavigateToProcessing,
                Event.PhantomCeremonyFailed,
                is Event.DepositSubmitted,
                Event.OnBuyConfirmed,
                Event.OnAmountConfirmed -> { state -> state }

                is Event.OnDestinationSelected -> { state ->
                    val purpose = state.purpose
                    if (purpose is SwapPurpose.Convert) {
                        state.copy(purpose = purpose.copy(destinationMint = event.mint))
                    } else {
                        state
                    }
                }

                is Event.OnDestinationTokenResolved -> { state ->
                    state.copy(destinationTokenWithBalance = event.token)
                }

                Event.SelectConvertDestination,
                Event.ShowConvertReceipt,
                Event.OnConvertConfirmed -> { state -> state }

                is Event.ProceedWithConversion -> { state -> state }
                is Event.OnConvertSubmitted -> { state -> state }

                is Event.SelectFundingToken -> { state -> state }
                is Event.OnFundingTokenSelected -> { state -> state }
                is Event.OnFundingTokenResolved -> { state -> state.copy(fundingTokenWithBalance = event.token) }

                is Event.OnFundingSourceResolved -> { state ->
                    state.copy(fundingTokenWithBalance = event.token)
                }
                is Event.SelectBuyFundingSource -> { state -> state }
                is Event.OnFundingSourceSelected -> { state -> state }

                is Event.UpdateBuyState -> { state ->
                    val entryState = state.buyProgress
                    state.copy(
                        buyProgress = entryState.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                is Event.UpdateSellState -> { state ->
                    val entryState = state.sellProgress
                    state.copy(
                        sellProgress = entryState.copy(
                            loading = event.loading,
                            success = event.success
                        )
                    )
                }

                is Event.UpdateProcessingState -> { state ->
                    val entryState = state.processingProgress
                    state.copy(
                        processingProgress = entryState.copy(
                            loading = event.loading,
                            success = event.success,
                            error = event.error,
                        )
                    )
                }

                is Event.OnSwapIdChanged -> { state -> state.copy(swapId = event.swapId) }

                is Event.ProceedWithPurchase -> { state -> state }
                is Event.ProceedWithSale -> { state -> state }
                is Event.CreateAndSendTransactionToWallet -> { state -> state }
                Event.OnTransactionSuccessful -> { state -> state }
                is Event.OnPurchaseSubmitted -> { state -> state }
                is Event.OnSellConfirmed -> { state -> state }
                is Event.OnSellSubmitted -> { state -> state }
                Event.ShowSellReceipt -> { state -> state }
                is Event.OnInitialAmountProvided -> { state ->
                    state.copy(minimumBuyAmount = event.amount, pendingInitialAmount = event.amount)
                }

                Event.OnInitialAmountEntered -> { state ->
                    state.copy(pendingInitialAmount = null)
                }

                is Event.OnVerificationNeeded -> { state -> state }
                Event.Exit -> { state -> state }
                Event.PresentDepositOptions -> { state -> state }
                is Event.OpenScreen -> { state -> state }
            }
        }
    }
}