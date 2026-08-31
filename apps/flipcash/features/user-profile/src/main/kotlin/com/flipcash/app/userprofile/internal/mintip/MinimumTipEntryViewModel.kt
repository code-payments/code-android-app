package com.flipcash.app.userprofile.internal.mintip

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.ui.ConfirmationStyle
import com.flipcash.core.R
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.user.UserManager
import com.flipcash.shared.amountentry.AmountEntryDelegate
import com.flipcash.shared.amountentry.AmountEntryLabel
import com.flipcash.shared.amountentry.AmountEntryStyle
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel
import com.getcode.view.LoadingSuccessState
import com.getcode.view.SuccessHoldDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * Backs the minimum-tip entry screen — the fee another user has to pay to open a DM, which the
 * profile carries as `minDmChatInitFee`.
 *
 * Two things separate it from the send-side tip entry: there is no ceiling, since a user can ask
 * for any amount regardless of what anyone can afford, so the preset minimum is the only bound and
 * it shows as a standing hint rather than only on error; and the confirm action is a save, so it
 * stays inert until the entry actually differs from what is already stored.
 */
@HiltViewModel
internal class MinimumTipEntryViewModel @Inject constructor(
    exchange: Exchange,
    private val resources: ResourceHelper,
    private val profileController: ProfileController,
    userManager: UserManager,
    tipPaymentDelegate: TipPaymentDelegate,
) : BaseViewModel<MinimumTipEntryViewModel.State, MinimumTipEntryViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
) {

    data class State(
        /** Currency the keypad is entering in — kept in sync with the preferred rate. */
        val currency: CurrencyCode = CurrencyCode.USD,
        /** The fee already on the profile, or null when none has been set. */
        val saved: Fiat? = null,
        val saving: LoadingSuccessState = LoadingSuccessState(),
    )

    sealed interface Event {
        /** Preferred currency resolved/changed; keeps the entry currency in sync. */
        data class CurrencyChanged(val currency: CurrencyCode) : Event

        /** The stored fee resolved or changed under us. */
        data class SavedFeeChanged(val fee: Fiat?) : Event

        /** User asked to save the currently entered amount. */
        data object ConfirmRequested : Event

        /**
         * The entry cleared validation and, when one was already stored, the user confirmed
         * replacing it. This is what actually writes to the profile.
         */
        data class CommitRequested(val amount: Fiat) : Event

        data class UpdateSavingState(
            val loading: Boolean = false,
            val success: Boolean = false,
            val error: Boolean = false,
        ) : Event

        /** The fee was stored — the screen should dismiss. */
        data object Saved : Event
    }

    private val minimumAmount = tipPaymentDelegate.minTipAmount

    // Gates the confirm action on the entry differing from what is stored. Fed from init, because
    // it needs the delegate's own state and so cannot be built before the delegate exists.
    private val entryChanged = MutableStateFlow(false)

    // The label is the only thing the step's position changes, and the screen supplies it, so the
    // style is a flow rather than a constant.
    private val style = MutableStateFlow(styleFor(isLastStep = true))

    val amountDelegate = AmountEntryDelegate(
        exchange = exchange,
        scope = viewModelScope,
        style = style,
        loadingState = stateFlow.map { it.saving }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadingSuccessState()),
        // No ceiling: what someone asks to be paid isn't bounded by any balance. The preset
        // minimum is the only bound, and with no max it reads as a standing hint.
        minimumAmount = minimumAmount,
        confirmEnabled = entryChanged,
    )

    // Prefill happens once. `prefill` types on top of the entry rather than replacing it, so a
    // second pass would corrupt whatever the user typed in the meantime.
    private var prefilled = false

    init {
        exchange.observePreferredRate()
            .onEach { rate ->
                exchange.getCurrency(rate.currency.name)?.let { amountDelegate.onCurrencyChanged(it) }
                dispatchEvent(Event.CurrencyChanged(rate.currency))
            }
            .launchIn(viewModelScope)

        // Registered after the rate above so the keypad's fraction units are set before the
        // prefill scales the stored amount.
        userManager.state
            .map { it.userProfile?.minDmChatInitFee }
            .distinctUntilChanged()
            .onEach { saved ->
                dispatchEvent(Event.SavedFeeChanged(saved))
                if (!prefilled && saved != null) {
                    prefilled = true
                    amountDelegate.prefill(saved.decimalValue)
                }
            }
            .launchIn(viewModelScope)

        combine(
            amountDelegate.state.map { it.enteredAmount }.distinctUntilChanged(),
            stateFlow.map { it.saved }.distinctUntilChanged(),
        ) { entered, saved ->
            // Both sides are money in the same currency; the tolerance is only there to keep
            // binary-fraction noise from reading as an edit.
            abs(entered - (saved?.decimalValue ?: 0.0)) > 0.0001
        }
            .onEach { entryChanged.value = it }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.ConfirmRequested>()
            .onEach {
                if (!stateFlow.value.saving.isIdle) return@onEach

                val entered = amountDelegate.state.value.enteredAmount
                if (entered <= 0.0) return@onEach
                val amount = Fiat(entered, stateFlow.value.currency)

                val min = minimumAmount.value
                if (min != null && amount.valueLessThan(min)) {
                    // Info, not alert: nothing has gone wrong and nothing is being destroyed —
                    // the entry is just under the floor and needs raising.
                    BottomBarManager.showInfo(
                        title = resources.getString(R.string.error_title_minimumTip, min.formatted()),
                        message = resources.getString(R.string.error_description_minimumTip),
                    )
                    return@onEach
                }

                // Replacing a stored fee asks first; a first one has nothing to overwrite. Asked
                // after validation so a rejected amount never gets a confirmation dialog.
                if (stateFlow.value.saved == null) {
                    dispatchEvent(Event.CommitRequested(amount))
                    return@onEach
                }
                BottomBarManager.showMessage(
                    title = resources.getString(R.string.prompt_title_changeMinimumTip),
                    message = resources.getString(R.string.prompt_description_changeMinimumTip),
                    actions = listOf(
                        BottomBarAction(resources.getString(R.string.action_changeMinimumTip)) {
                            dispatchEvent(Event.CommitRequested(amount))
                        }
                    ),
                    showCancel = true,
                )
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.CommitRequested>()
            .onEach { event ->
                if (!stateFlow.value.saving.isIdle) return@onEach

                dispatchEvent(Event.UpdateSavingState(loading = true))
                profileController.setMinDmChatInitFee(event.amount)
                    .onSuccess {
                        viewModelScope.launch {
                            dispatchEvent(Event.UpdateSavingState(success = true))
                            delay(SuccessHoldDuration)
                            dispatchEvent(Event.Saved)
                            dispatchEvent(Event.UpdateSavingState())
                        }
                    }
                    .onFailure {
                        dispatchEvent(Event.UpdateSavingState())
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_minimumTipFailed),
                            message = resources.getString(R.string.error_description_minimumTipFailed),
                        )
                    }
            }
            .launchIn(viewModelScope)
    }

    /** Called by the screen once the step's position in the flow is known. */
    fun onPositionResolved(isLastStep: Boolean) {
        style.value = styleFor(isLastStep)
    }

    private fun styleFor(isLastStep: Boolean) = AmountEntryStyle(
        actionLabel = AmountEntryLabel.Plain(
            resources.getString(if (isLastStep) R.string.action_save else R.string.action_next)
        ),
        actionStyle = ConfirmationStyle.Button,
        belowMinHint = { resources.getString(R.string.subtitle_minimumTipHint, it) },
    )

    companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            when (event) {
                is Event.CurrencyChanged -> { state -> state.copy(currency = event.currency) }
                is Event.SavedFeeChanged -> { state -> state.copy(saved = event.fee) }
                is Event.UpdateSavingState -> { state ->
                    state.copy(
                        saving = LoadingSuccessState(
                            loading = event.loading,
                            success = event.success,
                            error = event.error,
                        )
                    )
                }
                is Event.ConfirmRequested -> { state -> state }
                is Event.CommitRequested -> { state -> state }
                is Event.Saved -> { state -> state }
            }
        }
    }
}
