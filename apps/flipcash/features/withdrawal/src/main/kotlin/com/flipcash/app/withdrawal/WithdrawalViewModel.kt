package com.flipcash.app.withdrawal

import android.content.ClipboardManager
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.withdrawal.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import com.getcode.vendor.Base58
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class DestinationState(
    val textFieldState: TextFieldState = TextFieldState(),
    val checkingClipboard: LoadingSuccessState = LoadingSuccessState(),
    val availability: WithdrawalAvailability? = null,
)

@HiltViewModel
internal class WithdrawalViewModel @Inject constructor(
    private val resources: ResourceHelper,
    private val exchange: Exchange,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
    private val userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    transactionController: TransactionOperations,
    clipboardManager: ClipboardManager,
    activityFeedCoordinator: ActivityFeedCoordinator,
    analytics: FlipcashAnalyticsService,
    private val tokenCoordinator: TokenCoordinator,
    dispatchers: DispatcherProvider,
) : BaseViewModel<WithdrawalViewModel.State, WithdrawalViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    private val tokenBalanceFlow: StateFlow<Fiat?> = stateFlow
        .map { it.tokenBalance.takeIf { balance -> balance != Fiat.Zero } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val minimumWithdrawalAmountFlow: StateFlow<Fiat?> = stateFlow
        .map { state ->
            val fee = state.feeInEntryCurrency ?: return@map null
            fee + fee.smallestUnit
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = AmountEntryStyle(
            actionLabel = resources.getString(R.string.action_next),
            infoHint = { resources.getString(R.string.subtitle_withdrawHint, it) },
            overMaxHint = { resources.getString(R.string.subtitle_withdrawHintLimitExceeded, it) },
            belowMinHint = { resources.getString(R.string.subtitle_withdrawHintMinimumNotMet, it) },
        ),
        loadingState = stateFlow.map { it.confirmingAmount }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState()),
        maxAmount = tokenBalanceFlow,
        minimumAmount = minimumWithdrawalAmountFlow,
    )

    internal data class State(
        val selectedTokenAddress: Mint? = null,
        val token: TokenWithBalance? = null,
        val entryRate: Rate? = null,
        val feeAmount: Fiat? = null,
        val confirmingAmount: LoadingSuccessState = LoadingSuccessState(),
        val selectedAmount: VerifiedFiat = VerifiedFiat(LocalFiat.Zero, null),
        val destinationState: DestinationState = DestinationState(),
        val withdrawalState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val tokenBalance: Fiat
            get() = token?.balance ?: Fiat.Zero

        val feeInEntryCurrency: Fiat?
            get() {
                val fee = feeAmount ?: return null
                val rate = entryRate ?: return null
                return fee.convertingTo(rate)
            }

        val minimumWithdrawalAmount: Fiat
            get() {
                val amount = selectedAmount.localFiat.nativeAmount
                val baseline = (feeInEntryCurrency ?: 0.00.toFiat(amount.currencyCode))
                return baseline + baseline.smallestUnit
            }

        val netTransferAmount: Fiat
            get() {
                val amount = selectedAmount.localFiat.nativeAmount
                val fee = feeInEntryCurrency ?: 0.00.toFiat(amount.currencyCode)
                return amount - fee
            }
    }

    internal sealed interface Event {
        // common
        data class OnMintSelected(val mint: Mint) : Event
        data class OnTokenUpdated(val token: TokenWithBalance) : Event

        // amount
        data class OnFeeChanged(val fee: Fiat?): Event
        data object OnAmountConfirmed : Event
        data class OnEntryRateUpdated(val rate: Rate) : Event
        data class OnAmountAccepted(val amount: VerifiedFiat) : Event
        data object OnDestinationConfirmed : Event
        data class UpdateConfirmingAmountState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        // destination
        data class OnAvailabilityChecked(val availability: WithdrawalAvailability?) : Event
        data object PasteFromClipboard : Event
        data class UpdateClipboardCheckState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) : Event

        // withdrawal
        data class UpdateWithdrawalState(
            val loading: Boolean = false,
            val success: Boolean = false,
            val error: Boolean = false,
        ) : Event

        data object OnLearnAboutFee : Event

        data object OnWithdrawalTooSmall : Event

        data object OnWithdraw : Event
        data object ConfirmWithdrawal : Event
        data object OnWithdrawalConfirmed : Event
        data object ProceedWithWithdrawalViaSwapper : Event
        data object ProceedWithWithdrawal : Event
        data object OnWithdrawSuccessful : Event
    }

    val checkBalanceLimit: () -> Boolean = {
        val delegateState = amountDelegate.state.value
        val state = stateFlow.value
        val enteredAmount = Fiat(
            fiat = delegateState.enteredAmount,
            currencyCode = state.tokenBalance.currencyCode
        )
        val isOverBalance = !delegateState.isEmpty &&
            enteredAmount.valueNonZero() &&
            enteredAmount.valueGreaterThan(state.tokenBalance)
        if (isOverBalance) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_insufficientFunds),
                message = resources.getString(R.string.error_description_withdrawInsufficientFunds)
            )
        }
        isOverBalance
    }

    val checkMinimumExceeded: () -> Boolean = {
        val delegateState = amountDelegate.state.value
        val state = stateFlow.value
        val enteredAmount = Fiat(
            fiat = delegateState.enteredAmount,
            currencyCode = state.tokenBalance.currencyCode
        )
        val fee = state.feeInEntryCurrency ?: 0.00.toFiat(state.tokenBalance.currencyCode)
        val isUnderMinimum = !delegateState.isEmpty &&
            enteredAmount.valueNonZero() &&
            enteredAmount.valueLessThanOrEqualTo(fee)
        if (isUnderMinimum) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_withdrawalTooSmall),
                message = resources.getString(R.string.error_description_withdrawalTooSmall)
            )
        }
        isUnderMinimum
    }

    init {
        dispatchEvent(Event.OnEntryRateUpdated(exchange.preferredRate))

        stateFlow
            .mapNotNull { it.selectedTokenAddress }
            .flatMapLatest { tokenAddress ->
                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(tokenAddress),
                    exchange.observePreferredRate(),
                ) { tokens, balance, rate ->
                    val token = tokens.find { it.address == tokenAddress } ?: return@combine null
                    val tokenName = when (token.address) {
                        Mint.usdc -> {
                            resources.getString(R.string.displayName_usdc)
                        }
                        Mint.usdf -> {
                            resources.getString(R.string.displayName_usdf)
                        }
                        else -> token.name
                    }
                    TokenWithBalance(
                        token = token,
                        balance = balance.convertingTo(rate),
                        displayName = tokenName,
                    )
                }
            }.filterNotNull()
            .onEach {
                dispatchEvent(Event.OnTokenUpdated(it))
            }.mapNotNull { (token, balance) ->
                exchange.getCurrency(balance.currencyCode.name)
            }.onEach {
                amountDelegate.onCurrencyChanged(it)
            }.launchIn(viewModelScope)

        userFlags.resolvedFlags
            .map { it.withdrawalFeeAmount.effectiveValue }
            .onEach { fee -> dispatchEvent(Event.OnFeeChanged(fee)) }
            .launchIn(viewModelScope)

        exchange.observePreferredRate()
            .onEach {
                dispatchEvent(Event.OnEntryRateUpdated(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountConfirmed>()
            .map { amountDelegate.state.value.amountAnimatedModel }
            .filterNot { checkBalanceLimit() }
            .filterNot { checkMinimumExceeded() }
            .onEach { data ->
                dispatchEvent(Event.UpdateConfirmingAmountState(loading = true))
                val rate = exchange.preferredRate
                val token = stateFlow.value.token!!.token
                val amountVerified = verifiedFiatCalculator.compute(
                    amount = Fiat(data.amountData.amount, rate.currency),
                    token = token,
                    balance = stateFlow.value.token!!.balance,
                    rate = rate,
                ).getOrElse {
                    dispatchEvent(Event.UpdateConfirmingAmountState(loading = false))
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_staleRates),
                        message = resources.getString(R.string.error_description_staleRates),
                    )
                    return@onEach
                }

                dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = true))
                dispatchEvent(Event.OnAmountAccepted(amountVerified))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PasteFromClipboard>()
            .onEach { dispatchEvent(Event.UpdateClipboardCheckState(loading = true)) }
            .mapNotNull {
                val clipboard = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                if (clipboard == null) {
                    dispatchEvent(Event.UpdateClipboardCheckState(loading = false))
                }
                clipboard
            }
            .mapNotNull { text ->
                runCatching { Base58.decode(text) }
                    .onFailure { dispatchEvent(Event.UpdateClipboardCheckState(loading = false)) }
                    .getOrNull()
            }.filter { address ->
                val length = address.size
                if (length != 32) {
                    dispatchEvent(Event.UpdateClipboardCheckState(loading = false))
                    false
                } else {
                    true
                }
            }.onEach { address ->
                val textState = stateFlow.value.destinationState.textFieldState
                textState.setTextAndPlaceCursorAtEnd(address.base58)
                dispatchEvent(Event.UpdateClipboardCheckState())
            }.launchIn(viewModelScope)

        stateFlow
            .map { it.destinationState.textFieldState }
            .flatMapLatest { ts -> snapshotFlow { ts.text } }
            .debounce(500)
            .filter { stateFlow.value.selectedTokenAddress != null }
            .map {
                transactionController.checkWithdrawalAvailability(
                    address = it.toString(),
                    mint = stateFlow.value.selectedTokenAddress!!
                )
            }
            .onResult(
                onError = {
                    dispatchEvent(Event.OnAvailabilityChecked(null))
                },
                onSuccess = { availability ->
                    dispatchEvent(Event.OnAvailabilityChecked(availability))
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLearnAboutFee>()
            .onEach {
                BottomBarManager.showInfo(
                    title = resources.getString(R.string.prompt_title_learnAboutWithdrawalFee),
                    message = resources.getString(R.string.prompt_description_learnAboutWithdrawalFee),
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnWithdraw>()
            .onEach {
                if (stateFlow.value.netTransferAmount < Fiat.Zero) {
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    dispatchEvent(Event.OnWithdrawalTooSmall)
                    return@onEach
                }

                dispatchEvent(Event.ConfirmWithdrawal)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmWithdrawal>()
            .onEach {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_confirmWithdrawal),
                    message = resources.getString(R.string.prompt_description_confirmWithdrawal),
                    actions = buildList {
                        add(
                            BottomBarAction(
                                text = resources.getString(R.string.action_confirmWithdraw),
                                onClick = { dispatchEvent(Event.OnWithdrawalConfirmed) }
                            )
                        )
                    },
                    showCancel = true
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnWithdrawalConfirmed>()
            .onEach { dispatchEvent(Event.UpdateWithdrawalState(loading = true)) }
            .onEach {
                // determine withdrawal method
                if (stateFlow.value.token?.token?.address == Mint.usdf) {
                    dispatchEvent(Event.ProceedWithWithdrawalViaSwapper)
                } else {
                    dispatchEvent(Event.ProceedWithWithdrawal)
                }
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ProceedWithWithdrawal>()
            .mapNotNull {
                val token = stateFlow.value.token?.token
                val amount = stateFlow.value.selectedAmount
                val withdrawalChecks = stateFlow.value.destinationState.availability
                val rawDestination = withdrawalChecks?.destination
                val resolvedDestination = withdrawalChecks?.resolvedDestination
                val feeInUsd = stateFlow.value.feeAmount

                val owner = userManager.accountCluster
                if (token == null || resolvedDestination == null || owner == null || rawDestination == null) {
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedWithdrawal),
                        message = resources.getString(R.string.error_description_failedWithdrawal)
                    )
                    return@mapNotNull null
                }

                // Refresh balance from network before submitting to ensure
                // the on-chain balance matches what we're about to withdraw.
                tokenCoordinator.updateTokenAccount(token.address)

                // underlyingTokenAmount.quarks are token quarks, not USD —
                // convert back through the bonding curve for an apples-to-apples comparison.
                val amountInUsd =
                    Fiat.tokenBalance(amount.localFiat.underlyingTokenAmount.quarks, token)
                val refreshedBalance = tokenCoordinator.balanceForToken(token)
                if (amountInUsd > refreshedBalance) {
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds)
                    )
                    return@mapNotNull null
                }

                val sendingVault = owner.withTimelockForToken(token)

                val feeInMint = feeInUsd?.let { fee ->
                    verifiedFiatCalculator.compute(
                        amount = fee,
                        token = token,
                        balance = stateFlow.value.token!!.balance,
                        rate = Rate.oneToOne,
                    ).getOrElse {
                        dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_staleRates),
                            message = resources.getString(R.string.error_description_staleRates),
                        )
                        return@mapNotNull null
                    }.localFiat.underlyingTokenAmount
                }

                transactionController.withdraw(
                    amount = amount,
                    mint = token.address,
                    fee = feeInMint,
                    destination = resolvedDestination,
                    // only provide the destination account if we are dealing with an owner account
                    destinationOwner = rawDestination.takeUnless { withdrawalChecks.kind == WithdrawalAvailability.Kind.TokenAccount },
                    owner = sendingVault,
                )
            }.onResult(
                onError = {
                    analytics.transfer(
                        event = Analytics.Transfer.Withdrawal,
                        amount = stateFlow.value.selectedAmount.localFiat,
                        successful = false,
                        error = it,
                    )
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedWithdrawal),
                        message = resources.getString(R.string.error_description_failedWithdrawal)
                    )
                },
                onSuccess = {
                    analytics.transfer(
                        event = Analytics.Transfer.Withdrawal,
                        amount = stateFlow.value.selectedAmount.localFiat,
                    )
                    viewModelScope.launch {
                        coroutineScope {
                            activityFeedCoordinator.fetchSinceLatest()
                        }
                        dispatchEvent(Event.UpdateWithdrawalState(success = true))
                        delay(400)
                        dispatchEvent(Event.OnWithdrawSuccessful)
                    }
                }
            )
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ProceedWithWithdrawalViaSwapper>()
            .mapNotNull {
                val token = stateFlow.value.token?.token
                val amount = stateFlow.value.selectedAmount
                val withdrawalChecks = stateFlow.value.destinationState.availability
                val rawDestination = withdrawalChecks?.destination
                val resolvedDestination = withdrawalChecks?.resolvedDestination
                val feeInUsd = stateFlow.value.feeAmount

                val owner = userManager.accountCluster
                if (token == null || resolvedDestination == null || owner == null || rawDestination == null) {
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedWithdrawal),
                        message = resources.getString(R.string.error_description_failedWithdrawal)
                    )
                    return@mapNotNull null
                }

                // Refresh balance from network before submitting to ensure
                // the on-chain balance matches what we're about to withdraw.
                tokenCoordinator.updateTokenAccount(token.address)

                // underlyingTokenAmount.quarks are token quarks, not USD —
                // convert back through the bonding curve for an apples-to-apples comparison.
                val amountInUsd =
                    Fiat.tokenBalance(amount.localFiat.underlyingTokenAmount.quarks, token)
                val refreshedBalance = tokenCoordinator.balanceForToken(token)
                if (amountInUsd > refreshedBalance) {
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    BottomBarManager.showAlert(
                        title = resources.getString(R.string.error_title_insufficientFunds),
                        message = resources.getString(R.string.error_description_insufficientFunds)
                    )
                    return@mapNotNull null
                }

                val feeInMint = feeInUsd?.let { fee ->
                    verifiedFiatCalculator.compute(
                        amount = fee,
                        token = token,
                        balance = stateFlow.value.token!!.balance,
                        rate = Rate.oneToOne,
                    ).getOrElse {
                        dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_staleRates),
                            message = resources.getString(R.string.error_description_staleRates),
                        )
                        return@mapNotNull null
                    }.localFiat.underlyingTokenAmount
                }

                val fee = feeInMint?.let {
                    LocalFiat.fromUsd(
                        usdf = it,
                        rate = amount.localFiat.rate
                    )
                } ?: LocalFiat.fromUsd(0.00.toFiat(), rate = amount.localFiat.rate)

                transactionController.withdrawUsdf(
                    amount = amount,
                    owner = owner,
                    destination = resolvedDestination,
                    destinationOwner = rawDestination,
                    fee = fee,
                )
            }.onResult(
                onSuccess = {
                    analytics.transfer(
                        event = Analytics.Transfer.Withdrawal,
                        amount = stateFlow.value.selectedAmount.localFiat,
                    )
                    viewModelScope.launch {
                        coroutineScope {
                            activityFeedCoordinator.fetchSinceLatest()
                        }
                        dispatchEvent(Event.UpdateWithdrawalState(success = true))
                        delay(400)
                        dispatchEvent(Event.OnWithdrawSuccessful)
                    }
                },
                onError = {
                    analytics.transfer(
                        event = Analytics.Transfer.Withdrawal,
                        amount = stateFlow.value.selectedAmount.localFiat,
                        successful = false,
                        error = it,
                    )
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedWithdrawal),
                        message = resources.getString(R.string.error_description_failedWithdrawal)
                    )
                }
            )
            .launchIn(viewModelScope)
    }


    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnMintSelected -> { state ->
                    // if mint is USDC, store it as USDF
                    val mint = if (event.mint == Mint.usdc) {
                        Mint.usdf
                    } else {
                        event.mint
                    }
                    state.copy(selectedTokenAddress = mint)
                }
                is Event.OnTokenUpdated -> { state -> state.copy(token = event.token) }

                is Event.OnFeeChanged -> { state ->
                    state.copy(feeAmount = event.fee)
                }

                Event.ProceedWithWithdrawal,
                Event.ProceedWithWithdrawalViaSwapper,
                Event.OnLearnAboutFee,
                Event.OnWithdrawalTooSmall,
                Event.ConfirmWithdrawal,
                Event.OnWithdrawalConfirmed,
                Event.OnWithdrawSuccessful,
                Event.PasteFromClipboard,
                Event.OnAmountConfirmed,
                Event.OnDestinationConfirmed,
                Event.OnWithdraw -> { state -> state }

                is Event.OnEntryRateUpdated -> { state ->
                    state.copy(entryRate = event.rate)
                }

                is Event.OnAmountAccepted -> { state ->
                    state.copy(
                        selectedAmount = event.amount,
                        confirmingAmount = LoadingSuccessState()
                    )
                }

                is Event.UpdateConfirmingAmountState -> { state ->
                    state.copy(
                        confirmingAmount = state.confirmingAmount.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                is Event.UpdateClipboardCheckState -> { state ->
                    val destinationState = state.destinationState
                    val loadingSuccess = destinationState.checkingClipboard
                    state.copy(
                        destinationState = destinationState.copy(
                            checkingClipboard = loadingSuccess.copy(
                                loading = event.loading,
                                success = event.success
                            )
                        )
                    )
                }

                is Event.OnAvailabilityChecked -> { state ->
                    val destinationState = state.destinationState
                    state.copy(
                        destinationState = destinationState.copy(
                            availability = event.availability,
                        )
                    )
                }

                is Event.UpdateWithdrawalState -> { state ->
                    state.copy(
                        withdrawalState = state.withdrawalState.copy(
                            loading = event.loading,
                            success = event.success,
                            error = event.error,
                        )
                    )
                }
            }
        }
    }
}