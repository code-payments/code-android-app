package com.flipcash.app.currencycreator.internal

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.core.text.trimmedLength
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.currencycreator.internal.components.CurrencyCreatorTopBarController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.toFiat
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.data.isLoaded
import com.getcode.util.resources.ContentReader
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
internal class CurrencyCreatorViewModel @Inject constructor(
    dispatchers: DispatcherProvider,
    userFlags: UserFlagsCoordinator,
    val contentReader: ContentReader,
) : BaseViewModel2<CurrencyCreatorViewModel.State, CurrencyCreatorViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent,
    defaultDispatcher = dispatchers.Default,
) {

    internal data class State(
        val stepCount: Int = PROGRESS_STEPS.count(),
        val descriptionLengthMax: Int = MAX_DESCRIPTION,
        val currentStep: CurrencyCreatorStep? = null,
        val nameFieldState: TextFieldState = TextFieldState(),
        val descriptionFieldState: TextFieldState = TextFieldState(),
        val icon: Loadable<Uri> = Loadable.Loading(),
        val bill: Bill? = null,
        val purchaseAmount: Fiat = 20.toFiat(),
        val processingState: LoadingSuccessState = LoadingSuccessState(),
    ) {
        val hasName: Boolean
            get() = nameFieldState.text.isNotBlank()

        val descriptionLength: Int
            get() = descriptionFieldState.text.trimmedLength()

        val remainingSpaceForDescription: Int
            get() = descriptionLengthMax - descriptionLength

        val hasDescription: Boolean
            get() = descriptionFieldState.text.isNotBlank() &&
                    remainingSpaceForDescription >= 0

        val progress: Float
            get() {
                val step = currentStep ?: return 0f
                val index = PROGRESS_STEPS.indexOfFirst { it.isInstance(step) }
                if (index < 0) return 0f
                return (index + 1).toFloat() / PROGRESS_STEPS.size
            }

        private companion object {
            private const val MAX_DESCRIPTION = 500

            val PROGRESS_STEPS = listOf(
                CurrencyCreatorStep.NameSelection::class,
                CurrencyCreatorStep.IconSelection::class,
                CurrencyCreatorStep.DescriptionSelection::class,
                CurrencyCreatorStep.BillCustomization::class,
                CurrencyCreatorStep.BillReviewAndPurchase::class,
            )
        }
    }

    internal sealed interface Event {
        data class OnStepChanged(val step: CurrencyCreatorStep) : Event

        data class OnIconSelected(val image: Uri) : Event
        data class OnIconCached(val image: Uri) : Event

        data class OnPurchaseAmountChanged(val amount: Fiat) : Event

        data class OnBillConfirmed(val bill: Bill?): Event
        data class UpdateProcessingState(
            val loading: Boolean = false,
            val success: Boolean = false,
        ) : Event

        data object Purchase: Event
    }

    init {
        userFlags.resolvedFlags
            .map { it.newCurrencyPurchaseAmount.effectiveValue }
            .onEach { dispatchEvent(Event.OnPurchaseAmountChanged(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnIconSelected>()
            .mapNotNull { event ->
                contentReader.copyToCache(event.image, "currency_icon_${System.nanoTime()}")
            }
            .flowOn(dispatchers.IO)
            .onEach { cached -> dispatchEvent(Event.OnIconCached(cached)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.Purchase>()
            .onEach {  }
    }

    /**
     * Connect the ViewModel to the top bar controller. The ViewModel pushes
     * [State.progress] to the controller whenever the current step changes.
     */
    fun connectTopBar(controller: CurrencyCreatorTopBarController) {
        stateFlow
            .map { it.currentStep }
            .distinctUntilChanged()
            .onEach { _ ->
                val state = stateFlow.value
                controller.progress = state.progress
            }
            .launchIn(viewModelScope)
    }

    internal companion object {
        val updateStateForEvent: (Event) -> (State.() -> State) = { event ->
            println("Event: $event")
            when (event) {
                is Event.OnStepChanged -> { state ->
                    state.copy(currentStep = event.step)
                }

                is Event.OnIconSelected -> { state ->
                    state.copy(icon = Loadable.Loading(event.image))
                }

                is Event.OnIconCached -> { state ->
                    state.copy(icon = Loadable.Loaded(event.image))
                }

                is Event.OnBillConfirmed -> { state ->
                    state.copy(bill = event.bill)
                }

                is Event.OnPurchaseAmountChanged -> { state ->
                    state.copy(purchaseAmount = event.amount)
                }

                is Event.UpdateProcessingState -> { state ->
                    val processingState = state.processingState

                    state.copy(
                        processingState = processingState.copy(
                            loading = event.loading,
                            success = event.success,
                        )
                    )
                }

                is Event.Purchase -> { state -> state }
            }
        }
    }
}
