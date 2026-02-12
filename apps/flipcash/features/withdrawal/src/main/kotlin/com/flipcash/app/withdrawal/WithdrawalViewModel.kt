package com.flipcash.app.withdrawal

import android.content.ClipboardManager
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.app.core.extensions.onResult
import com.flipcash.app.core.ui.CurrencyHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.withdrawal.R
import com.flipcash.services.analytics.AnalyticsEvent
import com.flipcash.services.analytics.FlipcashAnalyticsService
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.Currency
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.minus
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
    val selectedAmount: LocalFiat = LocalFiat.Zero,
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
    private val userManager: UserManager,
    transactionController: TransactionController,
    clipboardManager: ClipboardManager,
    activityFeedCoordinator: ActivityFeedCoordinator,
    analytics: FlipcashAnalyticsService,
    tokenCoordinator: TokenCoordinator,
) : BaseViewModel2<WithdrawalViewModel.State, WithdrawalViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    private val numberInputHelper = NumberInputHelper()

    internal data class State(
        val selectedTokenAddress: Mint? = null,
        val token: TokenWithBalance? = null,
        val amountEntryState: AmountEntryState = AmountEntryState(),
        val destinationState: DestinationState = DestinationState(),
        val withdrawalState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val canWithdraw: Boolean
            get() = (amountEntryState.amountAnimatedModel.amountData.amount) > 0.00

        val tokenBalance: Fiat
            get() = token?.balance ?: Fiat.Zero

        val isError: Boolean
            get() {
                if (amountEntryState.amountAnimatedModel.amountData.isEmpty()) return false
                val enteredAmount = Fiat(
                    fiat = amountEntryState.amountAnimatedModel.amountData.amount,
                    currencyCode = tokenBalance.currencyCode
                )

                return !enteredAmount.valueLessThanOrEqualTo(tokenBalance)
            }
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
        data class OnCurrencyChanged(val currency: Currency) : Event
        data object OnAmountConfirmed : Event
        data class OnAmountAccepted(val amount: LocalFiat) : Event
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
            val success: Boolean = false
        ) : Event

        data object OnLearnAboutFee : Event

        data object OnWithdrawalTooSmall : Event

        data object OnWithdraw : Event
        data object ConfirmWithdrawal : Event
        data object OnWithdrawalConfirmed : Event
        data object OnWithdrawSuccessful : Event
    }

    val checkBalanceLimit: () -> Boolean = {
        val amount =
            stateFlow.value.amountEntryState.amountAnimatedModel.amountData.amount
        val conversionRate =
            exchange.rateToUsd(
                stateFlow.value.amountEntryState.currencyModel.code ?: CurrencyCode.USD
            ) ?: Rate.ignore
        val enteredInUsdc = Fiat(
            fiat = amount,
            currencyCode = stateFlow.value.amountEntryState.currencyModel.code ?: CurrencyCode.USD
        ).convertingTo(conversionRate)
        val tokenBalance = stateFlow.value.token?.balance ?: Fiat.Zero

        val isOverBalance = enteredInUsdc > tokenBalance.rounded()
        if (isOverBalance || conversionRate == Rate.ignore) {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_insufficientFunds),
                message = resources.getString(R.string.error_description_insufficientFunds)
            )
        }
        isOverBalance
    }

    init {
        numberInputHelper.reset()

        stateFlow
            .mapNotNull { it.selectedTokenAddress }
            .flatMapLatest { tokenAddress ->
                combine(
                    tokenCoordinator.tokens,
                    tokenCoordinator.balanceForToken(tokenAddress),
                ) { tokens, balance ->
                    val token = tokens.find { it.address == tokenAddress } ?: return@combine null
                    TokenWithBalance(
                        token = token,
                        balance = balance
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
            .onEach { data ->
                dispatchEvent(Event.UpdateConfirmingAmountState(loading = true))
                val rate = exchange.rateForUsd()
                val token = stateFlow.value.token!!.token
                val amountFiat = LocalFiat.valueExchangeIn(
                    amount = Fiat(data.amountData.amount, rate.currency),
                    token = token,
                    balance = stateFlow.value.token!!.balance,
                    rate = rate,
                )

                dispatchEvent(Event.UpdateConfirmingAmountState(loading = false, success = true))
                dispatchEvent(Event.OnAmountAccepted(amountFiat))
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
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_learnAboutWithdrawalFee),
                        subtitle = resources.getString(R.string.prompt_description_learnAboutWithdrawalFee),
                        showCancel = false,
                        actions = buildList {
                            add(
                                BottomBarAction(
                                    text = resources.getString(R.string.action_ok),
                                )
                            )
                        },
                        type = BottomBarManager.BottomBarMessageType.INFO
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnWithdraw>()
            .onEach {
                val amount = stateFlow.value.amountEntryState.selectedAmount
                val withdrawalChecks = stateFlow.value.destinationState.availability
                val fee = withdrawalChecks?.feeAmount
                if (amount.nativeAmount - (fee ?: Fiat.Zero) < Fiat.Zero) {
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    dispatchEvent(Event.OnWithdrawalTooSmall)
                    return@onEach
                }

                dispatchEvent(Event.ConfirmWithdrawal)
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmWithdrawal>()
            .onEach {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_confirmWithdrawal),
                        subtitle = resources.getString(R.string.prompt_description_confirmWithdrawal),
                        showScrim = true,
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
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnWithdrawalConfirmed>()
            .onEach { dispatchEvent(Event.UpdateWithdrawalState(loading = true)) }
            .mapNotNull {
                val token = stateFlow.value.token?.token
                val amount = stateFlow.value.amountEntryState.selectedAmount
                val withdrawalChecks = stateFlow.value.destinationState.availability
                val rawDestination = withdrawalChecks?.destination
                val resolvedDestination = withdrawalChecks?.resolvedDestination
                val feeInUsd = withdrawalChecks?.feeAmount

                val owner = userManager.accountCluster
                if (token == null || resolvedDestination == null || owner == null || rawDestination == null) {
                    dispatchEvent(Event.UpdateWithdrawalState(loading = false))
                    BottomBarManager.showError(
                        title = resources.getString(R.string.error_title_failedWithdrawal),
                        message = resources.getString(R.string.error_description_failedWithdrawal)
                    )
                    return@mapNotNull null
                }

                val sendingVault = owner.withTimelockForToken(token)

                val feeInMint = feeInUsd?.let { fee ->
                    LocalFiat.valueExchangeIn(
                        fee,
                        token = token,
                        balance = stateFlow.value.token!!.balance,
                        rate = exchange.rateToUsd(CurrencyCode.USD)!!,
                    ).underlyingTokenAmount
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
                        event = AnalyticsEvent.Withdrawal,
                        amount = stateFlow.value.amountEntryState.selectedAmount,
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
                        AnalyticsEvent.Withdrawal,
                        amount = stateFlow.value.amountEntryState.selectedAmount
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
                                success = event.success
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
                            success = event.success
                        )
                    )
                }
            }
        }
    }
}