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
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.features.withdrawal.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.financial.plus
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.transactions.WithdrawalAvailability
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.text.AmountAnimatedInputUiModel
import com.getcode.ui.components.text.NumberInputHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.base58
import com.getcode.vendor.Base58
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class AmountEntryState(
    val currencyModel: CurrencyHolder = CurrencyHolder(),
    val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
    val confirmingAmount: LoadingSuccessState = LoadingSuccessState(),
    val selectedAmount: VerifiedFiat = VerifiedFiat(LocalFiat.Zero, null),
)

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
) : BaseViewModel2<WithdrawalViewModel.State, WithdrawalViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {
    private val numberInputHelper = NumberInputHelper()

    internal data class State(
        val selectedTokenAddress: Mint? = null,
        val token: TokenWithBalance? = null,
        val entryRate: Rate? = null,
        val feeAmount: Fiat? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
        val destinationState: DestinationState = DestinationState(),
        val withdrawalState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val canWithdraw: Boolean
            get() = (amountEntryState.amountAnimatedModel.amountData.amount) > 0.00

        val tokenBalance: Fiat
            get() = token?.balance ?: Fiat.Zero

        val feeInEntryCurrency: Fiat?
            get() {
                val fee = feeAmount ?: return null
                val rate = entryRate ?: return fee
                return fee.convertingTo(rate)
            }

        val minimumWithdrawalAmount: Fiat
            get() {
                val amount = amountEntryState.selectedAmount.localFiat.nativeAmount
                val baseline = (feeInEntryCurrency ?: 0.00.toFiat(amount.currencyCode))
                return baseline + baseline.smallestUnit
            }

        val netTransferAmount: Fiat
            get() {
                val amount = amountEntryState.selectedAmount.localFiat.nativeAmount
                val fee = feeInEntryCurrency ?: 0.toFiat(amount.currencyCode)
                return amount - fee
            }

        val error: EnteredAmountError
            get() {
                if (amountEntryState.amountAnimatedModel.amountData.isEmpty()) return EnteredAmountError.None

                val enteredAmount = Fiat(
                    fiat = amountEntryState.amountAnimatedModel.amountData.amount,
                    currencyCode = tokenBalance.currencyCode
                )
                if (!enteredAmount.valueNonZero()) return EnteredAmountError.None

                if (enteredAmount.valueGreaterThan(tokenBalance)) {
                    return EnteredAmountError.InsufficientFunds
                }

                val fee = feeInEntryCurrency ?: 0.toFiat(tokenBalance.currencyCode)

                if (enteredAmount.valueLessThanOrEqualTo(fee)) {
                    return EnteredAmountError.TooLow
                }

                return EnteredAmountError.None
            }
    }

    enum class EnteredAmountError {
        None,
        InsufficientFunds,
        TooLow,
        ;
    }

    internal sealed interface Event {
        // common
        data class OnMintSelected(val mint: Mint) : Event
        data class OnTokenUpdated(val token: TokenWithBalance) : Event

        // amount
        data class OnNumberPressed(val number: Int) : Event
        data object OnDecimalPressed : Event
        data object OnBackspace : Event
        data class OnEnteredNumberChanged(val backspace: Boolean = false) : Event
        data class OnAmountChanged(val amountAnimatedModel: AmountAnimatedInputUiModel) : Event
        data class OnFeeChanged(val fee: Fiat?): Event
        data class OnCurrencyChanged(val currency: Currency) : Event
        data object OnAmountConfirmed : Event
        data class OnEntryRateUpdated(val rate: Rate) : Event
        data class OnAmountAccepted(val amount: VerifiedFiat) : Event
        data object OnDestinationConfirmed : Event
        data class UpdateConfirmingAmountState(
            val loading: Boolean = false,
            val success: Boolean = false
        ) :
            Event

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
        val isOverBalance = stateFlow.value.error == EnteredAmountError.InsufficientFunds
        if (isOverBalance) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_insufficientFunds),
                message = resources.getString(R.string.error_description_withdrawInsufficientFunds)
            )
        }
        isOverBalance
    }

    val checkMinimumExceeded: () -> Boolean = {
        val isUnderMinimum = stateFlow.value.error == EnteredAmountError.TooLow
        if (isUnderMinimum) {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_withdrawalTooSmall),
                message = resources.getString(R.string.error_description_withdrawalTooSmall)
            )
        }
        isUnderMinimum
    }

    init {
        numberInputHelper.reset()

        stateFlow
            .mapNotNull { it.selectedTokenAddress }
            .flatMapLatest { tokenAddress ->
                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(tokenAddress),
                    exchange.observeEntryRate(),
                ) { tokens, balance, rate ->
                    val token = tokens.find { it.address == tokenAddress } ?: return@combine null
                    TokenWithBalance(
                        token = token,
                        balance = balance.convertingTo(rate),
                        displayName = if (token.address == Mint.usdf) {
                            resources.getString(R.string.displayName_solanaUsdc)
                        } else {
                            token.name
                        }
                    )
                }
            }.filterNotNull()
            .onEach {
                dispatchEvent(Event.OnTokenUpdated(it))
            }.mapNotNull { (token, balance) ->
                exchange.getCurrency(balance.currencyCode.name)
            }.onEach {
                dispatchEvent(Event.OnCurrencyChanged(it))
            }.launchIn(viewModelScope)

        userFlags.resolvedFlags
            .map { it.withdrawalFeeAmount.effectiveValue }
            .onEach { fee -> dispatchEvent(Event.OnFeeChanged(fee)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCurrencyChanged>()
            .map { it.currency }
            .onEach {
                numberInputHelper.fractionUnits = it.fractionUnits
            }.launchIn(viewModelScope)

        exchange.observeEntryRate()
            .onEach {
                // reset when entry rate changes
                numberInputHelper.reset()
                dispatchEvent(Event.OnAmountChanged(AmountAnimatedInputUiModel()))
                dispatchEvent(Event.OnEntryRateUpdated(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnNumberPressed>()
            .map { it.number }
            .onEach { number ->
                numberInputHelper.fractionUnits =
                    stateFlow.value.amountEntryState.currencyModel.fractionUnits
                numberInputHelper.maxLength = 10 // 1 billion dollars
                numberInputHelper.onNumber(number)
                dispatchEvent(Event.OnEnteredNumberChanged())
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnDecimalPressed>()
            .onEach {
                numberInputHelper.onDot()
                dispatchEvent(Event.OnEnteredNumberChanged())
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnBackspace>()
            .onEach {
                numberInputHelper.onBackspace()
                dispatchEvent(Event.OnEnteredNumberChanged(true))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnEnteredNumberChanged>()
            .map { it.backspace }
            .onEach { backspace ->
                val current = stateFlow.value.amountEntryState.amountAnimatedModel
                val model = stateFlow.value.amountEntryState.amountAnimatedModel
                val amount = numberInputHelper.getFormattedStringForAnimation(includeCommas = true)

                val updated = model.copy(
                    amountDataLast = current.amountData,
                    amountData = amount,
                    lastPressedBackspace = backspace
                )

                dispatchEvent(Event.OnAmountChanged(updated))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnAmountConfirmed>()
            .map { stateFlow.value.amountEntryState.amountAnimatedModel }
            .filterNot { checkBalanceLimit() }
            .filterNot { checkMinimumExceeded() }
            .onEach { data ->
                dispatchEvent(Event.UpdateConfirmingAmountState(loading = true))
                val rate = exchange.entryRate
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
                val amount = stateFlow.value.amountEntryState.selectedAmount
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
                        amount = stateFlow.value.amountEntryState.selectedAmount.localFiat,
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
                        amount = stateFlow.value.amountEntryState.selectedAmount.localFiat,
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
                val amount = stateFlow.value.amountEntryState.selectedAmount
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
                } ?: LocalFiat.fromUsd(0.toFiat(), rate = amount.localFiat.rate)

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
                        amount = stateFlow.value.amountEntryState.selectedAmount.localFiat,
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
                        amount = stateFlow.value.amountEntryState.selectedAmount.localFiat,
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

    override fun onCleared() {
        exchange.resetEntryToBalance()
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnMintSelected -> { state -> state.copy(selectedTokenAddress = event.mint) }
                is Event.OnTokenUpdated -> { state -> state.copy(token = event.token) }

                is Event.OnAmountChanged -> { state ->
                    val entryState = state.amountEntryState
                    state.copy(
                        amountEntryState = entryState.copy(
                            amountAnimatedModel = event.amountAnimatedModel
                        )
                    )
                }

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
                Event.OnWithdraw,
                Event.OnBackspace,
                is Event.OnEnteredNumberChanged,
                is Event.OnNumberPressed,
                Event.OnDecimalPressed -> { state -> state }

                is Event.OnCurrencyChanged -> { state ->
                    val entryState = state.amountEntryState
                    state.copy(
                        amountEntryState = entryState.copy(
                            currencyModel = CurrencyHolder(event.currency)
                        )
                    )
                }

                is Event.OnEntryRateUpdated -> { state ->
                    state.copy(entryRate = event.rate)
                }

                is Event.OnAmountAccepted -> { state ->
                    val entryState = state.amountEntryState
                    state.copy(
                        amountEntryState = entryState.copy(
                            selectedAmount = event.amount,
                            confirmingAmount = LoadingSuccessState()
                        )
                    )
                }

                is Event.UpdateConfirmingAmountState -> { state ->
                    val entryState = state.amountEntryState
                    val loadingSuccess = entryState.confirmingAmount
                    state.copy(
                        amountEntryState = entryState.copy(
                            confirmingAmount = loadingSuccess.copy(
                                loading = event.loading,
                                success = event.success,
                            )
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